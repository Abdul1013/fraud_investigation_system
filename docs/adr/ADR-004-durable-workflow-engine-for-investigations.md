# ADR-004: Durable Workflow Engine for Investigations

**Status:** Accepted
**Date:** 2026-09-25
**Deciders:** Architecture Lead, Backend Lead, SRE, Compliance Lead
**Technical Area:** Backend / Infrastructure / Reliability

## 1. Context

A fraud investigation is a long-running, multi-step process: plan, retrieve evidence, reason, request human approval (which may take hours), execute actions, and record audit events. Steps call external systems (model providers, KYC, payment rails) that fail, time out, and rate-limit. Human approval introduces waits measured in minutes to hours.

Key constraints:

* An HTTP request/response cannot hold this state; holding a request thread open for hours is not viable.
* Investigations must survive process restarts and deployments without losing progress.
* Every step must be idempotent and replayable for audit and debugging.
* Human approval is a durable wait, not an in-memory block.
* External calls fail; retries, backoff, and compensation are required.
* The system requires moving slow AI/RAG work out of request threads and using durable execution, queues, retries, idempotency, job status APIs, and compensation.
* Auditability requires a replayable state log — you must be able to reconstruct what happened, in order, with versions.

Affected architecture: `orchestration`, `case`, `decision`, `audit`, `agent-runtime`, and the analyst-facing status API.

## 2. Decision

> We will model every investigation as a **durable workflow** in a workflow engine (Temporal; Spring Batch + RabbitMQ/Kafka as the fallback implementation), with persisted, replayable state, worker-based execution, idempotent steps, and a status endpoint that exposes progress to analysts and admins.

The case API returns `202 Accepted` with an investigation ID. Clients poll the status endpoint or subscribe to events. Human approval is implemented as a durable signal that resumes the workflow. All steps are idempotent and keyed.

## 3. Options Considered

### Option 1 — Durable workflow engine (Temporal; Spring Batch + queue as fallback)

**Pros**

* Persisted, replayable state survives restarts, deployments, and outages.
* Human approval is a first-class durable signal; no thread held open.
* Built-in retries, backoff, timeouts, and compensation (sagas).
* Replayability directly satisfies auditability and debugging requirements.
* Workers scale independently from the API.
* Workflow versioning supports safe change management.
* Status and progress are queryable.

**Cons**

* Operational complexity: workflow server, worker fleet, versioning discipline.
* Learning curve for me.
* Temporal may be heavy for a capstone; Spring Batch + queue is a viable but less ergonomic fallback.

### Option 2 — Synchronous request/response

**Pros**

* Simplest to implement for short operations.
* No extra infrastructure.

**Cons**

* Cannot hold state across hours-long human approval.
* Thread exhaustion under load; a slow model call blocks a request thread.
* No replay; a restart loses in-flight investigations.
* No durable retries or compensation.
* Directly contradicts the Async workflow requirement.

### Option 3 — Database-backed job table with a scheduler

**Pros**

* No new infrastructure; uses Postgres.
* Full control over state transitions.

**Cons**

* Reimplementation of retries, backoff, timeouts, compensation, versioning, and visibility — all of which a durable engines provide.
* Concurrency, locking, and exactly-once semantics are easy to get wrong.
* Harder to make replayable and auditable.
* Becomes a bespoke workflow engine over time.

### Option 4 — Event-driven choreography (Kafka events, no central orchestrator)

**Pros**

* Loose coupling; scales well.
* Natural fit for ingestion and detection.

**Cons**

* No single place to see investigation state; hard to reason about and audit.
* Compensation and timeouts become implicit and error-prone.
* Human approval and workflow versioning are awkward.
* Better suited to the ingestion/detection edge than to investigation orchestration.

## 4. Rationale

* **Reliability:** Durable execution is the only option that survives restarts, deployments, and provider outages without losing progress. This is a hard requirement for a system that files regulatory reports.
* **Auditability:** A replayable state log is direct evidence for compliance and for the Week 28 replayable audit log deliverable.
* **Security:** Human approval as a durable signal means the approval gate (ADR-002) cannot be bypassed by process restarts or timeouts; the workflow simply waits.
* **Maintainability:** Retries, backoff, compensation, and versioning are provided by the engine, not reimplemented.
* **Scalability:** Workers scale independently; slow AI/RAG work does not consume API threads.
* **Cost:** Fewer stuck threads and fewer lost investigations means less wasted model spend on abandoned work.
* **Complexity:** Temporal adds infrastructure, but the alternative (bespoke job table) is more complex in the ways that matter ()correctness and auditability).
* **Team expertise:** Spring Batch + RabbitMQ is a familiar fallback if Temporal is out of scope; the workflow semantics are preserved.
* **Integration:** Approval UI, status API, and audit all read from the workflow's state and history.

## 5. Consequences

### Positive

* Investigations survive restarts, deployments, and provider outages.
* Human approval is durable; no thread held open; no lost approvals.
* Replayability gives a first-class audit and debugging story.
* Retries, backoff, timeouts, and compensation are standardized.
* Workers scale independently of the API.
* Workflow versioning supports safe rollout .

### Negative / Trade-offs

* Additional infrastructure: workflow server, worker fleet, persistence store.
* Must learn workflow versioning and determinism constraints (no non-deterministic code in workflow definitions).
* Local dev requires running the workflow engine (mitigated by Docker Compose).

### Risks

* **Non-deterministic workflow code.** Mitigation: workflow definitions call activities only; all I/O, randomness, and time are in activities. Enforced by code review and engine-side determinism checks.
* **Workflow versioning breaks in-flight runs.** Mitigation: use engine versioning APIs; never mutate a running workflow definition incompatibly; add new paths instead.
* **Activity duplication.** Mitigation: idempotency keys on every activity; activities are safe to retry; side effects guarded by the idempotency store.
* **Runaway workflows.** Mitigation: per-investigation timeouts, step limits, and budget guards (ADR-003) that pause and escalate.
* **Operational complexity.** Mitigation: Docker Compose for dev; managed Temporal or Spring Batch fallback; runbooks for stuck workflows.
* **Audit gaps.** Mitigation: every activity emits an audit event; workflow history is the source of truth and is exported to `audit-svc`.

## 6. Implementation

* Define the investigation workflow:
  1. `loadCase(caseId)`
  2. `planInvestigation(caseId)` — agent planner (ADR-003 gateway call)
  3. `gatherEvidence(caseId)` — retrieval + tool calls (read-only)
  4. `reason(caseId)` — fraud reasoning + recommendation
  5. `complianceCheck(caseId)` — obligation check
  6. `requestApproval(caseId, recommendation)` — durable human task (ADR-002)
  7. `executeAction(caseId, approvalToken)` — write action
  8. `recordAudit(caseId)` — append immutable events
  9. `closeCase(caseId)` — capture feedback
* Each step is an activity with:
  * Idempotency key: `caseId + stepName + inputHash`.
  * Timeouts and retry policy (exponential backoff, capped).
  * Explicit compensation where a partial action must be undone.
* Status API:
  * `POST /v1/investigations` → `202 Accepted` + `investigationId`.
  * `GET /v1/investigations/{id}` → state, current step, progress, elapsed, degraded flag.
  * `GET /v1/investigations/{id}/events` → ordered history for audit and replay.
* Human approval:
  * Workflow emits an approval task; `decision-svc` issues a token on approval; workflow receives a signal and resumes.
  * If approval is not received within SLA, workflow escalates or expires per policy.
* Idempotency store: Postgres table keyed by idempotency key, storing result hash and status.
* Outbox pattern for events that must be published exactly once (case state changes, audit events).
* Workers: separate deployment; concurrency tuned per activity type; slow model/retrieval activities isolated in their own worker pools.
* Tests:
  * Failure-recovery test: kill worker mid-investigation; workflow resumes and completes.
  * Duplicate-delivery test: same activity delivered twice; only one side effect.
  * Approval-timeout test: workflow escalates per policy.
  * Compensation test: partial action rolled back correctly.
  * Replay test: reconstruct state from history.
* Observability: workflow traces with correlation IDs; per-step latency and retry counts; stuck-workflow alert; queue depth alert.

## 7. Related Decisions

* ADR-001: Modular monolith — `workflow-svc` is a planned extraction candidate.
* ADR-002: Human-in-the-loop write gate — approval is a durable signal in the workflow.
* ADR-003: Model/provider gateway — over-budget and degraded modes are durable workflow states.
* ADR-005: Versioned knowledge product — retrieval activities are idempotent and version-tagged.
* Temporal Documentation; Spring Batch Reference; RabbitMQ Tutorials; Transactional Outbox Pattern.
* `docs/architecture/data-flow.md`

## 8. Review

**Review date:** 2026-12-25
**Review trigger:** Any of the following causes reconsideration:

* Workflow engine operational cost or complexity exceeds its reliability benefit.
* A simpler job-table implementation proves sufficient for the actual load.
* Investigation patterns change such that durable orchestration is no longer the right model (e.g., fully event-driven).
* Workflow versioning causes repeated in-flight failures.
* Managed workflow service becomes available and changes the cost/ops calculus.

**Status history:**

* 2026-09-25 — Proposed
* 2026-09-25 — Accepted
