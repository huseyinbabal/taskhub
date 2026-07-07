# Plan: TaskHub (8-session build)

Source spec: [`SPEC.md`](../SPEC.md) · Planning lives as **GitHub issues** on
`huseyinbabal/taskhub` (this file is the index).

## Approach

Each course session is **one vertical slice** — a complete path from HTTP/gRPC
edge through service → repository → DB, shippable as a reviewed PR. The plan is
tracked as issues #1–#8; `tasks/todo.md` is the checklist mirror.

## Dependency graph

```
#1 Setup (skeleton, layering, exception handling)
      │
      ▼
#2 REST + Security (CRUD, DTO/validation, pagination, Swagger, JWT, RBAC, CORS)
      │
      ├────────────► #3 gRPC notification-service (unary + streaming, interceptors)
      │
      ▼
#4 Database (Flyway migrations, relations, N+1 fixes)
      │
      ▼   (needs #2, #3, #4)
#5 Testing (unit → slice → Testcontainers → E2E + coverage gate)
      │
      ▼
#6 Docker & CI/CD (multi-stage image, GH Actions: build/test/sonar/publish)
      │
      ▼
#7 Kubernetes & GitOps (kind, Flux CD, dev/prod overlays, rollback)
      │
      ▼
#8 Observability (JSON logs, Prometheus/Grafana/Loki, tracing, alerting, /ship)
```

The chain is essentially linear (= session order). Only fan-out: #3 (gRPC) and
#4 (DB) both build on #2 and can proceed in parallel once #2 lands, but #5
requires both.

## Checkpoints (human review gates)

| After | Gate — must be true before next phase |
|-------|----------------------------------------|
| **#1** | All modules build; `/actuator/health` UP; problem-JSON error contract works. |
| **#2** | Auth + RBAC enforced (401/403); validation + pagination + Swagger verified. |
| **#4** | Flyway owns schema; `ddl-auto: validate` passes; no N+1 on list endpoints. |
| **#5** | `./mvnw verify` green incl. Testcontainers + E2E; coverage ≥ 80% gate holds. |
| **#6** | Image builds & runs non-root; PR pipeline (test+sonar) green; image published. |
| **#7** | Both services `Ready` on kind via Flux; rollback demonstrated. |
| **#8** | Logs/metrics/traces/alerts verified end-to-end; `/ship` go decision. |

## Work items (GitHub issues)

| # | Slice | Labels | Depends on |
|---|-------|--------|-----------|
| [#1](https://github.com/huseyinbabal/taskhub/issues/1) | Project setup | session, setup | — |
| [#2](https://github.com/huseyinbabal/taskhub/issues/2) | REST + Security | session, rest-security | #1 |
| [#3](https://github.com/huseyinbabal/taskhub/issues/3) | gRPC notifications | session, grpc | #2 |
| [#4](https://github.com/huseyinbabal/taskhub/issues/4) | Database (JPA+Flyway) | session, database | #2 |
| [#5](https://github.com/huseyinbabal/taskhub/issues/5) | Test strategy | session, testing | #2, #3, #4 |
| [#6](https://github.com/huseyinbabal/taskhub/issues/6) | Docker & CI/CD | session, ci-cd | #5 |
| [#7](https://github.com/huseyinbabal/taskhub/issues/7) | K8s & GitOps | session, kubernetes | #6 |
| [#8](https://github.com/huseyinbabal/taskhub/issues/8) | Observability | session, observability | #7 |

Each issue carries its own **task checklist**, **acceptance criteria**, and
**verify** steps (copied from the matching `SPEC.md` session section).

## Boundaries

Follow `SPEC.md → Boundaries` (Always / Ask-first / Never) for every slice: DTOs
at the edge, Flyway owns the schema, backward-compatible proto only, no merge
below the coverage gate, secrets never committed.
