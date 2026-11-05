<!--
============================================================================
FILE:        docs/week30-implementation.md
PURPOSE:     Explain prompt pinning and evaluation design for a first Spring project.
OWNER:       Fraud Investigation Engineering
SINCE:       week-30
RELATED:     context.md, curriculum week 30
NOTES:
  - Measurements and human review status are reported separately.
============================================================================
-->

# Week 30 implementation walkthrough

Status: technical checks passed, including real local semantic evaluation and clean reconstructed-source regression. Human reviewed.

Read `PromptCatalog` and `AiVersionEnvelope` first. These public types let orchestration and decision depend on a stable platform contract. `PromptRegistry` is a Spring component: constructor injection supplies its JSON parser and operator-selected active version. An allowlisted manifest binds each version to its file digest. Changing a published prompt's bytes causes startup or lookup to fail; add a new version instead.

Next read `DurableInvestigationStore`. A new launch saves the prompt reference and text alongside the durable run. Migration V13 prevents later mutation once bound. Activities load this binding rather than whatever prompt is currently active. This preserves replay meaning when an operator selects another active version. Older Week 29 runs retain their original prompt through an explicit legacy path.

`InvestigatorTools.callVersioned` passes a server-owned envelope to decision. The model's argument schema does not expose these version fields. Decision stores the prompt reference in its existing approval binding; operation-key input mismatches remain rejected. This is preferable to trusting the model to report its own prompt version.

`KnowledgeApi.answerCaseEvidence` combines case-authorized retrieval with the existing AnswerService grounding checks. The answer service accepts only exact quotes from returned passages and rejects fabricated citation IDs. Scope checks remain in the owning case/knowledge contexts. Evaluation uses this same public boundary rather than duplicating SQL.

`EvalService` is a Spring service. Constructor injection gives it public knowledge, case and audit APIs. Each question yields recall, reciprocal rank, abstention, quote support and latency. Scope leaks are counted against the authorized source/classification scope; consequential effects are counted from actual audit events before and after the run. Citation support proves that the text exists in a retrieved source; it does not prove that it answers the question. Relevance is therefore a separate labelled measurement.

`TraceGrader` uses deterministic tool names, correlation IDs and budgets. These checks are reproducible and are not an LLM judge. Native A/B experiments record actual tool turns, final text and task-compliance failures under both prompt versions. Their three goals are read-only, so a proposal is recorded as a task failure even though the backend proposal boundary itself remains non-consequential.

The retrieval answer path uses the deterministic extractive selector. Its scores are independent of the investigator system prompt. The native A/B report is a separate experiment; do not attribute common retrieval scores to a prompt improvement. Subjective assessment of free-form final responses remains human review pending.

`EvalGate` runs both as a Java API and an executable process. It rejects fixture embeddings and invalid metric numbers. A subprocess test injects a known low-recall regression and verifies exit code 1. The GitHub semantic job measures the local embedding model, checks its digest and preserves failing reports. It does not silently replace native embeddings with fixtures.

Limitations: the corpus is synthetic and small; extractive selection can miss paraphrases or select irrelevant supported text. Repeated experiment results can vary with model/runtime behavior. Branch protection and human review are not automatically configured. The security scenario labels must be traced to executed authorization/version/tool tests; their presence in JSON alone is not evidence.
