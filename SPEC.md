# TaskHub — Master Specification

> The single source of truth for **TaskHub**, a team task-management service
> built one vertical slice per session across an 8-session course. `/plan`
> decomposes this spec into per-session tasks (which become GitHub issues); each
> session's slice lands as a reviewed PR. Each **course session maps to one
> `## Session N` section** below.

---

## 1. Objective

TaskHub lets a team organize work into **projects** and **tasks**, assign tasks
to members, tag and filter them, and receive **real-time notifications** as task
events happen. It is a backend product: a REST API for humans/clients plus a
gRPC notification service for event streaming.

**Target users**

- **Team members (`USER`)** — create/track their own projects and tasks, get
  notified about changes to work they care about.
- **Administrators (`ADMIN`)** — manage all projects/tasks and administer users.
- **Integrations / clients** — front-ends and services consuming the documented
  REST + gRPC contracts.

**Definition of done (whole product)** — every item ships behind a green build:

1. Authenticated, role-aware REST API for Users, Projects, Tasks, Tags with
   validation, pagination, and Swagger docs.
2. A separate gRPC `notification-service` streaming task events (unary +
   server-streaming) with interceptors.
3. PostgreSQL persistence with Flyway-managed schema and no N+1 hot paths.
4. A layered test pyramid (unit → slice → Testcontainers integration → E2E) with
   an enforced coverage gate.
5. A multi-stage Docker image published by a GitHub Actions pipeline.
6. Kubernetes (kind) deployment via Flux CD GitOps with dev/prod overlays and a
   documented rollback.
7. Production-grade observability: structured JSON logs, Prometheus metrics,
   Grafana dashboards, Loki log aggregation, distributed tracing, and alerts.

---

## 2. Architecture & Stack

**Runtime stack:** Java 25 · Spring Boot 4.1 · PostgreSQL · gRPC · Docker ·
GitHub Actions · Kubernetes (kind) · Flux CD · Prometheus / Grafana / Loki /
Tempo.

**Workflow:** Claude Code driven by the agent-skills lifecycle
`/spec → /plan → /build → /test → /review → /ship`.

**Maven coordinates & packages**

| Setting | Value |
|---------|-------|
| Group | `io.github.huseyinbabal.taskhub` |
| Base package | `io.github.huseyinbabal.taskhub` |
| Java | 25 |
| Spring Boot | 4.1.x |
| Build | Multi-module Maven (parent aggregator + `mvnw` wrapper) |

**Modules** (one repo, one reactor build):

```
taskhub/                         ← repo root (project root; this SPEC lives here)
├── pom.xml                      ← parent aggregator / dependency & plugin mgmt
├── taskhub-proto/               ← shared .proto contract → generated gRPC stubs
├── taskhub-api/                 ← REST service (web, security, JPA, Flyway) + gRPC client
└── notification-service/        ← gRPC server (task-event notifications)
```

`taskhub-api` is a **gRPC client** of `notification-service`: when task events
occur it calls the notification service, which fans them out to subscribers via
server-streaming. Both depend on `taskhub-proto` for the contract.

**Domain model** (owned by `taskhub-api`, provisioned via Flyway in Session 4):

| Entity | Key fields | Relations |
|--------|-----------|-----------|
| `User` | id, email (unique), username, passwordHash, roles, createdAt | owns Projects; assigned Tasks |
| `Project` | id, name, description, ownerId, createdAt | 1 owner (User); 1..* Tasks |
| `Task` | id, title, description, status, priority, dueDate, projectId, assigneeId | belongs to Project; optional assignee (User); 0..* Tags |
| `Tag` | id, name (unique), color | many-to-many with Task |

`Task.status ∈ {TODO, IN_PROGRESS, DONE}`; `Task.priority ∈ {LOW, MEDIUM, HIGH}`;
`User.roles ⊆ {USER, ADMIN}`.

---

## 3. Commands

All commands run from the repo root using the Maven wrapper.

```bash
# Build everything (all modules)
./mvnw clean install

# Build / run a single module
./mvnw -pl taskhub-api -am spring-boot:run           # REST API on :8080
./mvnw -pl notification-service -am spring-boot:run   # gRPC server on :9090

# Tests
./mvnw test                                          # unit + slice
./mvnw verify                                        # + integration (Testcontainers) + coverage gate
./mvnw -pl taskhub-api -Dtest=TaskServiceTest test   # single test class

# Coverage report (JaCoCo)
open taskhub-api/target/site/jacoco/index.html

# Local infra (Postgres, Prometheus, Grafana, Loki) for manual runs
docker compose up -d

# Container image (multi-stage)
docker build -t taskhub-api:local taskhub-api/

# Kubernetes (kind) — see Sessions 7 for full GitOps flow
kind create cluster --name taskhub
kubectl apply -k k8s/overlays/dev

# API docs (when taskhub-api is running)
# Swagger UI:  http://localhost:8080/swagger-ui.html
# OpenAPI:     http://localhost:8080/v3/api-docs
```

> Actual scaffold is generated with **Spring Initializr** (Session 1). Do not
> hand-write the wrapper or parent boilerplate — start from Initializr output.

---

## 4. Project Structure

```
taskhub-api/src/main/java/io/github/huseyinbabal/taskhub/
├── TaskhubApiApplication.java
├── config/            # Security, CORS, OpenAPI, gRPC client config
├── security/          # JWT filter, token service, UserDetails, method-security
├── user/              # controller · service · repository · entity · dto · mapper
├── project/           # controller · service · repository · entity · dto · mapper
├── task/              # controller · service · repository · entity · dto · mapper
├── tag/               # controller · service · repository · entity · dto · mapper
├── notification/      # gRPC client that publishes task events
└── common/            # GlobalExceptionHandler, error DTO, pagination helpers

taskhub-api/src/main/resources/
├── application.yml            # base config
├── application-dev.yml        # local/dev profile
├── application-prod.yml       # prod profile
└── db/migration/              # Flyway V1__*.sql, V2__*.sql, …

notification-service/src/main/java/io/github/huseyinbabal/taskhub/notification/
├── NotificationServiceApplication.java
├── grpc/              # service impl (unary + server-streaming) + interceptors
└── config/

taskhub-proto/src/main/proto/
└── notification.proto         # shared contract → generated stubs for both modules

k8s/
├── base/                      # Deployments, Services, ConfigMap, HPA, ServiceMonitor
└── overlays/{dev,prod}/       # Kustomize env overlays (replicas, resources, ingress)

clusters/                      # Flux CD GitOps sources & Kustomizations
.github/workflows/ci.yml       # build → test → sonar → image publish
```

**Layering (strict, per module):** `Controller → Service → Repository`.
Controllers never touch repositories or entities directly; services never return
entities across the HTTP boundary — only DTOs.

---

## 5. Code Style

- **Language level:** Java 25. Prefer records for DTOs and immutable value types;
  constructor injection only (no field injection).
- **Boundaries:** entities stay inside the persistence/service layer; controllers
  speak DTOs. Map with dedicated mappers (MapStruct or hand-written mapper class,
  chosen in Session 2 — kept consistent thereafter).
- **Validation:** Bean Validation (`jakarta.validation`) annotations on request
  DTOs; validation failures return a consistent error body.
- **Errors:** one `@RestControllerAdvice` `GlobalExceptionHandler` producing an
  RFC-7807-style problem JSON (`type`, `title`, `status`, `detail`, `instance`).
  No stack traces or internal messages leak to clients.
- **Naming:** `XxxController`, `XxxService`, `XxxRepository`, `XxxEntity`/`Xxx`,
  `XxxRequest`/`XxxResponse` DTOs. Packages by feature, not by layer-first.
- **Config:** no secrets in source. Externalize via env vars / Spring profiles;
  `application-{dev,prod}.yml` for profile deltas only.
- **Logging:** SLF4J; structured JSON in non-local profiles (Session 8). Never
  log credentials, tokens, or PII.
- **API:** RESTful resource paths under `/api`, plural nouns, proper status codes
  (201 on create with `Location`, 204 on delete, 400/401/403/404/409 as apt).
- **Proto:** `proto3`; version the package; backward-compatible field changes
  only (never renumber or reuse field tags).

---

## 6. Testing Strategy

A layered pyramid; `./mvnw verify` runs all of it plus the coverage gate.

| Layer | Tooling | Scope |
|-------|---------|-------|
| **Unit** | JUnit 5 + Mockito | Services & pure logic in isolation; collaborators mocked. |
| **Slice** | `@WebMvcTest` (+ Spring Security test), `@DataJpaTest` | Controller ↔ JSON/validation/security; repository queries. |
| **Integration** | Spring Boot Test + **Testcontainers** (Postgres) | Full context against a real containerized DB; Flyway applied. |
| **gRPC** | in-process gRPC + Testcontainers | notification-service unary + streaming + interceptors. |
| **E2E** | Testcontainers (API + notification + DB) | Auth → create project → create task → receive notification event. |

**Rules**

- Every new service method gets unit tests (happy path + validation/error paths).
- Every endpoint gets a slice test asserting status, body shape, and RBAC.
- Integration tests use Testcontainers — **never** an embedded/in-memory DB
  substitute for Postgres-specific behavior.
- **Coverage gate:** JaCoCo enforced in `verify`; **line coverage ≥ 80%** on
  `taskhub-api` and `notification-service` (excluding generated proto & config).
  A build under the threshold fails.
- Tests are deterministic and independent — no ordering dependencies, no shared
  mutable fixtures, no reliance on wall-clock or external network.

---

## Session 1 — AI-Assisted Project Setup

**Slice:** Skeleton + layered architecture + global exception handling + this
master SPEC.

- Bootstrap the multi-module project with **Spring Initializr** (parent +
  `taskhub-api`, add `taskhub-proto` / `notification-service` modules).
  Dependencies: Web, Data JPA, PostgreSQL, Validation, Actuator, Security (wired
  later), Flyway.
- Establish strict layered architecture and package-by-feature structure (§4).
- Add `GlobalExceptionHandler` + problem-JSON error contract (§5).
- Add `application-{dev,prod}.yml` profiles and externalized config.

**Acceptance**

1. `./mvnw clean install` builds all modules green.
2. App starts on the `dev` profile; `/actuator/health` returns `UP`.
3. An unmapped/error route returns the standard problem-JSON body, not a stack
   trace.
4. This `SPEC.md` exists at the repo root and describes the full product.

---

## Session 2 — REST API Design & Spring Security

**Slice:** Task + Project CRUD with DTO/validation + Swagger + JWT auth + RBAC +
CORS.

- CRUD REST endpoints for **Projects** and **Tasks** (+ **Tags**), request/response
  **DTOs** with Bean Validation, and **pagination** on list endpoints
  (`page`/`size`, bounded max size).
- **Swagger/OpenAPI** via springdoc; every endpoint documented with auth scheme.
- **JWT authentication:** `POST /api/auth/register`, `POST /api/auth/login`
  (returns a signed JWT); stateless security filter validates the token.
- **RBAC:** `USER` manages own resources; `ADMIN` manages all + `GET /api/users`.
  Enforced with method security (`@PreAuthorize`).
- **CORS** configured for the front-end origin(s) via config, not `*` in prod.

**Endpoints (indicative)**

```
POST   /api/auth/register            POST /api/auth/login
GET    /api/projects?page&size       POST /api/projects
GET    /api/projects/{id}            PUT  /api/projects/{id}    DELETE /api/projects/{id}
GET    /api/projects/{id}/tasks      POST /api/projects/{id}/tasks
GET    /api/tasks/{id}               PUT  /api/tasks/{id}       DELETE /api/tasks/{id}
GET    /api/tags                     POST /api/tags
GET    /api/users            (ADMIN only)
```

**Acceptance**

1. Unauthenticated calls to protected routes return `401`; wrong-role calls `403`.
2. A `USER` cannot read/modify another user's project (`403`/`404`).
3. Invalid request bodies return `400` with field-level validation details.
4. List endpoints return bounded pages with metadata; oversized `size` is capped.
5. Swagger UI renders all endpoints with the bearer-auth scheme.

---

## Session 3 — gRPC Microservice Communication

**Slice:** A separate `notification-service` with a proto contract, unary +
server-streaming task events, and interceptors.

- Define `notification.proto` in `taskhub-proto` (shared): a `NotifyTaskEvent`
  **unary** RPC and a `SubscribeTaskEvents` **server-streaming** RPC; message
  types for task-created/updated/assigned/completed events.
- Implement the gRPC server in `notification-service` (unary handler persists/
  fans out; streaming handler pushes events to subscribers).
- Add **interceptors**: server-side logging + auth (propagated JWT/metadata) and
  client-side correlation-id propagation.
- `taskhub-api` publishes task events as a **gRPC client** on task mutations.

**Acceptance**

1. A unary `NotifyTaskEvent` call from `taskhub-api` is received and acknowledged.
2. A client subscribed via `SubscribeTaskEvents` receives events in real time
   when tasks change.
3. Interceptors log every call and reject unauthenticated gRPC calls.
4. The proto contract compiles into stubs consumed by both modules.

---

## Session 4 — Database Layer (JPA + Flyway)

**Slice:** PostgreSQL persistence with Flyway migrations, entity relations, and
query optimization.

- **Flyway** migrations under `db/migration` create the `User`, `Project`,
  `Task`, `Tag`, and `task_tag` schema with constraints, FKs, and indexes.
- JPA entities and relations per §2 (Project 1..* Task; Task *..1 assignee; Task
  *..* Tag). `ddl-auto: validate` — schema is owned by Flyway, never Hibernate.
- **Query optimization:** eliminate N+1 on task/project listing (fetch joins or
  `@EntityGraph`); index columns used in filters (status, assigneeId, projectId).

**Acceptance**

1. On a clean database, Flyway applies all migrations and the app starts with
   `ddl-auto: validate` passing.
2. Listing tasks with tags/assignee issues a bounded number of queries (no N+1),
   verified by a test asserting query count.
3. Filtering tasks by status/assignee/tag uses indexed columns.
4. A repeat startup is idempotent (no duplicate migration, no drift).

---

## Session 5 — Test Strategy

**Slice:** The full pyramid from §6 wired into the build with a coverage gate.

- Unit tests (JUnit 5 + Mockito) for services.
- Slice tests: `@WebMvcTest` (+ security) for controllers, `@DataJpaTest` for
  repositories.
- **Testcontainers** integration tests against real Postgres; gRPC integration
  for notification-service.
- One **E2E** flow: register → login → create project → create task → assignee
  receives a streamed notification.
- **JaCoCo coverage gate** enforced in `verify` (≥ 80% line, §6).

**Acceptance**

1. `./mvnw verify` runs unit + slice + integration + E2E and passes.
2. Coverage report is produced; a build below threshold fails.
3. Integration tests spin up Postgres via Testcontainers (no in-memory DB).

---

## Session 6 — Docker & CI/CD

**Slice:** Multi-stage Docker image + GitHub Actions pipeline.

- **Multi-stage Dockerfile** per deployable module: build stage (Maven + JDK 25)
  → slim runtime stage (JRE), non-root user, layered jar, healthcheck.
- **GitHub Actions** `ci.yml`: on PR/push → build → test (`verify`, with
  Testcontainers) → **SonarQube/SonarCloud** scan (quality gate) → build & push
  image to a registry (GHCR) on main, tagged by commit SHA + semver.
- Fail the pipeline on test failure, coverage-gate failure, or Sonar quality-gate
  failure.

**Acceptance**

1. `docker build` produces a runnable image that starts and passes healthcheck.
2. A PR triggers build + tests + Sonar; a red gate blocks merge.
3. Merges to `main` publish a tagged image to the registry.
4. Image runs as non-root and contains no build tooling in the final layer.

---

## Session 7 — Kubernetes & GitOps

**Slice:** kind deployment via Flux CD with dev/prod overlays and rollback.

- **Kustomize** `base` + `overlays/{dev,prod}` for both services: Deployments,
  Services, ConfigMaps/Secrets refs, HPA, and `ServiceMonitor` for Prometheus.
- Postgres provisioned in-cluster (or via overlay-specific config); config &
  secrets injected per environment.
- **Flux CD** GitOps: the cluster reconciles from Git; `clusters/` holds
  `GitRepository` + `Kustomization`. Image updates flow through Git.
- Documented **rollback**: revert the Git commit (Flux reconciles back) and/or
  `kubectl rollout undo`.

**Acceptance**

1. `kubectl apply -k k8s/overlays/dev` (or Flux reconcile) brings both services
   to `Ready` on kind.
2. dev and prod overlays differ (replicas/resources/ingress) from a shared base.
3. Flux auto-syncs a committed manifest change without manual `kubectl apply`.
4. A bad deploy can be rolled back via Git revert and via `rollout undo`, both
   documented and demonstrated.

---

## Session 8 — Observability & Production Readiness

**Slice:** Structured logging, metrics, dashboards, log aggregation, tracing,
alerting — then `/ship`.

- **Structured JSON logging** with correlation/trace IDs (MDC) in non-local
  profiles, shipped to **Loki**.
- **Prometheus metrics** via Micrometer/Actuator (`/actuator/prometheus`),
  including custom business metrics (tasks created, notifications sent) and a
  scrape `ServiceMonitor`.
- **Grafana dashboards** for API latency/throughput/errors (RED) and JVM/DB
  health; **Loki** wired as a log source.
- **Distributed tracing** (Micrometer Tracing → Tempo/OTLP) spanning REST →
  gRPC → DB.
- **Alerting** rules (e.g. high error rate, p99 latency, pod crashloop,
  notification backlog).
- Run the **`/ship`** pre-launch checklist for go/no-go.

**Acceptance**

1. Logs are JSON with a trace/correlation id and appear in Loki/Grafana.
2. `/actuator/prometheus` exposes default + custom metrics; Prometheus scrapes
   them; Grafana dashboards render.
3. A request is traceable end-to-end across REST → gRPC → DB.
4. At least one alert rule fires on an induced fault (e.g. forced error spike).
5. `/ship` checklist passes with a documented go/no-go decision.

---

## Boundaries

Rules the agent follows for every slice.

### Always

- Follow this SPEC and the layered architecture (§4); DTOs at the HTTP boundary,
  entities never serialized to clients.
- Bootstrap/extend from **Spring Initializr** output and the `mvnw` wrapper;
  keep parent-managed dependency versions.
- Validate all external input; return the standard problem-JSON error contract.
- Write tests with each slice and keep `./mvnw verify` (incl. coverage gate)
  green before calling a slice done.
- Keep secrets out of source — env vars / profiles / K8s secrets only.
- Keep the proto contract backward-compatible (never reuse/renumber field tags).
- Work slice-by-slice on a feature branch and land via reviewed PR (`/review`).

### Ask first

- Adding or upgrading a **major dependency**, or changing the Java / Spring Boot
  baseline (Java 25 / Spring Boot 4.1).
- Changing a **public contract**: REST path/shape, the JWT/auth scheme, or the
  gRPC proto in a breaking way.
- Introducing a **new persistent entity**, relation, or a destructive/irreversible
  Flyway migration (data backfill, column drop, type change).
- Changing the **module topology** (adding/removing/splitting a module) or the
  chosen mapping approach (MapStruct vs. hand-written).
- Anything affecting **prod**: prod overlay values, secrets, CORS origins,
  scaling, or the coverage-gate threshold.
- Adding paid/external services (SonarCloud org, registry, cloud tracing backend).

### Never

- Commit secrets, tokens, or real credentials; never log PII or credentials.
- Let Hibernate manage the schema (`ddl-auto` beyond `validate`) — Flyway owns it.
- Delete or rewrite existing Flyway migrations that have shipped; only add new
  forward migrations.
- Weaken security to make something pass (disable auth/CSRF/validation, wildcard
  CORS in prod, log or return raw stack traces).
- Merge a slice with a failing build, failing tests, or coverage below the gate.
- Break the gRPC proto contract by reusing or renumbering field tags.
- Introduce N+1 queries on list endpoints or unbounded (non-paginated) list
  responses.
