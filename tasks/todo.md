# TODO: TaskHub

Tracked as GitHub issues on `huseyinbabal/taskhub`. Check off when the slice's
PR merges. See [`plan.md`](plan.md) and [`SPEC.md`](../SPEC.md).

- [x] **#1 — Session 1: Project setup** (skeleton, layering, exception handling, SPEC) — done, commit `0ecac95`
- [x] **#2 — Session 2: REST + Security** (CRUD, DTO/validation, pagination, Swagger, JWT, RBAC, CORS) — done, PR #9
- [x] **#3 — Session 3: gRPC notifications** (proto, unary + streaming, interceptors) — done, PR #11
- [x] **#4 — Session 4: Database** (Flyway, relations, N+1 fixes) — done, schema landed with Session 2 in commit `a9ceb0c` (`ddl-auto: validate`)
- [x] **#5 — Session 5: Testing** (unit/slice/Testcontainers/E2E + coverage gate) — done, tests across all three modules; JaCoCo line gate ≥ 80% enforced in `./mvnw verify`
- [x] **Hibernate L2 cache with Hazelcast** (client/server, native region factory, query cache) — done, PR #12 · _not a numbered session; see [`../spec/hibernate-l2-cache-hazelcast.md`](../spec/hibernate-l2-cache-hazelcast.md)_
- [x] **#6 — Session 6: Docker & CI/CD** (multi-stage image, GH Actions) — done, PR #13; multi-arch images added in PR #14 · _Sonar gate wired but skipped until a `SONAR_TOKEN` secret exists — see [`../docs/ci-cd.md`](../docs/ci-cd.md)_
- [x] **#7 — Session 7: K8s & GitOps** (kind, Flux, overlays, rollback) — done, PR #15; Flux Operator + `FluxInstance`, Postgres via CloudNativePG · _`ServiceMonitor` deferred to #8 (needs the Prometheus Operator CRDs); see [`../docs/kubernetes-gitops.md`](../docs/kubernetes-gitops.md)_
- [ ] **#8 — Session 8: Observability** (logs/metrics/traces/alerts, /ship) · _needs #7_

### Checkpoints
- ⛳ After #1 · After #2 · After #4 · After #5 · After #6 · After #7 · After #8 — review gate before proceeding (see plan.md).
