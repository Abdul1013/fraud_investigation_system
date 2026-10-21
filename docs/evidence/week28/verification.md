<!--
============================================================================
FILE:        docs/evidence/week28/verification.md
PURPOSE:     Record reproducible Week 28 behavior checks and distinguish test doubles from live evidence.
OWNER:       Investigation Orchestration / Decision & Action / Platform
SINCE:       week-28
RELATED:     docs/week28-implementation.md, Week28RuntimeIntegrationTest
NOTES:
  - Only isolated fixtures simulate verified MFA; the runtime step-up adapter denies Basic authentication.
  - All account effects are local synthetic database changes.
============================================================================
-->

# Week 28 verification

Verification date: 20 October 2026. **Full verification passed: 87 discovered tests,
86 passed, zero failures, zero errors, one optional live-model test skipped.**
The optional live-model test was then run separately and also passed (1/1). Tests use Temurin 25 with the Java 21 target, Spring Boot 3.5.7,
Spring AI 1.0.9, Spring Security 6.5.6, MCP SDK 0.18.3, and isolated
PostgreSQL 16/pgvector Testcontainers. No existing user database is reset.

## Unsafe-action and boundary checks

| Check | Evidence | Expected behavior |
| --- | --- | --- |
| No credentials, invalid password, spoofed identity headers | SecurityBoundaryTest | Identity endpoints reject unauthenticated access; arbitrary headers cannot replace the caller. |
| Global retrieval aliases and ingestion/index permissions | SecurityBoundaryTest; existing HTTP integration tests | Only explicitly scoped curator accounts reach the legacy routes. |
| CSRF token/session matching and per-request authentication | SecurityBoundaryTest; AuthenticatedHttp | Missing/mismatched CSRF is rejected; a CSRF session does not authenticate the next request. |
| Unrelated case, removed grant, insufficient summary clearance | RetrievalIntegrationTest | Same denial body; protected summary never returned. |
| Both retrieval strategies scope before ranking/completeness | RetrievalIntegrationTest | Allowed source set and minimum caller/server clearance apply in SQL. |
| Empty source set or unrelated case | RetrievalIntegrationTest | No query embedding invocation. |
| Direct service calls | RetrievalIntegrationTest | Authentication and request validation cannot be bypassed by avoiding HTTP. |
| Unknown tool, extra identity fields, wrong argument types | Week28RuntimeIntegrationTest | Correlated bounded error; no simulated account effect. |
| Model-only proposal and duplicate proposal | Week28RuntimeIntegrationTest | Persisted pending record, no freeze, no self approval. |
| Forged quotation or removed evidence scope | Week28RuntimeIntegrationTest | Proposal or execution is denied. |
| Public source evidence with C2 recommendation rationale | Week28RuntimeIntegrationTest | C0 grant cannot read or approve C2 recommendation metadata. |
| Missing, tampered, wrong-binding, expired, or revoked approval | Week28RuntimeIntegrationTest; ApprovalTokenServiceTest | Execution denied; valid signature alone is insufficient. |
| Wrong audience/purpose, absent audience/identifier, real JWT expiry | ApprovalTokenServiceTest | Maintained verifier and application binding checks reject the token. |
| Authorized paged audit history and low-clearance denial | Week28RuntimeIntegrationTest | Auditor reads stored action events with a sequence cursor; no model or simulator work repeats. |
| Concurrent token replay | Week28RuntimeIntegrationTest | One successful logical effect, one stored consumption state. |
| Matching retry and conflicting reused key | Week28RuntimeIntegrationTest | Original result returned or conflict; no second effect. |
| Audit insert failure during execution | Week28RuntimeIntegrationTest | Simulator, consumption, and idempotency write all roll back; retry succeeds. |
| Expired approval renewal | Week28RuntimeIntegrationTest | Fresh simulated verified step-up issues a new identifier; old token is rejected. |
| Missing verified step-up | Week28RuntimeIntegrationTest; SecurityBoundaryTest | Approval denied and rejection survives rollback; actual runtime Basic is not MFA. |
| Audit UPDATE/DELETE/TRUNCATE protection | PostgreSQL triggers; mutation test | Ordinary mutations are rejected; privileged database-owner resistance is not claimed. |
| Multi-step model loop and launch replay | Week28RuntimeIntegrationTest | Persisted pause; duplicate launch/status/history never repeat model work. |
| Repeated calls, four reads, parallel requests, timeout, safe abstention | Week28RuntimeIntegrationTest | Deterministic failure or insufficient-evidence state; no account effect. |
| Cross-context imports and approval/executor calls | Week28BoundaryRulesTest | Orchestration uses public APIs and cannot call approval/execution. |
| Native Ollama request/result structure | ToolCallingAdapterTest | Native function schemas and assistant/tool messages; malformed batches rejected. |
| Actual stdio MCP protocol | Week28RuntimeIntegrationTest | SDK initializes a child application, lists exactly three tools, reads authorized data, rejects overrides/executor calls, records tool observations. |

## Reproduce

```bash
# Use an installed JDK; this machine has Temurin 25, compiling the Java 21 target.
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
mvn clean verify
# Optional installed-model demonstration, separate from deterministic tests:
mvn -Dtest=Week28RuntimeIntegrationTest#realLocalModelRequestsAReadToolAndReceivesItsResult -Dweek28.live=true -Dsurefire.reportNameSuffix=live test
```

The separate [native tool-call record](native-tool-roundtrip.json) captures real
`qwen2.5:1.5b` inference: a requested case-read tool, the authorized backend result,
and a follow-up answer. Model version: `ollama:qwen2.5:1.5b@65ec06548149b04c096a120e4a6da9d4017ea809c91734ea5631e89f96ddc57b`.
The model run uses synthetic data and makes zero account changes. It establishes
one native function-call round trip, not general fraud-investigation quality.

The normal suite intentionally skips the live-model test. The SDK smoke test is
part of normal verification and launches the actual application, not a mock MCP
HTTP endpoint. Model and MFA test doubles in other scenarios are explicitly labelled.

## Remaining limits

The local simulator is not an external financial account service. Runtime analyst
approval remains denied until a verified identity-provider step-up adapter is
installed. Local stdio identity provisioning is not remote MCP OAuth. Process crash
recovery is Week 29 work. Normal database triggers do not defeat privileged database
owners. Production C3 redaction, encryption, wider adversarial evaluation, and client
model/data handling remain later work.
