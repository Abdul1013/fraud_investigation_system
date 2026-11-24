<!--
============================================================================
FILE:        infra/k8s/README.md
PURPOSE:     Explain and run the Kubernetes fundamentals example without EKS.
OWNER:       Fraud Investigation Engineering
SINCE:       week-34
RELATED:     context.md, curriculum week 34
NOTES:
  - Human review remains pending; executed checks determine technical status.
============================================================================
-->

# Local Kubernetes learning

This example teaches a Namespace, Secret reference, StatefulSet, persistent volume claim, one-off Jobs, Deployment, Service and probes. AWS staging uses ECS. This local example deliberately uses the explicit learning profile and disabled models/local runner; it does not demonstrate production identity or durable Temporal orchestration.

Prerequisites: a local Kubernetes cluster with a default storage class and the reviewed application image loaded as `fraud-engine:week34-review`. For kind, use `kind load docker-image fraud-engine:week34-review`; for minikube, use `minikube image load fraud-engine:week34-review`. Do not use an unrelated or billable cluster. Verify your current context before applying.

```bash
kubectl config current-context
kubectl apply -f infra/k8s/namespace-and-database.yml
# Generate private credentials into a pipe, without files or command arguments containing values.
python3 - <<'PYCODE' | kubectl -n fraud-learning create secret generic learning-database --from-env-file=/dev/stdin
import secrets
for name in ['administrator','migrator','runtime','temporal','identity']:
    print(name+'='+secrets.token_urlsafe(32))
PYCODE
kubectl -n fraud-learning rollout status statefulset/learning-postgres
kubectl apply -f infra/k8s/bootstrap-job.yml
kubectl -n fraud-learning wait --for=condition=complete job/fraud-bootstrap --timeout=180s
kubectl apply -f infra/k8s/migrate-job.yml
kubectl -n fraud-learning wait --for=condition=complete job/fraud-migrate --timeout=180s
kubectl apply -f infra/k8s/application.yml
kubectl -n fraud-learning rollout status deployment/fraud-api
kubectl -n fraud-learning port-forward --address 127.0.0.1 service/fraud-api 8080:8080
# In another terminal:
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

There are no default application accounts: protected routes remain inaccessible until explicit learning provisioning. PostgreSQL uses a separate owner/runtime arrangement; its administrator credential appears only in the bootstrap Job. Local PostgreSQL traffic is unencrypted; staging uses private RDS with verify-full TLS and real OIDC/OTP.

The YAML Jobs use JSON syntax after their comment headers, which is valid YAML and makes generated secret references unambiguous. No secret values are in these files. Kubernetes Secrets are base64 containers, not encryption by themselves; real cluster encryption, RBAC, admission/network policies and secret rotation need an operator review. This example is not production Kubernetes hardening.

Review before teardown: deleting `fraud-learning` deletes its example workloads, Secrets and PVC objects. Persistent-volume reclaim behaviour depends on the local storage class. Do not run teardown against an unrelated context. Cluster execution evidence remains pending until this example is actually run; parsing a manifest is not a deployment.
