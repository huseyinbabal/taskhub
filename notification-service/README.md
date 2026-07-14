# notification-service

gRPC service that receives task events from `taskhub-api` and streams them to
subscribers (SPEC §Session 3). The contract lives in
[`taskhub-proto`](../taskhub-proto/src/main/proto/notification.proto) and is compiled
into stubs both modules consume.

| RPC | Kind | Purpose |
|-----|------|---------|
| `NotifyTaskEvent` | unary | `taskhub-api` reports a task mutation; the service records it, fans it out, and acknowledges with the number of subscribers reached. |
| `SubscribeTaskEvents` | server-streaming | A subscriber receives task events in real time, optionally filtered to one project. |

Every call passes through two global server interceptors: **logging** (method,
correlation id, final status, duration) and **auth**, which verifies the JWT that
`taskhub-api` propagates as `authorization` metadata and rejects anything else with
`UNAUTHENTICATED`. gRPC's own `health`/`reflection` services are exempt so probes and
tooling keep working.

Events are held in memory and fan-out is process-local: this service owns no schema
until the database slice, and more than one replica would need a shared bus.

## Run it

```bash
./mvnw -pl notification-service spring-boot:run   # gRPC on :9090
./mvnw -pl taskhub-api spring-boot:run            # REST on :8080 (needs docker compose up -d postgres)
```

Both dev profiles share the same non-production HMAC secret, so a token minted by
`taskhub-api` is accepted here. In every other environment `TASKHUB_JWT_SECRET` must
be set for both services to the same value, and `TASKHUB_NOTIFICATIONS_TARGET` points
`taskhub-api` at this service.

## Verify end to end

Subscribe (reflection is on in dev, so `grpcurl` can discover the contract):

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}' | jq -r .token)

grpcurl -plaintext -H "authorization: Bearer $TOKEN" -d '{"project_id": 1}' \
  localhost:9090 taskhub.notification.v1.NotificationService/SubscribeTaskEvents
```

Then mutate a task over REST — the event appears on the stream immediately:

```bash
curl -X POST localhost:8080/api/projects/1/tasks -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" -H 'X-Correlation-Id: my-trace' \
  -d '{"title":"Observe me","status":"TODO","priority":"HIGH"}'
```

`my-trace` shows up in both services' logs for that call. Dropping the
`authorization` header from the `grpcurl` command gets you `Unauthenticated`.
