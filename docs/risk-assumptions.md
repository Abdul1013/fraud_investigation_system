
# 7. Risk Assumptions

The architecture is based on the following assumptions about the operating environment, threat model, users, regulatory requirements, and business constraints.

| #               | Risk Assumption                                                                                                                                            | Architectural Implication                                                                      | Required Control                                                                                         |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| **RA-01** | Fraud alerts are high-volume and bursty; ingestion may experience peaks of up to **10× normal traffic**.                                           | Ingestion must decouple intake from downstream processing and remain available during bursts.  | Queue/event buffering, backpressure, autoscaling capacity, durable ingestion, replay capability          |
| **RA-02** | Model providers may experience outages, latency degradation, API changes, or price changes.                                                                | The platform must not depend on a single provider or assume continuous provider availability.  | Model gateway, provider adapters, health checks, fallback routing, circuit breakers, cost controls       |
| **RA-03** | Untrusted business data may contain indirect prompt-injection content, including transaction memos, merchant names, KYC documents, and dispute narratives. | Retrieved or ingested content must never be treated as trusted instructions to an AI system.   | Content isolation, prompt/data boundary controls, model gateway, tool authorization, output validation   |
| **RA-04** | False negatives carry significant fraud cost, while false positives create regulatory, operational, and customer-harm costs.                               | Risk thresholds are policy decisions and must not be implicitly determined by models.          | Deterministic policy layer, configurable thresholds, versioned policies, human override                  |
| **RA-05** | Analysts require evidence, citations, and an explicit override mechanism to rely on system recommendations.                                                | Explainability and analyst control are functional requirements, not optional UX features.      | Evidence lineage, citations, recommendation provenance, override workflow                                |
| **RA-06** | Regulatory retention and explainability requirements apply from the beginning of the system lifecycle.                                                     | Compliance controls cannot depend on future retrofitting.                                      | Immutable/tamper-evident audit trail, retention controls, provenance, decision records                   |
| **RA-07** | Policies, prior cases, and other knowledge sources will change over time.                                                                                  | Retrieval freshness directly affects decision correctness.                                     | Corpus versioning, document lineage, freshness metadata, re-indexing, stale-content detection            |
| **RA-08** | The cost of processing an individual case must remain bounded as case volume grows.                                                                        | AI and retrieval operations must be observable and subject to resource/cost controls.          | Per-case budgets, token/call metering, model routing, caching, rate limits, cost telemetry               |
| **RA-09** | Audit records may be relied upon for regulatory and investigative defensibility.                                                                           | Audit data must be resistant to unauthorized modification or deletion.                         | Append-only storage, tamper evidence, restricted access, integrity verification                          |
| **RA-10** | Human approval is the primary control for high-impact autonomous actions.                                                                                  | Human approval must be enforced by the architecture rather than represented only as a UI step. | Deterministic approval gate, policy enforcement, separation of recommendation and execution, audit trail |

## 7.1 Risk Categories

### Availability

* **RA-01:** Bursty ingestion
* **RA-02:** Model-provider outages

### Security

* **RA-03:** Indirect prompt injection
* **RA-09:** Audit-log tampering

### Decision Risk

* **RA-04:** False positives and false negatives
* **RA-07:** Knowledge/corpus drift

### Human Factors

* **RA-05:** Analyst trust and explainability
* **RA-10:** Enforceable human approval

### Compliance

* **RA-06:** Retention and explainability requirements

### Economic / Operational

* **RA-08:** Bounded cost per case

## 7.2 Architectural Principles Derived from the Assumptions

These assumptions establish the following system-level principles:

1. **Ingestion is decoupled from analysis.**
2. **No external model provider is a trusted or permanent dependency.**
3. **All externally supplied content is untrusted input.**
4. **Models recommend; deterministic policy controls decide.**
5. **Recommendations must be evidence-backed and traceable.**
6. **Auditability is a first-class architectural requirement.**
7. **Knowledge has provenance, version, and freshness.**
8. **AI execution is subject to explicit cost budgets.**
9. **High-impact writes require an enforceable authorization gate.**
10. **Safety controls cannot depend solely on prompts or agent behaviour.**

## 7.3 Risk Assumption Traceability

Each assumption should trace to the architectural components responsible for
mitigating it.

| Risk  | Primary Context / Component                                          |
| ----- | -------------------------------------------------------------------- |
| RA-01 | Signal Ingestion, Event Infrastructure                               |
| RA-02 | Platform, Model Gateway                                              |
| RA-03 | Signal Ingestion, Knowledge & Evidence, Agent Runtime, Model Gateway |
| RA-04 | Detection & Risk Scoring, Decision & Action                          |
| RA-05 | Knowledge & Evidence, Case Management, Decision & Action             |
| RA-06 | Audit, Reporting & Compliance                                        |
| RA-07 | Knowledge & Evidence, Retrieval                                      |
| RA-08 | Platform, Model Gateway, Agent Runtime                               |
| RA-09 | Audit, Reporting & Compliance                                        |
| RA-10 | Decision & Action, Platform/IAM                                      |

## 7.4 Assumption Review

Risk assumptions must be reviewed when:

* Traffic characteristics materially change.
* A new model provider or AI capability is introduced.
* New data sources are connected.
* The threat model changes.
* Regulatory requirements change.
* The autonomy level of the system changes.
* Cost or performance targets change.

**Important:** An assumption is not itself a control. It describes something the architecture expects to be true or a risk it expects to face; the corresponding **architectural implication and control** explain what the system must do about it.
