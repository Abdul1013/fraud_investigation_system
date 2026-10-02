# ADR-003: Model/Provider Abstraction via a Single Gateway with Task-Based Routing

**Status:** Accepted
**Date:** 2026-09-25
**Deciders:** Architecture Lead, Platform Lead, Security Lead, Finance/FinOps
**Technical Area:** Backend / AI Platform / Security

## 1. Context

The engine uses multiple model capabilities: evidence summarization, query rewriting, fraud reasoning, compliance obligation checking, embeddings, and customer communication drafting. Each has different quality, latency, cost, and data-handling requirements. Provider pricing, capability, availability, and compliance posture change faster than the domain does.

Key constraints:

* Domain services must not hard-code a provider SDK; vendor lock-in would make two-version comparison and cost optimization impossible.
* Redaction and data-classification enforcement (C3 must never leave the boundary) must be a single, non-bypassable point.
* Token and cost budgets must be enforced per case to keep unit economics bounded (SLO: ≤ $0.35 per case).
* Provider outages must degrade gracefully, not fail the case.
* Every decision must record model, prompt, and config versions for audit and evaluation.
* Different tasks need different model tiers; using a frontier model for query rewriting wastes budget, and using a small model for fraud reasoning risks quality.

Affected architecture: every context that calls a model (`detection`, `orchestration`, `knowledge`, `decision`), plus `platform`, `audit`, and `eval`.

## 2. Decision

> We will route **all model and embedding traffic through a single `model-gateway`** that owns task-based routing, redaction, prompt registry, budget guards, fallback chains, and telemetry; domain services depend only on narrow `ChatPort` and `EmbedPort` interfaces and never on a provider SDK.

The gateway is stateless and policy-driven. Routing policy maps task → tier → provider. Redaction enforces classification ceilings before any external call. Every call is tagged with model, prompt, config, and routing-policy versions, and those versions flow into audit and eval records.

## 3. Options Considered

### Option 1 — Single model gateway with task-based routing

**Pros**

* One enforcement point for redaction, budget, and classification.
* Provider swap and A/B comparison become configuration, enabling Week 30 regression comparison.
* Central telemetry: cost, latency, tokens, version labels in one place.
* Task-based routing optimizes cost/quality per task.
* Fallback chains and degradation modes implemented once.
* Domain services stay clean; no provider SDK leaks into business logic.

**Cons**

* One more hop; the gateway must be highly available and low-overhead.
* Risk of the gateway becoming a god-service if policy grows unbounded.
* Requires disciplined versioning of prompts and routing policy.

### Option 2 — Direct SDK calls from each domain service

**Pros**

* Fewest hops; lowest latency.
* Simplest to write initially for a single provider.

**Cons**

* Vendor lock-in scattered across the codebase.
* Redaction and budget enforcement must be duplicated and can be bypassed.
* No central cost or version telemetry; Week 30 and Week 33 become very hard.
* Provider outage handling duplicated per service.
* Credentials distributed across services; larger secret surface.
* Directly contradicts the model/provider abstraction requirement.

### Option 3 — Framework-level abstraction only (Spring AI `ChatClient` used directly)

**Pros**

* Uses the stated abstraction (`ChatClient`, `EmbeddingModel`).
* Less code than a custom gateway.
* Provider swap at the framework level is supported.

**Cons**

* No central redaction, budget, or routing policy enforcement.
* No single place for version tagging or telemetry decoration.
* Fallback and degradation logic must live in each caller.
* Cost tracking is per-caller, so per-case budget enforcement is unreliable.
* Prompt registry and versioning are not provided.

### Option 4 — Self-hosted models only

**Pros**

* Full data control; no external data egress.
* Predictable unit cost at high volume.

**Cons**

* Operational burden (GPU fleet, scaling, upgrades) out of scope for a 10-week timeline.
* Quality gap for fraud reasoning without significant fine-tuning.
* Does not match the provider-flexibility goal.
* Rejected as the *only* option, but retained as a provider *behind* the gateway.

## 4. Rationale

* **Security:** Redaction at the gateway is the single non-bypassable control that keeps C3 data inside the boundary. Direct SDK calls would require every caller to implement it correctly.
* **Cost:** Task-based routing plus per-case budget guards is the only reliable way to hold the ≤ $0.35 per case SLO. Central telemetry makes Week 33's cost comparison meaningful.
* **Maintainability:** Provider changes, prompt changes, and routing changes are configuration, not code changes across N services.
* **Scalability:** The gateway is stateless and horizontally scalable; it can be the first extraction candidate from the monolith (ADR-001) when load demands it.
* **Complexity:** The gateway is a bounded, well-understood component. Centralizing policy is simpler than distributing it.
* **Developer experience:** Domain developers use a narrow port and get redaction, fallback, telemetry, and budget for free.
* **Integration:** Spring AI's `ChatClient`/`EmbeddingModel` abstractions sit *behind* the gateway as provider adapters, satisfying resource guidance while adding the policy layer.
* **Team expertise:** One gateway is easier to review and test than N call sites.

## 5. Consequences

### Positive

* Provider swap is configuration; Week 30's two-version comparison and Week 33's cost optimization become tractable.
* Redaction, classification ceilings, and budget are enforced once and cannot be bypassed.
* Central cost, latency, token, and version telemetry feeds observability directly.
* Fallback and degradation modes are implemented once and behave consistently.
* Audit records can always name the model, prompt, and config version behind a decision.

### Negative / Trade-offs

* One additional network hop for every model call.
* The gateway is a potential single point of failure; must be stateless, replicated, and circuit-broken.
* Prompt registry and routing policy require versioning discipline and review.
* Risk of policy sprawl if the gateway grows beyond routing/redaction/budget/telemetry.

### Risks

* **Gateway as SPOF.** Mitigation: stateless, horizontally scaled, health-checked, with client-side timeouts and circuit breakers; degrade to rules-only mode if unavailable.
* **God-service creep.** Mitigation: gateway owns only routing, redaction, prompt registry, budget, fallback, and telemetry. Business logic stays in domain services. New responsibilities require an ADR amendment.
* **Prompt drift.** Mitigation: prompts are versioned artifacts in Git, loaded by `promptId` + `promptVersion`; no inline prompts in domain code.
* **Budget guard false positives.** Mitigation: per-case budgets are configurable per risk tier; over-budget pauses the investigation and escalates rather than failing it.
* **Redaction bypass via tool output.** Mitigation: tool outputs re-enter through the gateway before being fed back into a model; retrieval results are classification-tagged.
* **Credential exposure.** Mitigation: provider credentials live only in the gateway's secret scope; domain services have no provider credentials.

## 6. Implementation

* Define ports:
  * `ChatPort.complete(ChatRequest) : ChatResponse`
  * `EmbedPort.embed(EmbedRequest) : EmbedResponse`
* Implement `model-gateway` with:
  * `RoutingPolicy` — task → tier → provider, versioned.
  * `RedactionFilter` — enforces classification ceiling per caller identity; strips/tokenizes C3.
  * `PromptRegistry` — `promptId` + `promptVersion` → template; loaded from Git-backed config.
  * `BudgetGuard` — per-case token and cost caps; emits pause signal on breach.
  * `FallbackChain` — primary → secondary → degraded mode.
  * `TelemetryDecorator` — OpenTelemetry spans with model, prompt, config versions; emits cost and token metrics.
  * `ProviderAdapter` — Spring AI `ChatClient` / `EmbeddingModel` per provider (OpenAI, Anthropic, Azure OpenAI, local vLLM).
* Routing table (initial):

| Task                             | Tier   | Primary            | Fallback                 | Temp | Max tokens |
| -------------------------------- | ------ | ------------------ | ------------------------ | ---- | ---------- |
| Evidence summarization           | cheap  | small model        | mid model                | 0.0  | 2k         |
| Query rewrite                    | cheap  | small model        | none (skip rewrite)      | 0.0  | 512        |
| Fraud reasoning + recommendation | strong | frontier model     | mid model                | 0.1  | 4k         |
| Compliance obligation check      | strong | frontier model     | none (escalate to human) | 0.0  | 2k         |
| Embeddings                       | fixed  | embedding model vN | cached only              | —   | —         |
| Customer communication draft     | mid    | mid model          | template fallback        | 0.2  | 1k         |

* Degradation modes:
  * Strong unavailable → mid model + mandatory human review.
  * All providers unavailable → rules-only recommendation + human review; case marked `degraded`.
  * Budget exhausted → pause investigation, escalate with partial evidence.
* Version tagging: every response carries `model_provider`, `model_id`, `model_version`, `prompt_id`, `prompt_version`, `retrieval_config_version`, `routing_policy_version`, `gateway_version`. These flow into `audit-svc` and `eval-svc`.
* Tests:
  * Redaction unit tests (C3 never leaves; C2 redacted per policy).
  * Routing policy tests (task → tier mapping).
  * Fallback tests (primary failure → secondary → degraded).
  * Budget guard tests (breach pauses, does not fail).
  * Contract tests for `ChatPort` / `EmbedPort`.
* Observability: gateway emits per-call spans, cost counters, token histograms, and version labels; feeds Week 32 dashboards and Week 33 cost reports.

## 7. Related Decisions

* ADR-001: Modular monolith — gateway is the first planned extraction candidate.
* ADR-002: Human-in-the-loop write gate — redaction and budget reduce the attack surface and cost of the approval path.
* ADR-004: Durable workflows — over-budget and degraded modes are durable states, not exceptions.
* ADR-005: Versioned knowledge product — `retrieval_config_version` and `corpus_version` are part of the same version envelope.
* Spring AI Reference — `ChatClient`, `EmbeddingModel` used as provider adapters behind the gateway.
* NIST AI RMF — Model governance, supply chain risk.
* `docs/model-provider-strategy.md`

## 8. Review

**Review date:** 2026-12-25
**Review trigger:** Any of the following causes reconsideration:

* A provider deprecates a model used in production.
* Cost per case exceeds the SLO for two consecutive weeks.
* A provider outage causes a case failure that fallback did not prevent.
* A new capability (e.g., structured function calling, long context) changes the routing calculus.
* The gateway accumulates responsibilities beyond routing, redaction, budget, fallback, and telemetry.
* A data-residency or regulatory requirement forbids a provider.

**Status history:**

* 2026-09-25 — Proposed
* 2026-09-25 — Accepted
