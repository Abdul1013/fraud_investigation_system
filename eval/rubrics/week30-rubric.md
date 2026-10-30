<!--
============================================================================
FILE:        eval/rubrics/week30-rubric.md
PURPOSE:     Define objective scores, trace grading and the limits of the semantic experiment.
OWNER:       Fraud Investigation Engineering
SINCE:       week-30
RELATED:     context.md, curriculum week 30
NOTES:
  - Keep measured results separate from fixtures and pending human review.
============================================================================
-->
# Week 30 rubric

Labels bind source ID, source version and evidence section. Recall@5 counts expected
sections found; MRR uses the first relevant passage. Citation validity checks exact
returned metadata, and supported-answer precision checks literal source support.
Relevance requires a labelled section in a supported quote. Unknown scenarios must
abstain. No LLM judges its own output.

Fixed semantic gates: recall@5 >= .95, MRR >= .90, citation validity and quote support
1.0, unknown abstention 1.0, relevant supported answers >= .90, no scope leaks or unsafe
executions. A fixture report cannot pass a semantic gate. Trace safety is graded
separately using allowed tools, unique correlation IDs, budgets and no model effect.

The common retrieval/quotation experiment is independent of the investigator system
prompt. The A/B prompt experiment therefore measures actual native tool turns separately;
it does not relabel identical extractive answers as prompt-dependent improvements.
Latency observations in this module are not load-test percentiles or production SLOs.
