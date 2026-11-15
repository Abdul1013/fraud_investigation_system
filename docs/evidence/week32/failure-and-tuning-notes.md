<!--
============================================================================
FILE:        docs/evidence/week32/failure-and-tuning-notes.md
PURPOSE:     Preserve observed operational bugs and measurement limits.
OWNER:       Fraud Investigation Engineering
SINCE:       week-32
RELATED:     context.md, curriculum week 32
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Failure and tuning notes

The first live trace check found a real executor propagation gap: SecurityContextWork copied authentication but not OpenTelemetry context. A tool proposal started a separate trace and some audit rows had no trace ID. Capturing both contexts fixed the issue; the corrected real Temporal restart scenario now has one correlated audit trace and its stored Tempo graph contains every required stage.

The live Prometheus provider-error alert fired and recovered after a real HTTP fixture returned slow 503 responses and was restored. This fixture is labelled separately from the actual Ollama token-accounting sample. The sample records provider token counts and a configured local compute estimate; it does not measure electricity, hardware depreciation or an invoice.

Backend inspection showed that this OTLP registry exports timer histograms in milliseconds. The dashboard and latency alert now convert those actual metric units to seconds. Dashboard provisioning and Prometheus target/rule readiness were queried through their real APIs.

An operational-profile full-suite run failed on Keycloak HTTP readiness and the MCP subprocess's cold-start timeout. The ephemeral Keycloak fixture now explicitly chooses local HTTP transport, and application contexts are closed before their test databases stop. The MCP integration test now allows a bounded 60-second cold-start window; application model/tool deadlines are unchanged. These corrections need a passing reconstructed-source regression before completion is claimed.

The collector strips unrestricted attributes and exception messages. Metric dimensions remain bounded. Short local windows do not establish a 28-day SLO, semantic model accuracy, production TLS, or real MFA in the fixture workflow trace. Genuine OTP evidence is in Week 31.

A later cold-start trace refresh exceeded the test helper's 15-second polling window. Stored spans showed launch delivery about 6.3 seconds and activity spans under 1.5 seconds, so the test polling window is now 45 seconds. Runtime per-call and active-investigation deadlines are unchanged. The corrected scenario must pass before the milestone is sealed.
