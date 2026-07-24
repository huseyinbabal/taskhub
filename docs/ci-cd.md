# Docker & CI/CD

How TaskHub is packaged and shipped (SPEC §Session 6, issue #6).

## Images

One multi-stage `Dockerfile` per deployable module:

| Module | File | Port | Health |
|---|---|---|---|
| `taskhub-api` | `taskhub-api/Dockerfile` | 8080 | `curl /actuator/health/readiness` |
| `notification-service` | `notification-service/Dockerfile` | 9090 | `grpc_health_probe -addr=:9090` |

Both share the same shape:

1. **Stage 1 — `eclipse-temurin:25-jdk`.** Runs the Maven build. Poms and
   `taskhub-proto` are copied before the module's own sources, so the dependency
   layer stays cached until a pom changes. Ends by splitting the fat jar with
   `java -Djarmode=tools ... extract --layers --launcher`.
2. **Stage 2 — `eclipse-temurin:25-jre`.** Copies the extracted layers in
   change-frequency order (dependencies → loader → snapshots → application) and
   runs as the non-root `taskhub` user (uid 10001). No JDK, Maven or source.

The build context is the **reactor root**, not the module directory:

```bash
docker build -f taskhub-api/Dockerfile -t taskhub-api .
docker build -f notification-service/Dockerfile -t notification-service .
```

`notification-service` speaks gRPC only, so there is no HTTP endpoint to curl —
its healthcheck probes the standard `grpc.health.v1.Health` service that
spring-grpc registers, using `grpc_health_probe` (pinned via the
`GRPC_HEALTH_PROBE_VERSION` build arg).

## Workflows

### `ci.yml` — the merge gate

Runs on every pull request and every push to `main`.

- **`verify`** — `./mvnw verify`: all tests plus the JaCoCo line gate (≥ 80%,
  configured in the parent `pom.xml`). The HTML coverage report is uploaded as a
  build artifact. A failing test *or* a coverage drop fails the job.
- **`image`** (matrix over both modules) — builds the image, asserts it runs as
  non-root and carries no build tooling, then starts the container and waits for
  Docker's own `HEALTHCHECK` to report `healthy`. Postgres and Hazelcast run as
  service containers for the `taskhub-api` leg. Nothing is pushed.

### `build.yml` — publish to GHCR

Runs on push to `main`, on `v*` tags, and on manual dispatch. Pushes to
`ghcr.io/huseyinbabal/taskhub/<module>`:

| Trigger | Tags |
|---|---|
| push to `main` | `sha-<short>`, `latest` |
| tag `v1.2.3` | `sha-<short>`, `1.2.3`, `1.2` |

Auth uses the built-in `GITHUB_TOKEN` with `packages: write` — no PAT needed.
Images are `linux/amd64`; an arm64 leg would run the whole Maven build under
QEMU, so it is left off until there is a reason for it.

`build.yml` does not re-run the tests — it packages what already landed on
`main`. **Require the `CI` check in branch protection** so nothing reaches
`main` untested.

## Enabling the Sonar gate

The Sonar scan is written but skipped while the `SONAR_TOKEN` secret is absent.
To turn it on:

1. Create the project on SonarQube Cloud (`huseyinbabal_taskhub` under the
   `huseyinbabal` organization — adjust the `-Dsonar.projectKey` /
   `-Dsonar.organization` flags in `ci.yml` if you pick different values).
2. `gh secret set SONAR_TOKEN --repo huseyinbabal/taskhub`.

`-Dsonar.qualitygate.wait=true` is already set, so a red quality gate fails the
job and blocks the merge.
