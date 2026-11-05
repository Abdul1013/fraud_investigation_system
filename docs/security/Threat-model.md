<!--
============================================================================
FILE:        docs/security/week31-threat-model.md
PURPOSE:     Record exercised abuse boundaries and residual risks.
OWNER:       Fraud Investigation Engineering
SINCE:       week-31
RELATED:     context.md, curriculum week 31
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Week 31 threat model

| Threat | Boundary and decision | Residual risk |
| --- | --- | --- |
| Direct prompt override | Outbound guard rejects known overrides; tools cannot execute decisions | Novel phrasing may reach model; backend authority must still hold |
| Indirect instruction in a source | Curator-approved exact revision, source text remains data, same outbound checks | Trusted curators can admit misleading facts; provenance is not truth |
| Fabricated citations | Exact lineage and quoted-substring checks in AnswerService | Supported text can still be irrelevant |
| Cross-case disclosure | Identity links, case grants and SQL source/classification filtering | Incorrect operator grants remain an operational risk |
| Token substitution | Trusted JWKS, RS256, issuer/audience/expiry and explicit subject link | Stolen fresh access tokens remain usable until expiry/link revocation |
| False MFA | Signed ACR 2 from mandatory password/OTP flow and fresh auth_time | Realm administrators are trusted to maintain the flow |
| Excessive agency | READ/PROPOSAL tools; human-only signed single-use approvals | A human can approve a poor but valid recommendation |
| Sensitive provider disclosure | C3 excluded structurally; source trust and outbound guard | Heuristic patterns do not identify every person or secret |
| Consumption denial | Active deadlines, bounded turns/tool budgets | Per-subject admission/rate limiting is Week 33 work |

Passwords, OTP seeds, bearer/approval tokens and raw restricted content must never appear in evidence or telemetry. Test identities and seeds are ephemeral. Authentication evidence records statuses and counts only. Pattern matches are additional controls, not a claim of complete injection or PII detection.
