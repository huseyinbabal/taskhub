# Hibernate second-level cache (Hazelcast)

`taskhub-api` caches its domain entities in a Hibernate second-level cache backed by
Hazelcast, so the id-based reads every request performs — authorizing a task walks
`Task → Project → owner (User)` — are served from Hazelcast instead of PostgreSQL.
Design and decisions: [`spec/hibernate-l2-cache-hazelcast.md`](../spec/hibernate-l2-cache-hazelcast.md).

## Topology

Client/server: Hazelcast runs as its own container; `taskhub-api` connects to it as a
**client**. The cached entries live in the Hazelcast cluster, not in the app JVM.

```
taskhub-api  ──(Hazelcast native client)──▶  hazelcast container  (cluster "taskhub", :5701)
     │ JDBC
     ▼
 postgres
```

## What is cached

| Entity | Strategy | Also cached |
|--------|----------|-------------|
| `User`, `Project`, `Task`, `Tag` | `READ_WRITE` | collections `Task.tags`, `User.roles` |

Only these entities are cached (`shared-cache-mode: ENABLE_SELECTIVE`). `READ_WRITE`
means every mutation invalidates the cached entry, so a read after a write is never
stale. The **query cache** is on but opt-in per query — currently the two hot list
queries `ProjectRepository.findByOwnerId` and `TaskRepository.findByProjectId`.

An unreachable cluster degrades to no-cache (`fallback: true`) rather than failing:
the cache is an optimisation, never a correctness dependency.

## Run it

```bash
docker compose up -d postgres hazelcast     # hazelcast: cluster "taskhub", :5701
./mvnw -pl taskhub-api spring-boot:run       # connects as a Hazelcast client
```

Configuration (`application.yml`, overridable per environment):

| Setting | Default (dev) | Env var |
|---------|---------------|---------|
| Hazelcast address | `127.0.0.1:5701` | `TASKHUB_HAZELCAST_ADDRESS` |
| Cluster name | `taskhub` | `TASKHUB_HAZELCAST_CLUSTER` |

Every environment must point both at the same cluster; there is no embedded fallback
member in dev or prod.

## Verify it works

With the app running against the container, read the same task twice and watch the SQL
log (dev has `show-sql: true`):

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"..."}' | jq -r .token)

curl -s localhost:8080/api/tasks/69 -H "Authorization: Bearer $TOKEN"   # cold: selects tasks + users
curl -s localhost:8080/api/tasks/69 -H "Authorization: Bearer $TOKEN"   # warm: selects only users
```

On the warm read the only SQL is `select ... from users` — the current-user lookup by
username (see the follow-up below). The `Task` and its `Project` are served from
Hazelcast: no `from tasks`, no `from projects`. That is the client/server cache working
end to end.

The automated equivalents (entity hit, invalidation, collection cache, query cache) run
container-free against an embedded Hazelcast member in
`HazelcastL2CacheTest` — `./mvnw -pl taskhub-api test`.

### Management Center

`docker compose up -d` also starts Hazelcast Management Center at
**http://localhost:8090** (dev convenience; mapped off 8080 so it does not clash with
`taskhub-api`). It auto-connects to the `taskhub` cluster. Once the app has served a few
requests, the cache regions appear under **Storage → Maps** — one map per cached entity/
collection (e.g. `io.github.huseyinbabal.taskhub.task.Task`), with live entry counts and
hit ratios.

## Known follow-ups

- **`findByUsername` is not cached.** Resolving the authenticated caller
  (`CurrentUserProvider`) queries `users` by username on every request — a query, not an
  id-based entity load — so it still hits the DB even on a full cache hit. Query-caching
  it would remove the last per-request `select`, but that is a new cacheable query and is
  "ask first" under the spec boundaries.
- **No cache metrics yet.** `hibernate.generate_statistics` is on in dev, but the
  Micrometer binder (`hibernate-micrometer`) is not on the classpath, so cache hit/miss
  counts are not exposed on `/actuator/prometheus`. Wiring it in is a natural part of
  Session 8 (Observability).
