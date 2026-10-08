# AI-Native Fraud Investigation & Resolution Engine

Designed a working, AI-native integrated backend architecture that balances product value, maintainability, security, observability, provider flexibility, and operational cost. with product, risk, and operations alignment. using  Spring Boot backend, PostgreSQL database, AI provider access or sandbox, architecture diagram tool, project rubric.Design an AI-native

AI Practice Requirement: Prompt log, AI-output verification notes, and human review evidence.

> **Status:**
> **Tag:** `w`
> **Stack:** Java 21 · Spring Boot · Spring AI / LangChain4j · PostgreSQL + pgvector · Temporal (or Spring Batch + RabbitMQ fallback) · Docker Compose

## Table of Contents

1. [What this is](#1-what-this-is)
2. [Why it exists](#2-why-it-exists)
3. [Core design principle](#3-core-design-principle)
4. [Doc]()
5. [Security and compliance](#11-security-and-compliance)
6. [Repository layout](#12-repository-layout)
7. [Local run](#13-local-run)
8. [AI usage and decision log](#14-ai-usage-and-decision-log)
9. [How decisions are made](#15-how-decisions-are-made)
10. [Roadmap](#16-roadmap)
11. [Glossary](#17-glossary)

## 1. What this is

This repository contains the **architecture pack** for an AI-native fraud investigation and resolution engine. It establishes the technical strategy, boundaries, contracts, and decision records that forms the foundation of the project.

The engine investigates fraud alerts end to end:

1. **Ingests** signals from transactions, logins, devices, KYC feeds, and disputes.
2. **Scores** risk using rules, ML models, and graph analytics.
3. **Creates and prioritizes** investigation cases.
4. **Investigates** using bounded AI agents that retrieve cited evidence from a versioned knowledge base.
5. **Recommends** an action with reasons, confidence, and citations.
6. **Executes** the action only after a deterministic human-approval gate.
7. **Audits** every step immutably for regulatory and internal review.

## 2. Why it exists

Fraud investigation is a high-volume, high-stakes, document-heavy workflow. Today it is typically manual, inconsistent, and slow. Analysts spend most of their time gathering context — reading policies, cross-referencing prior cases, pulling transaction history — rather than deciding. Meanwhile, adversaries adapt quickly, and regulatory expectations for explainability and human oversight keep tightening.

This engine exists to:

- **Compress investigation time** from hours or days to minutes for routine cases.
- **Increase consistency** by grounding every recommendation in versioned policy and prior-case evidence.
- **Reduce false positives** by combining rules, ML, graph risk, and retrieval-backed reasoning.
- **Preserve human control** over every adverse action, with a full audit trail.
- **Keep AI quality measurable** through citations, groundedness checks, and evaluation gates.
- **Survive provider change** through a single model gateway with task-based routing.

It is deliberately **not** a fully autonomous fraud system. The core bet is that AI creates the most value when it does the investigation work which includes gathering, summarizing, reasoning, and recommending while deterministic backend controls and human analysts retain write authority.

## 3. Core design principle

> **Probabilistic components investigate. Deterministic components decide. Humans authorize.**

Concretely:

- Agents, retrieval, and ML models live in the **probabilistic layer**. They may read anything they are permitted to read and may *propose* anything. They may never execute a write action directly.
- The **deterministic layer** (`decision-svc`, policy-as-code, approval tokens) classifies actions, enforces allowlists, and issues signed approval tokens.
- **Humans** authorize every write action above a narrow, versioned allowlist of low-value reversible actions.

This principle is enforced structurally via package boundaries, tool classification, and an approval token that binds a decision to its evidence and model versions not by convention.

## 4. Doc

#### Architecture: Full diagrams, boundary maps, and data-flow documents live in:

- `docs/architecture/system-diagram.md`
- `docs/architecture/data-flow.md`
- `docs/architecture/bounded-contexts.md`
- `docs/api/service-boundary-map.md`

#### Bounded contexts:

The system is divided into eight bounded contexts. Each owns its data, exposes a narrow `*Api` package, and communicates with other contexts only through those APIs. Cross-context imports of internal packages fail the build via ArchUnit.

**Boundary rule:** probabilistic components (agents, models, retrieval) live only in contexts 2, 4, and 5. All write authority lives in context 6 behind a deterministic gate. Nothing probabilistic can directly mutate money or account state.

See `docs/architecture/bounded-contexts.md` for the full ownership map and extraction triggers.

#### Architecture Decision Records

Five ADRs establish the foundational decisions for the engine. Each follows a standard template: Context, Decision, Options Considered, Rationale, Consequences, Implementation, Related Decisions, and Review.

They rienforce each other by:

- **ADR-001** keeps the boundary between probabilistic and deterministic code enforceable at compile time.
- **ADR-002** is the primary safety control, and it depends on ADR-001 keeping `decision` and `audit` in one transaction.
- **ADR-003** is the single enforcement point for redaction, budget, and version tagging, which ADR-002 and ADR-005 both rely on.
- **ADR-004** makes the human approval in ADR-002 durable and replayable.
- **ADR-005** supplies the citations that ADR-002's approval UI presents and that ADR-003's version envelope records.

#### Data classification

All data in the system is assigned one of five classes at the field level. Classification is an attribute in the schema, not a table-level guess. Retrieval and model calls enforce a **classification ceiling per caller**: an agent running under a standard service identity can never retrieve C3 data.**Handling rules**

1. Classification is enforced at ingest, at retrieval, and at the model gateway. Three independent checkpoints.
2. Redaction happens at the **model gateway**, not in prompts, so it cannot be bypassed by prompt changes.
3. Embeddings inherit the classification of their source chunk; a C3 chunk is never embedded.
4. Audit records are C2/C3 and are append-only with 7-year retention.
5. Any new data source must declare its classification before it can be ingested.

Full policy: `docs/data-classification.md`.

#### SLOs

Service-level objectives define what "working" means and how much failure is acceptable. Error budgets are consumed by violations and govern whether automated write actions remain enabled.

**Failure policy:** if the error budget for "unsafe write attempts blocked" is ever consumed, all automated write actions are frozen and every action requires dual analyst approval until the root cause is identified and fixed.

Full definitions, burn-rate alerts, and dashboards: `docs/slos.md`.

#### Model and provider strategy

All model and embedding traffic flows through a single `model-gateway`. Domain services depend only on `ChatPort` and `EmbedPort`; no service holds provider credentials or imports a provider SDK. Every decision record stores the full version envelope so that any recommendation can be reproduced and audited:

```
model_provider, model_id, model_version,
prompt_id, prompt_version,
retrieval_config_version, corpus_version,
routing_policy_version, gateway_version
```

Degradation modes

- **Strong model unavailable** → mid model + mandatory human review.
- **All providers unavailable** → deterministic rules-only recommendation + human review; case marked `degraded`.
- **Budget exhausted for a case** → pause investigation, escalate to human with partial evidence.

Full policy: `docs/model-provider-strategy.md`.

#### Risk assumptions

The architecture is designed against the following explicit assumptions. If any of these change, the corresponding ADR should be revisited.

1. Fraud alerts are high-volume and bursty; ingestion must absorb 10x peaks without dropping signals.
2. Model providers will have outages and price changes; the system must survive both.
3. Adversaries will attempt indirect prompt injection via transaction memos, merchant names, KYC documents, and dispute narratives.
4. False negatives (missed fraud) are more costly than false positives, but false positives carry regulatory and customer-harm cost  so thresholds are policy decisions, not model decisions.
5. Analysts will not trust the system without citations and an override path; adoption is a functional requirement, not a nice-to-have.
6. Regulatory retention and explainability requirements are non-negotiable and cannot be retrofitted.
7. The corpus (policies, prior cases) will drift; stale retrieval is a correctness risk, not just a quality risk.
8. Cost per case must stay bounded or the unit economics fail at scale.
9. Audit logs must be tamper-evident for regulatory defensibility.
10. Human approval is the primary control against unsafe autonomy; it must be enforceable, not advisory.

Full list with mitigations: `docs/risk-assumptions.md`.

---

## 5. Security and compliance

#### Security controls

- **Prompt injection defense:** source trust rules, input/output filters, tool sandboxing, and the human approval gate as the final backstop.
- **Data protection:** field-level classification, tokenization for C3, redaction at the model gateway, and no C3 in embeddings.
- **Excessive agency prevention:** tool registry marks read vs. write; agents may not hold write tools; write authority is centralized in `decision-svc`.
- **Credential isolation:** provider credentials exist only in the model gateway's secret scope.
- **Immutable audit:** append-only audit log with tamper-evident hashing and 7-year retention.

#### Compliance posture

The design anticipates the following regimes and expects to be evaluated against them:

- **AML / KYC** — SAR/STR filing workflow, customer due diligence, retention.
- **Adverse action / FCRA-style** — explainable decisions with reason codes; human decision-maker of record.
- **GDPR / data protection** — classification ceilings, redaction, retention, and right-to-explanation support.
- **NIST AI RMF** — governance, mapping, measurement, and management of AI risk.
- **OWASP Top 10 for LLM Applications** — LLM01 (prompt injection), LLM06 (excessive agency), LLM02 (insecure output handling).
- **MITRE ATLAS** — adversarial threat modeling for AI systems.

Threat model and mitigation tests are planned for later on. Reference material: `docs/security/threat-model.md` (to be created).

## 6. Repository layout

```
fraud-investigation-engine/
├─ README.md                          # this file
├─ docs/
│  ├─ architecture/
│  │  ├─ system-diagram.md
│  │  ├─ data-flow.md
│  │  └─ bounded-contexts.md
│  ├─ api/
│  │  └─ service-boundary-map.md
│  ├─ data-classification.md
│  ├─ slos.md
│  ├─ model-provider-strategy.md
│  ├─ risk-assumptions.md
│  └─ adr/
│     ├─ 0001-modular-monolith.md
│     ├─ 0002-human-in-the-loop-write-gate.md
│     ├─ 0003-model-provider-gateway.md
│     ├─ 0004-durable-investigation-workflows.md
│     └─ 0005-versioned-knowledge-data-product.md
├─ ai/
│  └─ AI_DECISION_LOG.md              # prompt log, verification notes, human decisions
├─ src/main/java/.../
│  ├─ ingestion/
│  ├─ detection/
│  ├─ case/
│  ├─ orchestration/
│  ├─ knowledge/
│  ├─ decision/
│  ├─ audit/
│  └─ platform/
├─ docker-compose.yml                 # Postgres + pgvector + local gateway stub
├─ .github/workflows/                 # CI: build, ArchUnit, evals (later weeks)
└─ CHANGELOG.md
```

Planned additions in later weeks: `src/test/` with ArchUnit rules, `eval/` with golden datasets, `infra/` with Docker and Kubernetes manifests, and `observability/` with dashboards.

## 7. Local run

The `docker compose up` command below will start the supporting infrastructure (Postgres with pgvector and the model gateway stub).

```bash
# Clone
git clone <repo-url>
cd fraud-investigation-engine

# Start supporting infrastructure
docker compose up -d

# Verify Postgres + pgvector
docker compose exec postgres psql -U fraud -d fraud -c "CREATE EXTENSION IF NOT EXISTS vector;"

# Check gateway stub
curl http://localhost:8081/actuator/health
```

Environment variables (see `.env.example`):

| Variable                                  | Purpose                            | Default                                    |
| ----------------------------------------- | ---------------------------------- | ------------------------------------------ |
| `POSTGRES_URL`                          | JDBC URL for the primary database  | `jdbc:postgresql://localhost:5432/fraud` |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | DB credentials                     | `fraud` / `fraud`                      |
| `MODEL_GATEWAY_URL`                     | Base URL of the model gateway      | `http://localhost:8081`                  |
| `PROVIDER_API_KEY`                      | Provider credential (gateway only) | —                                         |
| `OTEL_EXPORTER_OTLP_ENDPOINT`           | Trace exporter endpoint            | `http://localhost:4317`                  |
| `APP_ENV`                               | `local` / `staging` / `prod` | `local`                                  |

## 8. AI usage and decision log

Prompt log, AI-output verification notes, and human-decision evidence. This repository maintains a single running log at `ai/AI_DECISION_LOG.md`.

Each entry records:

| Field                    | Meaning                                                                                                |
| ------------------------ | ------------------------------------------------------------------------------------------------------ |
| **ID**             | `AIL-NNN`                                                                                            |
| **Date**           | When the AI output was produced                                                                        |
| **Task**           | What the AI was asked to do                                                                            |
| **Prompt / model** | Which prompt and model were used                                                                       |
| **Output summary** | What the AI produced                                                                                   |
| **Verification**   | How the output was checked (against a named principle, a failure scenario, or an authoritative source) |
| **Human decision** | Accepted, accepted with edits, or rejected — and why                                                  |
| **Notes**          | Follow-ups, links to issues or ADRs                                                                    |

### Verification rule for this project

Every AI-generated architecture artifact must be verified against:

1. A named principle in the **NIST AI RMF** (govern, map, measure, manage).
2. Safety requirements — **human approval, least agency, auditability**.
3. At least one **failure scenario** the artifact is meant to prevent.

The verification result is recorded in the log. An artifact with no verification entry is treated as unverified and cannot be merged.

## 9. How decisions are made

- **Architecture decisions** are recorded as ADRs under `docs/adr/`. Each follows the standard template and ends with a review date and review triggers.
- **Changes to an accepted ADR** are made by superseding it with a new ADR, not by editing the old one. The old ADR's status changes to `Superseded by ADR-XXX`.
- **Policy decisions** (allowlists, thresholds, retention) are versioned configuration, not code. They are reviewed on a fixed cadence and audited.
- **Prompt and model changes** follow the release-gate process in the timeline: golden dataset, eval gate, two-version comparison, and staged rollout with rollback.

### Contribution flow

1. Open an issue describing the problem or decision.
2. If the change is architectural, draft an ADR in `Proposed` status.
3. Update the relevant doc under `docs/`.
4. Add or update an entry in `ai/AI_DECISION_LOG.md` if AI assisted.
5. Open a PR; CI runs build, ArchUnit, and eval gates.
6. On merge, update the ADR status to `Accepted` and record the date in its status history.

## 10. Roadmap

| Week | Focus                                                  | Key deliverable                                                                                                                                                     |
| ---- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | **Architecture and technical strategy**          | **This architecture pack**                                                                                                                                    |
| 2    | Scalable knowledge ingestion and RAG data pipelines    | Versioned ingestion service with metadata, lineage, retryable jobs                                                                                                  |
| 3    | Retrieval quality engineering and modular RAG          | Modular RAG with two retrieval strategies, citation-backed answers, score report                                                                                    |
| 4    | Agentic workflows, tool boundaries, human approval     | Audited agent workflow with read/write classification and rejection tests                                                                                           |
| 5    | Durable AI workflow orchestration                      | Async service with idempotency keys, retries, status endpoint, failure recovery                                                                                     |
| 6    | AI evaluation engineering and regression gates         | Eval harness with golden dataset, CI threshold gate, two-version comparison                                                                                         |
| 6    | AI security, prompt injection defense, data protection | Threat model, injection tests, PII rules, rejection tests                                                                                                           |
| 7    | AI observability, tracing, cost, quality monitoring    | Instrumented service with dashboard, alerts, troubleshooting runbook                                                                                                |
| 8    | Performance, cost optimization, scale testing          | Baseline vs. improved load tests, caching/fallback, scaling recommendations                                                                                         |
| 10   | Deployment, platform engineering, release strategy     | Deployable service with CI/CD, secrets strategy, rollout and rollback planEach week's deliverable is tagged in Git (week-1, week-2, …) and linked from its README. |

## 11. Glossary

| Term                               | Meaning                                                                                                                           |
| ---------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| **ADR**                      | Architecture Decision Record — a dated, reviewable record of a significant technical decision.                                   |
| **Agent**                    | A bounded AI component that plans and calls tools to investigate a case. Never holds write authority.                             |
| **Approval token**           | A signed, single-use, short-TTL token issued by an analyst that authorizes one specific write action.                             |
| **Bounded context**          | A domain boundary with its own data, language, and API. Enforced by package structure and ArchUnit.                               |
| **Classification ceiling**   | The highest data class a given caller identity may retrieve. Enforced at the model gateway and retrieval service.                 |
| **Corpus version**           | A monotonic identifier for a consistent snapshot of the knowledge base after an ingestion run.                                    |
| **C3**                       | Restricted data — PII, KYC documents, SAR drafts. Never embedded, never sent to external providers.                              |
| **Groundedness**             | The degree to which an AI answer is supported by cited sources. Measured in evals; below threshold marks an answer`ungrounded`. |
| **Human-in-the-loop (HITL)** | A control requiring human approval before a consequential action executes.                                                        |
| **Model gateway**            | The single ingress for all model and embedding calls; owns routing, redaction, prompts, budget, fallback, telemetry.              |
| **Prompt injection**         | An attack that embeds instructions in data the model reads, attempting to change its behavior.                                    |
| **Read vs. write tool**      | A classification of a tool's effect. Read tools may execute autonomously; write tools require an approval token.                  |
| **SAR / STR**                | Suspicious Activity Report / Suspicious Transaction Report — regulatory filings triggered by certain investigations.             |
| **SLO**                      | Service-Level Objective — a target for a measurable service indicator, with an error budget.                                     |
| **Version envelope**         | The set of model, prompt, retrieval, corpus, and config versions attached to every AI decision record.                            |

kyc dodument reader

meta data enricher transformer

structure aware chunker

Deduplication transformer

IngestionRun Report

Ingestion processor 

`IngestionWriter `

Skip Listener 


corpus 

application.yml
