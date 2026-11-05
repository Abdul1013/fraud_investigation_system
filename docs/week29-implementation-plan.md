<!--
============================================================================
FILE:        docs/week29-implementation-plan.md
PURPOSE:     Map durable-workflow resources to the project implementation and acceptance checks.
OWNER:       Fraud Investigation Engineering
SINCE:       week-29
RELATED:     context.md, curriculum week 29
NOTES:
  - Keep measured results separate from fixtures and pending human review.
============================================================================
-->
# Week 29: durable AI orchestration

## Resource-to-module map

Temporal workflow/replay concepts map to `orchestration.domain.InvestigationWorkflow`.
Model, tool and SQL I/O live in activities, not deterministic workflow code. Transactional
outbox delivery maps to owning orchestration/decision adapters; neither reads the other's
private repository. SDK 1.40.0 is pinned independently of Spring Boot integration.

## Ordered implementation

1. Verify committed Week 28 without the existing uncommitted ingestion fixes.
2. Preserve launch/status/events APIs; select Temporal by default and local only explicitly.
3. Commit launch intent, delegation, reservation and launch outbox atomically.
4. Persist model response and checkpoint receipts; use stable proposal operation keys.
5. Deliver human outcomes without approval tokens; wait durably and never call execution.
6. Demonstrate real worker/server restart, retry/exhaustion, revocation, cancellation,
   duplicate delivery and one simulated consequential effect.
7. Produce walkthrough, runbook, verification outputs and reconstructed weekly source bundle.

## Safety and compatibility

Use additive V12 after V11. Legacy NEW/INVESTIGATING records become RECOVERY_REQUIRED;
never automatically repeat uncertain work. Existing pending recommendations remain human
review records and are not re-investigated. Keep Basic-era tests on the explicit local
engine. MCP tools do not require an unrelated Temporal worker. Neither profile invents MFA.

Model receipts prevent repeat calls after their commit, not an impossible guarantee of
exactly-once remote inference across an uncertain crash. Stable business operation keys
and the existing transactional write gate protect consequential effects.

## Acceptance

The real PostgreSQL/Temporal integration suite and the complete prior regression suite
must pass. Inspect history to exclude prompts, evidence and approval credentials. Verify
source reconstruction before moving to Week 30. Record native-model checks separately
from scripted model replies. Human review/commit/tag remain pending.
