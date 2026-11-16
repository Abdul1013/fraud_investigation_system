<!--
============================================================================
FILE:        docs/week32-implementation.md
PURPOSE:     Explain lifecycle instrumentation, privacy choices and honest accounting.
OWNER:       Fraud Investigation Engineering
SINCE:       week-32
RELATED:     context.md, curriculum week 32
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Week 32 implementation walkthrough

Status: technical checks passed; human reviewed 

Read the public OperationalTelemetry contract first. Its Stage and Queue enums constrain metric dimensions at compile time. Other contexts use this port instead of importing exporter implementations. The platform implementation creates SDK spans, timers and counters with fixed labels. A release label is operator-owned and shape-checked; no case, subject, query or token is a metric tag.

Migration V16 stores only validated W3C trace identifiers on investigations/recommendations and a trace ID on action audit events. Launch captures the current parent. Outbox and activity code reload it within their owning boundaries; deterministic workflow code still performs no I/O. Human approval/execution continue the proposal's trace and link an incoming HTTP trace where appropriate. Trace context is correlation metadata, never authentication proof.

DecisionService keeps its existing transactional public methods around the measured implementation. Observation wrappers do not move the simulated effect or audit append out of their shared transaction. Audit history adds an optional traceId for authorized investigation of operational failures; it still requires case access and READ_AUDIT.

The gateway reports provider token counters only when available. Native tool responses also persist ModelUsage with their receipts; older three-argument response constructors and old receipt JSON remain compatible. Compute cost is a configured elapsed-inference-time estimate at an assumed USD hourly rate. API charged USD is a separate counter. A zero local API bill does not mean zero operating cost.

The collector receives pushed OTLP data and exposes the scrape endpoint inside its private Compose network. Prometheus evaluates fixed alert rules, Tempo stores sanitized traces, and Grafana presents local read-only dashboards. Administrative/query ports bind loopback. Viewer access is a local learning setting; deployment needs a separate access policy.

Privacy tests exercise actual SDK propagation through another thread, confirm parent IDs, and inject a synthetic secret-bearing exception to prove that exception text is not exported. The collector independently allowlists span/event attributes. A real provider failure and slow operation must still be exercised and the alert observed firing and resolving before completion is claimed.

Limitations: fixture workflow traces prove lifecycle plumbing, not native-model semantic quality or real MFA. Genuine OTP evidence remains in Week 31;. Local demonstration retention is short and does not establish a long-window reliability SLO. Actual dashboard, alert and trace evidence is preserved under docs/evidence/week32.

Spring concepts to notice: constructor injection gives each context the public telemetry port; Boot auto-configuration supplies the registry and SDK; the observability profile enables export explicitly; ApplicationRunner applies the log-privacy policy before readiness. The policy disables body/validation loggers and debug/trace output, and omits exception text from normal application logs. Diagnostics therefore rely on bounded outcomes, trace identifiers and authorized audit data.

The live test found and fixed a concrete asynchronous-context bug. Authentication and tracing must both be captured before work crosses the bounded executor; copying one does not propagate the other. The stored trace and audit now share the same trace identity through retries and restart. The exported timer unit is milliseconds, and dashboard/alert expressions convert it to seconds.
