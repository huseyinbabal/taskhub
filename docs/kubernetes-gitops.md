# Kubernetes & GitOps

How TaskHub runs on a cluster and how changes get there (SPEC §Session 7, issue #7).

## Layout

```
k8s/
├── flux/flux-instance.yaml     # Flux itself, as a FluxInstance CR (applied once)
├── clusters/dev/               # what the dev cluster runs — Flux Kustomizations
├── infrastructure/             # CloudNativePG operator + chart sources
├── base/                       # environment-agnostic app + data tier
└── overlays/{dev,prod}/        # the differences
```

Nothing is applied by hand after bootstrap. `clusters/dev` holds two Flux
Kustomizations — `infrastructure` (operators, CRDs, `HelmRepository` sources)
and `apps` (the dev overlay), with `apps.dependsOn: infrastructure` so CRDs
always exist before the CRs that need them.

## The data tier is operator-managed

| Component | Managed by | Resource |
|---|---|---|
| Postgres | CloudNativePG operator | `postgresql.cnpg.io/v1` `Cluster` |
| Hazelcast | official OSS Helm chart via helm-controller | `HelmRelease` |

Neither is a Deployment we maintain. CloudNativePG owns the StatefulSet, PVCs,
failover and the connection services — `postgres-rw` (primary), `postgres-ro`
(replicas), `postgres-r` (any). `JDBC_DATABASE_URL` points at `postgres-rw`.

The Hazelcast **Platform Operator** was the first choice but its `Hazelcast` CR
requires `licenseKeySecretName` and defaults to the Enterprise image, so the
licence-free OSS chart is used instead — equally declarative, equally
Flux-managed.

## Overlays

| | dev | prod |
|---|---|---|
| namespace | `taskhub-dev` | `taskhub-prod` |
| replicas | 1 | 2 (+ HPA, 2–6 @ 70% CPU) |
| requests | 100m / 384Mi | 500m / 1Gi |
| Postgres | 1 instance, 1Gi | 2 instances, 10Gi |
| exposure | port-forward | Ingress (`taskhub.example.com`) |
| secrets | generated in-overlay (throwaway dev values) | **must pre-exist** |

`prod` deliberately has no `secretGenerator`. `taskhub-secrets` and
`taskhub-db-credentials` are created out of band (SOPS, sealed-secrets,
External Secrets, or `kubectl create secret`) — the SPEC boundary is that
secrets are never committed.

## Bootstrap on kind

```bash
kind create cluster --name taskhub

# 1. The operator that manages Flux
helm install flux-operator oci://ghcr.io/controlplaneio-fluxcd/charts/flux-operator \
  --namespace flux-system --create-namespace --wait

# 2. Git credentials — from the local gh CLI, never committed.
#    The repo is public, so this is about rate limits and being ready for a private one.
kubectl create secret generic flux-system -n flux-system \
  --from-literal=username=git \
  --from-literal=password="$(gh auth token)"

# 3. Flux itself, plus the sync that pulls everything else
kubectl apply -f k8s/flux/flux-instance.yaml

flux get kustomizations -A
kubectl get pods -n taskhub-dev
```

Reaching the API:

```bash
kubectl port-forward -n taskhub-dev svc/taskhub-api 8080:8080
curl localhost:8080/actuator/health
```

`taskhub-api` crash-loops for the first minute or two while CloudNativePG runs
`initdb` — expected, and it recovers on its own once `postgres-rw` answers.

## Images

Pinned to an immutable `sha-<short>` tag in `k8s/base/kustomization.yaml`.
`latest` is avoided on purpose: it moves under the cluster's feet and makes
"which commit is running?" unanswerable. **Deploying a new build is a one-line
commit** bumping those tags — which is also what makes the rollback a revert.

Images are published for `linux/amd64` and `linux/arm64` (`build.yml`), so the
same tag runs on a CI runner and on an Apple Silicon kind node.

## Rollback

Two paths, and they are not equivalent.

### `git revert` — the real one

```bash
git revert <bad-commit>
git push
```

Flux reconciles the cluster back to the reverted state. Because the `apps`
Kustomization sets `prune: true`, resources dropped from Git are also deleted —
a revert is a true rollback, not just an overwrite.

Measured on kind: a bad image tag (`sha-deadbee`) reached the cluster ~75s after
push and left the old pods serving while the new ReplicaSet sat in
`ImagePullBackOff`; the revert restored both services ~3m45s after push.

That lag is the `wait: true` health-check window: while the reconciler is
waiting out a failed rollout, it is not applying anything new, so the revert
only lands once that timeout expires. `timeout` on the `apps` Kustomization is
set to 5m for exactly this reason — raising it makes recovery slower.

### `kubectl rollout undo` — the stopgap

```bash
kubectl rollout undo deployment/taskhub-api -n taskhub-dev
```

Instant, and useful when a page is on fire. But it edits the cluster, not Git,
so the cluster is now drifted: the next successful reconcile re-applies the bad
manifest. Verified on kind — the undo held only while Flux was stuck in its
health-check window.

**Use `rollout undo` to stop the bleeding, then immediately revert in Git.**

## Not included

`ServiceMonitor` is listed in issue #7 but needs the Prometheus Operator CRDs,
which Session 8 installs. Applying it now would fail the reconcile, so it is
deferred to the observability slice.

The `HorizontalPodAutoscaler` in the prod overlay needs metrics-server; without
it the HPA reports unknown metrics and simply holds `minReplicas`.
