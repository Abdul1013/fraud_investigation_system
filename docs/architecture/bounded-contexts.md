
| # | Context                       | Owns                                                       | Does NOT own                   |
| - | ----------------------------- | ---------------------------------------------------------- | ------------------------------ |
| 1 | Signal Ingestion              | Raw event intake, normalization, entity resolution, dedup  | Risk decisions                 |
| 2 | Detection & Risk Scoring      | Rules, ML scores, graph risk, triage severity              | Case lifecycle, actions        |
| 3 | Case Management               | Case state machine, SLA, assignment, analyst queue         | Agent reasoning, model calls   |
| 4 | Investigation Orchestration   | Workflow state, retries, agent planning, tool invocation   | Tool implementations, policies |
| 5 | Knowledge & Evidence          | Corpus versions, retrieval, citations, evidence lineage    | Decisions                      |
| 6 | Decision & Action             | Recommendation evaluation, approval gates, write execution | Model selection, retrieval     |
| 7 | Audit, Reporting & Compliance | Immutable audit log, SAR/STR export, retention enforcement | Business logic                 |
| 8 | Platform                      | IAM, policy-as-code, observability, evals, cost metering   | Domain rules                   |

**Boundary rule:** probabilistic components (agents, models, retrieval) live only in contexts 2, 4, 5. All write authority lives in context 6 behind a deterministic gate. Nothing probabilistic can directly mutate money or account state.
