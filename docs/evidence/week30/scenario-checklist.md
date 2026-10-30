<!--
FILE: docs/evidence/week30/scenario-checklist.md
PURPOSE: Reproduce and review the Week 30 evaluation milestone.
OWNER: Platform & Operations
SINCE: week-30
RELATED: docs/week30-implementation.md
NOTES: Human review remains pending.
-->

# Verification checklist

| Scenario | Evidence/status |
| --- | --- |
| 30 labelled golden retrieval questions | Actual vector/hybrid JSON reports, 30 rows each |
| Real semantic embeddings | embeddinggemma digest recorded in each report; fixture=false |
| Two prompt versions | Six actual native model trials in prompt-comparison.json |
| Scope leak/consequential effect measurement | Authorized source scope and audit counts in evaluation reports |
| Revoked case grant | EvaluationIntegrationTest fixture method rejects subsequent access |
| Prohibited tools/correlation reuse | EvalGateTest deterministic TraceGrader checks |
| Fabricated quotations | Existing grounding regression tests; full regression passed |
| Version binding/path traversal | Prompt registry tests; durable prompt immutability test |
| Known regression rejected | Child JVM exit 1, known-regression-rejection.json |
| Required CI workflow | Prepared; GitHub run and branch-protection setting pending human push/admin review |
| Full suite and clean reconstructed source | Passed: 97 tests, three skips; see verification.json |
| Human implementation review | Pending review |
