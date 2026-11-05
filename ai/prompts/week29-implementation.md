<!--
============================================================================
FILE:        ai/prompts/week29-implementation.md
PURPOSE:     Preserve the actual Week 29 AI implementation instruction and review status.
OWNER:       Fraud Investigation Engineering
SINCE:       week-29
RELATED:     context.md, curriculum week 29
NOTES:
  - Keep measured results separate from fixtures and pending human review.
============================================================================
-->
# Week 29 implementation prompt

Implement durable investigation orchestration using Temporal while preserving the Week 28 public APIs, case authorization, tool budgets, and human-only execution boundary. Keep I/O in activities, add transactional outboxes and stable operation keys, and demonstrate restart recovery without duplicate simulated effects. Explain the design and record reproducible evidence.

Verification: five recovery tests and the clean reconstructed-source suite passed (91 passed; one optional native-model test skipped). Human decisions: dependency set approved; implementation review pending.
