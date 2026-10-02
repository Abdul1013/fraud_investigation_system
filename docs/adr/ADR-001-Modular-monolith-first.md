# ADR-001: Modular Monolith First, Service Extraction on Evidence

**Status:** Accepted
**Date:** 2026-09-25
**Deciders:** Architecture Lead, Backend Lead, Platform Lead
**Technical Area:** Backend / Architecture

## 1. Context

The fraud investigation engine spans eight bounded contexts: Signal Ingestion, Detection & Risk Scoring, Case Management, Investigation Orchestration, Knowledge & Evidence, Decision & Action, Audit/Reporting/Compliance, and Platform. The capstone runs 10 weeks with a small team, and the domain is not yet fully understood.

Key constraints:

* We must establish clear bounded contexts now to avoid a tangled domain later, but we do not yet know which contexts have genuinely different scaling, runtime, or team-ownership needs.
* Distributed systems impose real costs: network partitions, distributed transactions, cross-service tracing, deployment coordination, and local dev friction. Paying those costs before a driver exists is premature.
* Several contexts are probabilistic and latency-sensitive (detection, retrieval, agent runtime) while others are deterministic and transactional (case management, decision, audit).
* Explicit boundaries, ArchUnit-style enforcement is possible, and later we would be adding ingestion, retrieval, workflows, evals, observability, and deployment — any of which may create a real extraction driver.
* If we extract too early, we will extract along wrong seams and pay twice.

Affected architecture: all eight contexts, the service-boundary map, the deployment topology, and the CI/CD pipeline.

## 2. Decision

> We will build the fraud investigation engine as a **Spring Boot modular monolith with enforced package boundaries matching the eight bounded contexts**, communicating through in-process ports, and extract services only when a concrete driver appears (independent scaling, different runtime, or team split).

Each context lives in its own top-level package and exposes only a narrow `*Api` package. All cross-context calls go through those APIs. No context may import another context's internal packages.

## 3. Options Considered

### Option 1 — Modular monolith with enforced boundaries

**Pros**

* Single transaction boundary; case state changes are atomic without sagas.
* Single deployable; trivial local dev with `docker compose up`.
* Fast iteration; refactoring across contexts is a compile-time change.
* Boundaries are explicit and testable via ArchUnit, so extraction is mechanical.
* One observability pipeline; one set of correlation IDs; simpler debugging.
* Lower operational cost.

**Cons**

* Risk of boundary erosion under delivery pressure without strict enforcement.
* Single process means one bad context (e.g., runaway agent loop) can affect others unless isolated with bulkheads.
* Scaling is coarse — the whole app scales together until extraction.

### Option 2 — Microservices from day one

**Pros**

* Independent scaling per context from the start.
* Independent deployment and failure isolation.
* Matches the long-term target topology.

**Cons**

* Distributed transactions across case/decision/audit require sagas or outbox from week one.
* Local dev and testing complexity multiplies.
* Boundaries are locked in before the domain is understood, so wrong seams are expensive.
* Cross-service tracing, contract testing, and deployment coordination consume weeks that should go to AI-specific work (RAG, agents, evals).
* Premature operational cost with no measured scaling driver.

### Option 3 — Unstructured monolith (no enforced boundaries)

**Pros**

* Fastest to write initially.
* No package discipline overhead.

**Cons**

* Directly contradicts the goal of establishing bounded contexts.
* Makes later extraction extremely expensive and error-prone.
* Probabilistic and deterministic code intermix, which is a safety problem for the write gate.
* Violates the "AI-native architecture" outcome.

## 4. Rationale

* **Maintainability:** Explicit package boundaries plus ArchUnit enforcement give most of the design benefit of microservices without the runtime cost. Extraction later is a mechanical refactor, not a rewrite.
* **Developer experience:** A single Spring Boot app with `docker compose up` keeps the feedback loop tight.
* **Complexity:** Distributed transactions across case, decision, and audit are the hardest part of a fraud system. Keeping them in one process until proven otherwise is ,ost sufficient.
* **Safety:** The write gate (ADR-002) is easier to enforce when `decision` and `action` contexts can share a transaction and the audit append is atomic with the action.
* **Cost:** One deployable, one database, one observability stack during development.
* **Scalability:** The contexts most likely to need independent scaling later are `retrieval-svc`, `workflow-svc`, and `agent-runtime`. These are the extraction candidates and are already isolated behind ports.
* **Team expertise:** Solo dev distributed-systems overhead would consume time better spent on RAG quality, agent safety, and evals.
* **Integration requirements:** Extraction is planned, not prevented — the ports are the integration contracts.

## 5. Consequences

### Positive

* Rapid iteration with atomic case/decision/audit transactions.
* Boundaries remain explicit and enforceable; the service-boundary map stays valid.
* Extraction to services is a refactor, not a redesign, when a driver appears.
* One correlation-ID and tracing pipeline end to end.
* Lower operational and cognitive load.

### Negative / Trade-offs

* The whole application scales as one unit until extraction.
* A runaway component (e.g., an agent loop) can starve other contexts unless bulkheads and timeouts are enforced.
* Requires discipline: without ArchUnit, boundaries erode silently.

### Risks

* **Boundary erosion.** Mitigation: ArchUnit tests in CI that fail the build on cross-context imports; each context exposes only `*Api`.
* **Accidental coupling through the database.** Mitigation: each context owns its schema namespace; no cross-context joins outside the owning context's repository layer.
* **Resource contention.** Mitigation: dedicated thread pools / virtual threads per context, timeouts on all model and retrieval calls, bulkheads for agent execution.
* **Deferred extraction cost.** Mitigation: keep `retrieval-svc`, `workflow-svc`, and `agent-runtime` behind ports and free of in-process assumptions that would break over the network.

## 6. Implementation

* Create top-level packages: `ingestion`, `detection`, `case`, `orchestration`, `knowledge`, `decision`, `audit`, `platform`.
* Each package exposes a single `*Api` interface package; everything else is internal.
* Add ArchUnit rules:
  * No cross-context imports except through `*Api`.
  * No context may import `platform` internals except `platform.api`.
  * `decision` and `audit` are the only packages allowed to write action state.
* Define ports for the extraction candidates: `ChatPort`, `EmbedPort`, `RetrievalPort`, `WorkflowPort`, `ToolInvocationPort`.
* Configure per-context thread pools and timeouts; enable virtual threads (Java 21) for I/O-bound model and retrieval calls.
* Schema namespacing in Postgres: one schema per context (`ingestion`, `case`, `knowledge`, `decision`, `audit`, `platform`).
* Add a `docker compose up` dev environment with Postgres + pgvector and a local model gateway stub.
* CI: ArchUnit runs on every PR; a boundary violation fails the build.
* Document the extraction triggers in `docs/architecture/bounded-contexts.md`.

## 7. Related Decisions

* ADR-002: Human-in-the-loop gate for every write action — depends on `decision` and `audit` sharing an atomic boundary.
* ADR-003: Model/provider abstraction via a single gateway — the gateway is the first candidate for extraction.
* ADR-004: Durable workflow engine for investigations — `workflow-svc` is a planned extraction candidate.
* ADR-005: Knowledge base as a versioned data product — `knowledge` is a planned extraction candidate.
* `docs/architecture/bounded-contexts.md`
* `docs/api/service-boundary-map.md`

## 8. Review

**Review date:** 2026-12-25 (end of capstone)
**Review trigger:** Any of the following causes reconsideration:

* A context needs independent scaling that bulkheads cannot satisfy.
* A context needs a different runtime (e.g., Python for ML scoring) or release cadence.
* Team grows to the point where a context needs its own ownership boundary.
* Retrieval, workflow, or agent runtime latency/throughput demonstrably degrades the rest of the system.
* A distributed transaction requirement emerges that the monolith cannot satisfy.

**Status history:**

* 2026-09-25 — Proposed
* 2026-09-25 — Accepted
