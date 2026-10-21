<!--
============================================================================
FILE:        docs/adr/ADR-006-Authenticated-tool-boundaries.md
PURPOSE:     Record the Week 28 identity boundary and proposed tool authorization direction.
OWNER:       Platform / Case Management / Decision & Action
SINCE:       week-28
RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
NOTES:
  - Dependency approval is recorded separately from architecture acceptance.
  - Tool dispatch and local MCP now have test coverage; production identity integration remains future work.
============================================================================
-->

# ADR-006: Authenticated capability boundaries before tool dispatch

**Status:** Proposed — implementation foundation available for human review
**Date:** 2026-10-20
**Deciders:** Project owner (review pending)
**Technical Area:** Security / Backend / Agent tooling

## 1. Context

Week 28 adds tools that may disclose case evidence or propose consequential actions.
Tool arguments are model-controlled and cannot establish caller identity, permissions,
case assignment, or step-up authentication. Existing Week 27 routes retrieve from a
global corpus and have no per-case grants.

The original scaffold deferred Spring Security to Week 31. The Week 28 plan brings
minimal trusted authentication forward so tool permissions can be tested first. The
user explicitly approved the two Spring Security dependencies on 20 October 2026.
That approval does not imply acceptance of every future architecture choice here.

## 2. Decision proposed

Resolve identity through `platform.api.CurrentCaller`, implemented by a trusted
transport adapter. Public requests accept case/tool inputs, never authority claims.
Case services require both a capability and a server-owned assignment. Evidence
services must additionally enforce per-caller classification and case source scope.

Use framework Basic authentication for the loopback learning setup, explicitly
configured BCrypt accounts, no default login, and CSRF protection. Persist no
security context in the CSRF session; authenticate every request. Production
identity-provider integration and verified step-up remain separate work.

Restrict existing globally scoped knowledge endpoints to an explicit operational
curator role until case-scoped retrieval is implemented. Case roles do not inherit
this global access. Permit health probes, match known routes explicitly, and deny
unknown routes.

Future REST and MCP tool adapters should call the same authorized application
services. The tool registry will distinguish read and proposal capabilities; it will
not expose approval or execution to the model. Execution must still satisfy ADR-002's
signed approval, verified step-up, idempotency, and transactional audit requirements.

## 3. Options considered

| Option | Benefit | Cost or failure mode |
| --- | --- | --- |
| Trust actor/role fields in tool arguments | Easy demo plumbing | Model or client can manufacture authority; rejected. |
| Give case roles legacy global retrieval access | Preserves anonymous-era examples with fewer edits | Bypasses case grants; rejected. |
| Leave all endpoints inaccessible until production SSO | Avoids interim credentials | Blocks learning and incremental permission tests. |
| Framework local identity plus explicit operational scope | Testable, incremental, no custom password protocol | Requires secure local configuration, revised HTTP examples, and later SSO/MCP adapters. |

## 4. Consequences and validation

The implementation now includes persisted case grants, source-scoped SQL retrieval,
strict read/proposal tools, a bounded worker, native Ollama tool calling, signed approval
bindings, transactional local simulator execution and audit, and an SDK-backed stdio MCP
adapter. Real PostgreSQL tests cover replay, rollback, evidence changes, and case scope.
The MCP smoke test initializes a real application process with the SDK client.
Database and HTTP integration tests keep security filters enabled.

Production MFA is not implemented: runtime Basic authentication cannot approve actions.
Tests explicitly simulate a verified step-up assertion to exercise token and simulator
behavior. This record does not claim production authorization, remote MCP OAuth, durable
crash recovery, or external account execution is complete. Review this direction before
expanding the registry and write gate. See the implementation walkthrough for verified scope.

## 5. Implementation refinements for review

The schema uses additive V8 case grants, V9 audit history, V10 recommendations/simulator,
and V11 investigation runs rather than modifying the old applied placeholder files.
The worker persists agent state; effective human approval state is resolved from the
recommendation aggregate to avoid duplicate independently editable approval state.
Three reads include the proposal's scoped retrieval. Limits are fixed explicit values
in the first implementation. The local MCP client's overall session is not governed by
the built-in runner's aggregate budget, though every invocation retains authorization.
