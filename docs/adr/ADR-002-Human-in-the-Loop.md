# ADR-002: Human-in-the-Loop Gate for Every Write Action

**Status:** Accepted
**Date:** 2026-09-25
**Deciders:** Architecture Lead, Security Lead, Compliance Lead, Product Lead
**Technical Area:** Security / Backend / Compliance

## 1. Context

The engine can take actions with real consequences: freeze accounts, issue refunds, block transactions, escalate to human review, and file regulatory reports (SAR/STR). These actions are adverse, often hard or impossible to reverse, and legally significant. Meanwhile, the components recommending them — LLM agents, retrieval, ML scorers — are probabilistic and can be wrong, manipulated, or confidently hallucinate.

Key constraints:

* Regulatory regimes (AML/KYC, FCRA-style adverse action, GDPR) require explainability and human oversight for adverse decisions.
* Prompt injection via transaction memos, merchant names, KYC documents, and dispute narratives is a realistic attack.
* "Excessive agency" is a named LLM risk in OWASP Top 10 for LLM Applications and NIST AI RMF.
* Analysts must be able to override any recommendation, and the override must be recorded.
* Straight-through processing has real cost value, but only for actions that are low-value and reversible.
* The system must be defensible in an audit: who decided, on what evidence, with what model/prompt version.

Affected architecture: `decision`, `action`, `audit`, `agent-runtime`, `tool-registry`, and the analyst-facing API.

## 2. Decision

> We will require an **explicit human approval token for every write action**, issued by an authenticated analyst with step-up MFA, except a small, versioned, policy-as-code allowlist of low-value reversible actions below a configurable threshold; no probabilistic component may execute a write action directly.

`decision-svc` classifies every action as `read` or `write`. Read actions may execute autonomously. Write actions require a signed approval token bound to the case, the action, the evidence snapshot, and the recommending model/prompt versions. The allowlist is versioned, audited, and reviewed at a fixed cadence.

## 3. Options Considered

### Option 1 — Mandatory human approval for all write actions, with a narrow allowlist

**Pros**

* Eliminates the highest-severity failure mode: autonomous harmful action.
* Satisfies explainability and human-oversight expectations directly.
* Approval token binds decision to evidence and model versions, making audit trivial.
* Allowlist preserves some straight-through value for safe, reversible actions.
* Analysts can override, and overrides are captured as training signal.

**Cons**

* Limits straight-through processing rate; some cost savings deferred.
* Adds latency to every write action (human response time).
* Requires analyst capacity and an approval UI with step-up MFA.

### Option 2 — Confidence-threshold auto-execution

**Pros**

* Maximum straight-through processing.
* Lowest per-case cost at scale.

**Cons**

* LLM confidence is not calibrated; a high-confidence wrong answer is still wrong.
* No control against prompt injection that inflates confidence.
* Regulatory defensibility is weak: "the model was confident" is not an audit trail.
* A single bad threshold produces many harmful actions before detection.
* Rejected outright by the curriculum's "least agency" and human-oversight requirements.

### Option 3 — Post-hoc review (execute first, review after)

**Pros**

* Fastest possible resolution time.
* No approval latency.

**Cons**

* Harm is already done by the time review happens; refunds, freezes, and filings are not easily undone.
* Unacceptable for adverse actions and regulatory reporting.
* Transforms a prevention control into a detection control with a much larger blast radius.

### Option 4 — Full manual review, no automation at all

**Pros**

* Maximum safety.
* No new AI risk surface for actions.

**Cons**

* Abandons the core value proposition of the system.
* Does not scale and does not use the investigation automation that the AI layer provides.
* Not competitive with the project's stated business goal.

## 4. Rationale

* **Security:** The primary control against prompt injection and excessive agency is that no probabilistic component holds write authority. Redaction, sandboxing, and allowlists reduce the attack surface; the human gate removes the payoff.
* **Compliance:** Adverse action and regulatory reporting require a human decision-maker of record. The approval token makes that human explicit, named, and timestamped.
* **Maintainability:** Centralizing write authority in `decision-svc` means one place to audit, test, and evolve. `tool-registry` marks each tool read/write; `agent-runtime` can call read tools freely but cannot call write tools without a token.
* **Cost:** The allowlist recovers straight-through value for low-value reversible actions (e.g., auto-refund below a threshold, auto-close a confirmed false positive), so we do not lose all automation benefit.
* **Developer experience:** The gate is enforced at the tool boundary, not scattered across agents, so developers cannot accidentally bypass it.
* **Complexity:** A token-issuance and verification flow is small and well-understood; the alternative (calibrating confidence thresholds safely) is far more complex and less reliable.
* **Integration:** Approval integrates cleanly with the durable workflow (ADR-004) — the workflow pauses on a human task and resumes on the approval signal.

## 5. Consequences

### Positive

* The highest-severity failure mode autonomous harmful action is designed out.
* Every write action has a named human decision-maker and a full evidence/version snapshot.
* Overrides become a high-quality feedback and evaluation signal.
* The allowlist is a policy artifact that can be tuned without code changes.
* Audit and regulatory defensibility are strong by construction.

### Negative / Trade-offs

* Straight-through processing is limited to the allowlist, so cost per case is higher than a fully autonomous design.
* Approval adds human latency to write actions; SLAs must account for it.
* Requires an approval UI with step-up MFA and analyst staffing.

### Risks

* **Approval fatigue.** Mitigation: risk-tiered routing; only route to humans when the action is a write above the allowlist; batch low-risk approvals; monitor override rate and time-to-approve.
* **Rubber-stamping.** Mitigation: require analysts to acknowledge the key evidence and citations; sample-audit approvals; surface disagreement between agent recommendation and analyst decision.
* **Allowlist creep.** Mitigation: allowlist is versioned, reviewed monthly, and any expansion requires a new ADR or an amendment to this one.
* **Token replay or forgery.** Mitigation: signed tokens bound to case, action, evidence hash, and model/prompt versions; short TTL; single-use; verified server-side.
* **Compromised analyst account.** Mitigation: step-up MFA, RBAC, anomaly detection on approval patterns, dual approval for high-value actions.

## 6. Implementation

* Add `ActionType` enum with `READ` / `WRITE` classification; `tool-registry` declares each tool's type and required scopes.
* Implement `decision-svc`:
  * `POST /v1/recommendations` — records a recommendation with evidence and version snapshot.
  * `POST /v1/approvals` — issues a signed, single-use, short-TTL approval token (JWT or HMAC), bound to `caseId`, `actionId`, `evidenceHash`, `modelVersion`, `promptVersion`.
  * `POST /v1/actions` — verifies token, executes action, appends audit event atomically.
* Implement policy-as-code allowlist:
  * Config file (YAML) versioned in Git with `allowlistVersion`.
  * Rules express action type, max value, reversibility, and required conditions.
  * Evaluated deterministically in `decision-svc`; no model involvement.
* Analyst API:
  * `GET /api/v1/queue` — prioritized queue.
  * `POST /api/v1/cases/{id}/decision` — approve / override with step-up MFA.
  * `POST /api/v1/cases/{id}/comment` — analyst note (feeds retrieval).
* Enforce in `agent-runtime`: write tools are not in the agent's tool allowlist; the agent can only *request* a write, which flows to `decision-svc`.
* Audit: every recommendation, approval, override, and action appends to `audit-svc` with actor, timestamp, evidence hash, and versions.
* Tests:
  * Unsafe-action rejection tests: agent attempting a write without a token must fail.
  * Token replay, expiry, and tampering tests.
  * Allowlist boundary tests (just below / just above threshold).
  * ArchUnit rule: no package other than `decision` may invoke a write tool.
* Dashboards: approval rate, override rate, time-to-approve, write actions blocked, allowlist hit rate.

## 7. Related Decisions

* ADR-001: Modular monolith — `decision` and `audit` share a transaction so approval, action, and audit are atomic.
* ADR-003: Model/provider gateway — redaction and budget guards reduce the input attack surface that feeds the approval decision.
* ADR-004: Durable workflows — human approval is a durable task, not an in-memory wait.
* ADR-005: Versioned knowledge product — citations in the recommendation are what the analyst reviews and approves against.
* OWASP Top 10 for LLM Applications (LLM06: Excessive Agency; LLM01: Prompt Injection).
* NIST AI RMF — Human oversight and accountability.
* `docs/data-classification.md` — C3 handling rules that constrain what can appear in approval UI.

## 8. Review

**Review date:** 2026-12-25
**Review trigger:** Any of the following causes reconsideration:

* Regulatory guidance changes regarding automated adverse action.
* Override rate falls below a threshold that suggests the agent is reliable enough to expand the allowlist (still requires an amendment, not automatic).
* Approval fatigue metrics show rubber-stamping (e.g., median approval time under 2 seconds with no evidence review).
* A new action type is introduced that is irreversible and high-value.
* A prompt-injection incident occurs that reaches the approval stage.

**Status history:**

* 2026-09-25 — Proposed
* 2026-09-25 —
