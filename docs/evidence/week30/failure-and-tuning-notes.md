<!--
FILE: docs/evidence/week30/failure-and-tuning-notes.md
PURPOSE: Reproduce and review the Week 30 evaluation milestone.
OWNER: Platform & Operations
SINCE: week-30
RELATED: docs/week30-implementation.md
NOTES: Human review remains pending.
-->

# Observed failures and tuning notes

The first resumed build found invalid compound `var` declarations and an ambiguous AccessDeniedException import in the new tests. Both were corrected before integration measurement. An integration attempt then failed because Docker Desktop was stopped; it was restarted and the check rerun. These setup failures are not semantic scores.

Both actual embedding strategies passed without lowering thresholds or removing the Week 27 paraphrase question. The paraphrase has recall 1 but no relevant supported answer: the extractive selector's lexical matching remains limited. Hybrid returned a supported but irrelevant quotation for that question. This is why quotation support is reported separately from relevance.

The native prompt experiment contains three read-only goals per version. Prompt A had two tool argument rejections; prompt B had none in this run. Inspect the exact calls and final text in `prompt-comparison.json`. Three trials are too few to claim that B is reliably superior. Free-form final response faithfulness is human review pending; trace checks do not prove all prose correct.

No subjective model judge score is presented. The extractive retrieval report is common to the A/B setup and is not a prompt comparison result. Future tuning should add held-out questions and repeat the same pinned workloads before accepting any change. Keep this baseline dataset and thresholds intact.
