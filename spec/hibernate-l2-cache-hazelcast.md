# SPEC — Hibernate Second-Level Cache with Hazelcast

Feature spec for adding a Hibernate second-level cache (2LC) to `taskhub-api`,
backed by Hazelcast running as a separate container. Scoped as a focused addition
to the existing 8-session build (see the course [`SPEC.md`](../SPEC.md)); it does
**not** replace it.

---

## 1. Objective

Cut redundant database reads for the hot, id-based entity lookups TaskHub already
does on every request — authorizing a task walks `Task → Project → owner (User)` —
by caching those entities in a Hibernate second-level cache backed by Hazelcast.

- **Users:** the running service (lower DB load, lower p99 read latency); operators
  who get a shared, observable cache tier they can scale independently.
- **Success looks like:** the same request served with fewer SQL `SELECT`s after the
  first, proven by Hibernate statistics (second-level-cache hit count rises, entity
  load count does not), with **no change to correctness** under concurrent mutation.

In scope alongside entity/collection caching: the **query cache**, so cacheable
list queries (e.g. paginated project/task listings) can be served from Hazelcast too.

Non-goals: caching DTOs/HTTP responses, replacing the gRPC notification path,
distributed session state.

---

## 2. Architecture & key decisions

Decisions confirmed with the user (2026-07-16):

| Decision | Choice | Why |
|----------|--------|-----|
| Topology | **Client/server** — Hazelcast is its own docker-compose container; `taskhub-api` connects as a Hazelcast **client** | Matches "Hazelcast docker-compose içinde olsun"; cache scales/restarts independently of the app |
| 2LC provider | **Native `hazelcast-hibernate53` region factory** | User's choice over the JCache bridge. **Carries a Hibernate-version risk — see §8 and the gate task T0.** |
| Cache scope | **Selective** entity + collection caches, `READ_WRITE`, **plus query cache** | Correctness under mutation without over-caching; cacheable list queries served from Hazelcast |

```
┌─────────────────┐        Hazelcast client protocol        ┌──────────────────┐
│  taskhub-api    │  ───────────────────────────────────▶   │  hazelcast       │
│  Hibernate 7.4  │        (region factory, native client)  │  (container)     │
│  + hz client    │  ◀───────────────────────────────────   │  cluster :5701   │
└────────┬────────┘                                          └──────────────────┘
         │ JDBC
         ▼
   ┌───────────┐
   │ postgres  │
   └───────────┘
```

**Managed versions (Spring Boot 4.1 BOM):** Hibernate `7.4.1.Final`, Hazelcast
`5.5.0`. The region-factory artifact (`com.hazelcast:hazelcast-hibernate53`, latest
`5.2.0`, built against Hibernate 6.5) is **not** BOM-managed and is pinned explicitly.

**What gets cached** (entities are the FK targets walked on every authorize):

| Entity | Strategy | Collection cache |
|--------|----------|------------------|
| `User` | `READ_WRITE` | `User.roles` (`@ElementCollection`) |
| `Project` | `READ_WRITE` | — (no owned collection mapped) |
| `Task` | `READ_WRITE` | `Task.tags` (`@ManyToMany`) |
| `Tag` | `READ_WRITE` | — |

`shared-cache-mode: ENABLE_SELECTIVE` — only entities explicitly annotated are
cached. `READ_WRITE` (not read-only) because all four are mutated through the REST
API; it uses soft locks so a concurrent write invalidates rather than serving stale
data. `open-in-view: false` is already set, which keeps cache access inside the
transaction where it belongs.

**Query cache** is enabled (`hibernate.cache.use_query_cache=true`). It is opt-in
per query: only repositories that mark a query `Cacheable` (via `@QueryHints({
@QueryHint(name = HINT_CACHEABLE, value = "true")})` or a `jakarta.persistence`
`hint`) use it. Query results store entity **ids**, so a cached query still resolves
each row through the entity cache and stays consistent with `READ_WRITE`
invalidation. Hibernate keeps an implicit `default-update-timestamps-region`; a write
to a table bumps its timestamp and invalidates queries over it, so a mutation cannot
serve a stale list.

---

## 3. Commands

```bash
# Bring up backing services (postgres already present; hazelcast is added)
docker compose up -d postgres hazelcast

# Build + run the full test suite (incl. the cache-hit integration test)
./mvnw verify

# Run the API against the local Hazelcast container (dev profile)
./mvnw -pl taskhub-api spring-boot:run

# Confirm the cache is live: hit counts climb on repeated id lookups
#   - via the Hibernate statistics exposed on actuator/metrics, or
#   - the CacheIntegrationTest assertion (see §5)
```

No new build tooling. Hazelcast client config travels as Hibernate properties /
`hazelcast-client.yaml` on the classpath; no code-generation step.

---

## 4. Project structure (files touched / added)

```
docker-compose.yml                         # + hazelcast service (:5701), healthcheck
taskhub-api/
  pom.xml                                  # + hazelcast, hazelcast-hibernate53:5.2.0
  src/main/resources/
    application.yml                        # jpa 2LC props (factory, selective, stats)
    application-dev.yml                    # hazelcast client → localhost:5701
    application-prod.yml                   # hazelcast client → env-supplied address
    hazelcast-client.yaml                  # client cluster-name + address (optional; or props)
  src/main/java/.../user/User.java         # @Cacheable + @Cache(READ_WRITE) + roles cache
  src/main/java/.../project/Project.java   # @Cacheable + @Cache(READ_WRITE)
  src/main/java/.../task/Task.java         # @Cacheable + @Cache(READ_WRITE) + tags cache
  src/main/java/.../tag/Tag.java           # @Cacheable + @Cache(READ_WRITE)
  src/test/java/.../cache/
    CacheIntegrationTest.java              # asserts 2LC hits via Hibernate Statistics
```

No new production Java classes are required — 2LC is driven by Hibernate properties
plus entity annotations. A small `@Configuration` is added **only if** the spike
(T0) shows the client needs programmatic setup beyond properties.

Flyway migrations are **not** touched: the second-level cache never changes the
schema (boundary from the course spec holds — Flyway owns the schema, `ddl-auto:
validate`).

---

## 5. Testing strategy

The cache must be proven to (a) produce hits and (b) preserve correctness on writes.

1. **Cache-hit integration test** (`CacheIntegrationTest`, `@SpringBootTest`):
   load an entity by id twice in separate sessions; assert
   `SessionFactory.getStatistics().getSecondLevelCacheHitCount()` increased and the
   DB `EntityLoadCount` did **not** on the second read. Statistics are enabled in the
   test profile (`hibernate.generate_statistics=true`).
2. **Invalidation / correctness test:** cache an entity, mutate it through the
   service, re-read — assert the fresh value is returned (READ_WRITE invalidates on
   commit), not the stale cached one.
2b. **Query-cache test:** run a `Cacheable` list query twice; assert
   `Statistics.getQueryCacheHitCount()` rose on the second run and no SQL was issued,
   then mutate an underlying row and assert the next run misses (re-queries) rather
   than returning the stale list.
3. **Isolation from a running container:** slice/integration tests use an **embedded
   Hazelcast member** region factory (`HazelcastLocalCacheRegionFactory`) via the
   `test` profile, so `./mvnw verify` needs no Docker. The client/server path is
   validated separately (T4) either manually against the container or with a
   Hazelcast Testcontainer in one dedicated e2e test.
4. **Regression:** the existing 82 taskhub-api tests must stay green — enabling 2LC
   must not alter any observable behaviour of the REST/gRPC slices.

Coverage gate from the course spec (≥ 80%) continues to apply to changed code.

---

## 6. Code style

Follow the existing codebase conventions (they are already consistent):

- Jakarta `@Cacheable` + Hibernate `@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)`
  on the entity class; `@Cache` on the cached collection field. No `region` names
  unless a per-region config demands it.
- Configuration lives in `application*.yml` as `spring.jpa.properties.hibernate.*`
  keys, mirroring how JWT/CORS/gRPC settings are already externalised. Secrets and
  environment-specific addresses come from env vars (`TASKHUB_HAZELCAST_ADDRESS`),
  never committed — dev profile may hardcode `localhost:5701`.
- Javadoc on any new class explains *why* (consistency strategy, client vs embedded),
  matching the comment density of the surrounding entities/config.
- No Lombok, no new frameworks; match the existing constructor/getter style.

---

## 7. Tasks (dependency-ordered; T0 is a gate)

- **T0 — Compatibility spike (GATE).** Add `hazelcast` + `hazelcast-hibernate53:5.2.0`,
  wire the region factory, boot `taskhub-api` against a Hazelcast container, and prove
  one entity produces a second-level-cache hit. **If the region factory fails to load
  or init under Hibernate 7.4 → STOP and escalate to the user** (documented fallback:
  the JCache bridge, `hibernate-jcache` + Hazelcast JSR-107 provider). Everything
  below depends on T0 passing.
- **T1 — docker-compose Hazelcast service.** Add the `hazelcast` container (`:5701`,
  cluster name `taskhub`, healthcheck); app not yet wired.
- **T2 — Dependencies + Hibernate 2LC config.** `pom.xml` deps; `application*.yml`
  properties: enable 2LC, `ENABLE_SELECTIVE`, **query cache on**, native-client
  address per profile, statistics in test.
- **T3 — Annotate entities + cacheable queries.** `@Cacheable` + `@Cache(READ_WRITE)`
  on User, Project, Task, Tag; collection caches on `Task.tags` and `User.roles`; mark
  the hot list queries (paginated project/task listings) `Cacheable` via query hints.
- **T4 — Tests.** Cache-hit + invalidation tests (embedded member); one client/server
  smoke check against the container; full regression green.
- **T5 — Verify & document.** Show hit/miss counts climbing on a real request path
  (authorize walks Task→Project→User); note the observability hook for Session 8;
  update README/`api.http` notes as needed.

Acceptance criteria (all must hold):

1. `taskhub-api` starts as a Hazelcast **client** against the docker-compose Hazelcast
   container; no embedded member in dev/prod.
2. Repeated id-based reads of a cached entity produce **second-level-cache hits**
   (proven by Hibernate `Statistics`), with no extra SQL after the first load.
3. A mutation through the service **invalidates** the cached entry; a subsequent read
   returns fresh data — no stale reads under `READ_WRITE`.
4. `./mvnw verify` is green **without Docker** (tests use the embedded region factory);
   the existing suite is unaffected.
5. Only the four selected entities are cached (`ENABLE_SELECTIVE`). The query cache is
   on but opt-in: only queries explicitly marked `Cacheable` use it, and a mutation
   invalidates the affected cached queries (no stale lists).

---

## 8. Boundaries

**Always**
- Keep Flyway the sole owner of the schema; 2LC changes no DDL, `ddl-auto: validate`
  stays.
- Use `READ_WRITE` for every cached entity (all are mutable) so a write invalidates.
- Externalise the Hazelcast address/secrets per environment; dev-only defaults may be
  literal, prod is env-supplied.
- Prove cache behaviour with Hibernate statistics, not by eyeballing logs.

**Ask first**
- Marking additional queries `Cacheable` beyond the agreed hot list endpoints, or
  making the query cache the default for all queries.
- Caching any entity beyond the four agreed, or switching a strategy to
  `NONSTRICT_READ_WRITE`/`READ_ONLY`.
- Changing the Hazelcast topology (embedded vs client/server) or making the app a
  cluster member.
- Adding Testcontainers to the default `verify` path (test-runtime/Docker cost).

**Never**
- Cache across a security/tenant boundary in a way that could serve one user another
  user's data — cache is keyed by entity id, and authorization still runs on every
  request; do not add id-agnostic caching.
- Commit Hazelcast credentials or environment addresses.
- Let a cache miss/inconsistency mask a correctness bug — if a test needs the cache
  disabled to pass, the cache config is wrong, not the test.
- Merge below the course coverage gate.

---

## 9. Primary risk

`hazelcast-hibernate53:5.2.0` is built against **Hibernate 6.5**; the app runs
**Hibernate 7.4.1**. Hibernate's `org.hibernate.cache.spi.RegionFactory` SPI may have
changed enough between 6.5 and 7.4 that the region factory will not load or
initialise. This is why **T0 is a hard gate**: if it fails, the native path is a dead
end for this Hibernate version and we fall back to the JCache bridge
(`org.hibernate.orm:hibernate-jcache`, versioned with Hibernate 7.4, + Hazelcast's
JSR-107 `HazelcastClientCachingProvider`) — which requires re-confirming with the
user, since it changes the chosen provider.
