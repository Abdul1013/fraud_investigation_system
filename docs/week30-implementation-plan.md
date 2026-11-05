<!--
============================================================================
FILE:        docs/week30-implementation-plan.md
PURPOSE:     Map evaluation engineering resources to executable regression checks.
OWNER:       Fraud Investigation Engineering
SINCE:       week-30
RELATED:     context.md, curriculum week 30
NOTES:
  - Measurements and human review status are reported separately.
============================================================================
-->

# Week 30 implementation plan

Status: technical checks passed; human reviewed.

The evaluation resources map to five concrete modules:

| Resource concept             | Repository module                                                     | Acceptance                                                                                           |
| ---------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| Golden datasets              | `eval/datasets/week30-evaluation.json`                              | At least 30 unique labelled questions, known and unknown answers, paraphrases and source versions    |
| Retrieval and answer metrics | `platform.eval.EvalService`                                         | Question-level recall@5, reciprocal rank, exact citation/quotation support, relevance and abstention |
| Prompt comparison            | `PromptCatalog`, gateway registry and native integration experiment | Two digest-checked prompt versions, identical model, goals and tool definitions                      |
| Trace grading                | `TraceGrader`                                                       | Deterministic rejection of privileged tools, repeated correlations and exhausted budgets             |
| Regression gate              | `EvalGate`, `.github/workflows/eval-gate.yml`                     | Actual semantic embedding run; injected regression exits nonzero                                     |

1. Preserve the tested Week 29 source and patch before modifying shared files.
2. Publish immutable prompt resources and a public version envelope. Bind the prompt at launch and carry its reference into recommendations.
3. Reuse the scoped KnowledgeApi and existing exact-quote validation rather than evaluating an unscoped test-only retrieval path.
4. Expand the dataset while retaining previous failures. Record each question's results, model-space and retrieval version.
5. Run fixture checks separately from native embeddings and prompt experiments. A fixture report must fail the semantic gate.
6. Keep fixed thresholds: recall@5 ≥ .95, MRR ≥ .90, citation validity and supported quotations 100%, unknown abstention 100%, zero scope leaks or consequential effects. Relevant supported answers additionally require ≥ .90.
7. Capture the injected gate failure, native scores, prompt comparison and limitations. Run the full suite and reconstruct the weekly patch in a clean source directory before advancing.

No additional provider is introduced. Reports cannot establish production accuracy from this small synthetic corpus. The CI job must be selected as a required branch-protection check by the repository administrator after review; a workflow file alone cannot configure that policy.
