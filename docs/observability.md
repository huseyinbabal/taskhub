# Observability

Logs, metrics and traces for TaskHub (SPEC §Session 8, issue #8).

## The stack

Installed by the `infrastructure` Flux Kustomization, all single-replica and
filesystem-backed — sized for one kind node, not for retention.

| Component | Chart | Role |
|---|---|---|
| kube-prometheus-stack | `87.x` | Prometheus, Grafana, Alertmanager, and the `ServiceMonitor`/`PrometheusRule` CRDs |
| Loki | `7.x` (SingleBinary) | log store |
| Alloy | `1.x` (DaemonSet) | tails pod stdout → Loki |
| Tempo | `1.x` | trace store, OTLP on 4317/4318 |

Grafana is provisioned with all three as datasources (`prometheus`, `loki`,
`tempo`). The Loki datasource carries a derived field that turns the `traceId`
in a JSON log line into a link into Tempo.

```bash
kubectl port-forward -n monitoring svc/kube-prometheus-stack-grafana 3000:80   # admin/admin
```

## Logs

Outside the `dev` profile both services log **ECS JSON** via Boot's built-in
structured logging (`logging.structured.format.console: ecs`) — no logback XML.
Every line carries `service.name`, and every line written inside a request
carries `traceId` and `spanId`:

```json
{"@timestamp":"...","log":{"level":"INFO","logger":"...CorrelationIdFilter"},
 "service":{"name":"taskhub-api"},"message":"POST /api/projects/1/tasks -> 201 in 24ms",
 "traceId":"ec8db6e4f0d55f369cdecdb33a587ca8","spanId":"20346ae52c58e7ad"}
```

Two things had to be true for that, and neither was free:

- **A Tracer must exist.** Boot 4 moved tracing autoconfiguration out of the
  actuator jar. With only `micrometer-tracing-bridge-otel` on the classpath,
  observations still produced metrics but no span was ever created and the
  correlation field came out empty. `spring-boot-starter-opentelemetry` is what
  brings the autoconfiguration.
- **The logging filter must sit inside the observation filter.**
  `CorrelationIdFilter` was at `HIGHEST_PRECEDENCE`, so it *wrapped* Boot's
  observation filter: by the time its `finally` block ran the span was closed and
  every access line reached Loki untraced. It now runs at
  `HIGHEST_PRECEDENCE + 10` — still far ahead of Spring Security (`-100`), so
  rejected requests keep their correlation id.

The correlation id from Session 3 is unchanged and still crosses gRPC as
metadata; the trace id is the machine-readable twin of it.

Query in Grafana → Explore → Loki:

```logql
{namespace="taskhub-dev", app="taskhub-api"} | json | traceId != ""
```

## Metrics

Both services expose `/actuator/prometheus` and are scraped via `ServiceMonitor`.
`notification-service` speaks only gRPC, so it runs a small HTTP port (8081)
purely for actuator.

Four scrape targets: `taskhub-api`, `notification-service`, `postgres` (the
CloudNativePG exporter, via `monitoring.enablePodMonitor`) and
`hazelcast-metrics` (chart's `metrics.enabled`).

**`management.metrics.distribution.percentiles-histogram` is mandatory.** Without
it Micrometer publishes only `_count`/`_sum`/`_max`, so every
`histogram_quantile()` — the p99 dashboard panel and the slow-request alert —
returns "no data" no matter how much traffic there is. This was only caught
under load; it is invisible when eyeballing a healthy service.

### Business metric

`TaskMetrics` listens to the existing `TaskChangedEvent`, so counting costs the
service layer nothing and cannot be forgotten at a new call site:

```
taskhub_tasks_changed_total{type="CREATED"|"UPDATED"|"ASSIGNED"|"COMPLETED"|"DELETED"}
```

### Dashboards

Dashboards are ConfigMaps labelled `grafana_dashboard: "1"`; the Grafana sidecar
imports them from any namespace.

| Dashboard | Source |
|---|---|
| TaskHub — RED + business | ours, `k8s/base/dashboard.yaml` |
| CloudNativePG | grafana.com **20417** rev 4 |
| Spring Boot Observability | grafana.com **17175** rev 2 |
| Hazelcast Default | grafana.com **13183** rev 1 |

Community dashboards are **vendored** into `k8s/infrastructure/dashboards/`
rather than fetched at reconcile time, so an upgrade is a reviewable diff. Their
`__inputs` blocks were resolved to the provisioned datasource uids on the way in.
They live in `infrastructure`, not an overlay: a dashboard uid must be unique
cluster-wide and both overlays would otherwise claim the same one.

## Tracing

Micrometer Tracing → OTel → OTLP/HTTP → Tempo. Boot auto-instruments the gRPC
client and server, so one trace spans both services.

**The OTLP property was renamed in Boot 4**: `management.otlp.tracing.endpoint`
is now `management.opentelemetry.tracing.export.otlp.endpoint`. Under the old key
the exporter is silently never configured — no error, no spans, and Tempo
answers 404 for every trace id.

A single task creation, as stored in Tempo:

```
taskhub-api           http post /api/projects/{projectId}/tasks          345.0ms  <- root
taskhub-api           security filterchain before                         23.6ms
taskhub-api           authorize request                                    0.5ms
taskhub-api           secured request                                    320.4ms
taskhub-api           …NotificationService/NotifyTaskEvent               200.0ms
notification-service  …NotificationService/NotifyTaskEvent                47.4ms
taskhub-api           security filterchain after                           0.2ms
```

Sampling is `1.0` — fine for a demo cluster, far too high for a busy service.

JDBC spans come from `datasource-micrometer-spring-boot`; without it a trace
stops at the service layer and never shows the query underneath.

## Alerts

`k8s/base/alerts.yaml`, five rules. Deliberately few — an alert nobody acts on is
worse than no alert.

| Alert | Fires when |
|---|---|
| `TaskHubApiHighErrorRate` | 5xx share > 5% for 1m |
| `TaskHubApiSlowRequests` | p99 > 1s for 5m |
| `TaskHubNotificationsFailing` | any non-OK gRPC publish for 2m |
| `TaskHubPodCrashLooping` | > 2 restarts in 10m |
| `TaskHubTargetDown` | scrape target down for 2m |

The error-ratio expression wraps its numerator in `or vector(0)`: with no 5xx
series at all the division returns *empty*, which reads as a broken panel rather
than a healthy service.

`TaskHubNotificationsFailing` is the one worth understanding. Task events are
best-effort — a failed publish never surfaces to the caller — so without an alert
the events just stop and nobody notices.

### Inducing a fault

```bash
# Flux corrects drift, so suspend first or the outage repairs itself mid-test.
flux suspend kustomization apps
kubectl scale deploy/notification-service -n taskhub-dev --replicas=0
# create tasks for >2m, then check Prometheus /alerts and Alertmanager
flux resume kustomization apps
```

Verified: 21 `UNAVAILABLE` publishes, `TaskHubNotificationsFailing` moved to
`firing` and reached Alertmanager as `active`.

## Load testing

Run the generator **inside** the cluster. `kubectl port-forward` is not a load
test harness — at concurrency 20 it collapsed and reported 1.37M
"connection refused", which looks like an application failure and is not one.

```bash
kubectl run oha -n taskhub-dev --rm -i --restart=Never \
  --image=ghcr.io/hatoo/oha:latest --command -- \
  oha -z 60s -c 20 --no-tui -H "Authorization: Bearer $TOKEN" \
  http://taskhub-api:8080/api/projects
```

Measured on kind: 161,756 requests in 60s, 100% success, ~2700 rps, mean 7.4ms.
