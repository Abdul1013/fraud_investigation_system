<!--
============================================================================
FILE:        observability/runbooks/week32-troubleshooting.md
PURPOSE:     Troubleshoot operational symptoms while preserving evidence and human authority.
OWNER:       Fraud Investigation Engineering
SINCE:       week-32
RELATED:     context.md, curriculum week 32
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Week 32 troubleshooting

1. Check application readiness and container health. Confirm collector 4318, Prometheus 9090, Tempo 3200 and Grafana 3000 are reachable only on their documented loopback ports.
2. If telemetry is absent, verify the observability profile and OTLP endpoint configuration. Check collector startup/configuration errors and Prometheus target health before assuming the application stopped doing work.
3. For provider errors, check the local Ollama service and pinned model digest. Do not add a cloud or fixture fallback to silence an alert. Inspect sanitized status/error counters; do not log the provider request body.
4. For backlog, inspect committed pending outbox rows in the owning context, Temporal connectivity and worker task queue. Redeliver stable workflow IDs; do not manually create a second investigation to hide a delivery problem.
5. For latency, separate provider inference from retrieval, database waits and active queue time. A short p95 chart is a diagnostic, not a long-term SLO verdict.
6. For an execution error, stop further human attempts, inspect the authorized action audit and signed-approval/idempotency state, and verify whether the simulator already committed. Never automatically reverse a human-authorized freeze.
7. Correlate trace IDs with authorized audit metadata. Keep raw evidence, passwords, OTP seeds, bearer/approval credentials and prompts out of incident notes and telemetry exports.
8. After remediation, observe both successful requests and alert recovery. Preserve the firing/recovery timestamps and versions. Avoid claiming that an alert rule's presence proves it has worked.

The local Compose stack is disposable demonstration infrastructure. Do not delete shared database/model/workflow volumes as a troubleshooting shortcut. Cloud teardown and incident procedures are Week 34 artifacts.
