<!--
============================================================================
FILE:        docs/week29-implementation.md
PURPOSE:     Explain durable workflow code, design choices, recovery procedure and measured scope.
OWNER:       Fraud Investigation Engineering
SINCE:       week-29
RELATED:     context.md, curriculum week 29
NOTES:
  - Keep measured results separate from fixtures and pending human review.
============================================================================
-->
# Week 29 implementation walkthrough

## Start with the boundary

The API still returns an investigation ID immediately. The launch method now commits a
run, a one-hour scoped delegation, an active reservation and an outbox row together.
Temporal is contacted afterward. If that RPC or acknowledgement fails, delivery retries
using the same workflow ID. Duplicate rejection prevents another workflow execution.

`InvestigationWorkflowImpl` is deterministic orchestration: bounded activity turns, state,
a durable human wait and cancellation. It does not read the database, call a model, get
the system clock or execute an action. Only identifiers/outcome strings enter its history.

## Activities and receipts

`DurableTurnActivities` reconstructs a narrowly provisioned investigator identity and
rechecks the case grant. Model replies and sensitive transcript checkpoints are stored
in PostgreSQL. Already-completed receipts are reused. Model/tool calls are bounded by
the existing timeout and read/proposal limits. Permission failures are nonretryable;
transient activity failures retry at most three times.

If an inference completed but its response was never committed, the provider call may
repeat. That uncertainty is explicit. The persisted response wins before tool dispatch;
a stable run/step operation key binds proposal persistence. Reusing that key with changed
input conflicts rather than silently creating another proposal.

## Human decisions

The existing human DecisionApi still owns approval and execution. Its signed credential,
state consumption, simulator change, result and audit transaction remain intact. A new
decision outbox commits only the outcome and recommendation reference. Workflow signals
are observations, never authorization. No approval token enters Temporal history.

`PENDING_APPROVAL` returns the activity worker; Temporal maintains the human wait. The
runtime MFA adapter still rejects until Week 31. Recovery tests explicitly simulate
verified step-up; they are not evidence of production MFA.

## Recovery runbook

1. Restart the worker against the same Temporal namespace/task queue and application DB.
2. Restart a local Temporal server with its persistent SQLite file/Compose volume intact.
3. Inspect launch outbox attempts, persisted receipts and workflow history before retrying.
4. Let undelivered outbox rows retry; do not generate a replacement workflow ID.
5. Reconcile legacy RECOVERY_REQUIRED records against recommendations/audit before launching
   new work with a new explicit key. Never automatically rerun an uncertain legacy loop.
6. Cancellation releases active temporary reservations/delegation; it never reverses a
   human-authorized effect. Reject a pending recommendation through the human decision API.

The Compose `durable` profile uses pinned Temporal CLI 1.4.1 with SQLite persistence for
local demonstrations. It is not the PostgreSQL-backed production Temporal deployment
prepared in Week 34. Startup: `docker compose --profile durable up -d`.

## Spring concepts and choices

`@Transactional` on managed store methods makes launch intent and completion checkpoints
atomic. It does not span external I/O. `@Scheduled` delivers retained outboxes. Public APIs
preserve module ownership; scheduled delivery never joins another context's private SQL.
The explicit `local` engine retains the Week 28 teaching implementation for compatibility.

## Verification

See `docs/evidence/week29/` for actual results. Recovery proof uses real isolated server
and database containers, scripted model replies and test-only step-up. Do not confuse a
restarted service with in-memory workflow time-skipping tests. Human review is pending.
