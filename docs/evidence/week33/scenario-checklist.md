<!--
============================================================================
FILE:        docs/evidence/week33/scenario-checklist.md
PURPOSE:     Separate Week 33 actual measurements from infrastructure checks.
OWNER:       Fraud Investigation Engineering
SINCE:       week-33
RELATED:     context.md, curriculum week 33
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Week 33 scenarios

- Actual k6 fixture/native baselines captured before optimization; exact workload retained.
- PostgreSQL/HTTP fixture checks: subject/case isolation, automatic scope revisions, trust/grant revocation, credential bypass, bounded cache, minute budget rejection and concurrent-call rejection.
- Loopback protocol fixture: embedding batch order/count/dimension/value checks. Existing retrieval integration checks resumable/idempotent indexing.
- Actual HTTP stream: persisted sequence only, restricted event detail absent, grant revocation closes the stream. Signed-claim fixtures separately test expired JWT and disabled identity links.
- Simulated outage: authorized lexical/extractive read with explicit degraded flag; fixture embeddings not invoked; revoked actor denied.
- Deterministic semantic gate rejects degraded reports. These tests do not establish native semantic quality; measured semantic evidence is kept separately.
