<!--
============================================================================
FILE:        docs/week31-implementation-plan.md
PURPOSE:     Align MCP/tool security and identity resources with verifiable application boundaries.
OWNER:       Fraud Investigation Engineering
SINCE:       week-31
RELATED:     context.md, curriculum week 31
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Week 31 implementation plan

Status: technical checks passed; human review pending.

| Studied concept | Owning module | Required proof |
| --- | --- | --- |
| OIDC and PKCE | Platform security and local public browser client | Real authorization code exchange; strict issuer/audience/RS256/expiry checks |
| Step-up authentication | Keycloak password/OTP flow and StepUpVerifier | Actual OTP challenge, rejected wrong OTP, fresh signed ACR 2 before approval |
| Identity versus authorization | Explicit identity link plus existing case grants | Caller-selected usernames/roles cannot acquire permissions; link/grant revocation denies access |
| Source poisoning and indirect injection | Knowledge source-trust policy | Untrusted checksum/revision rejected before embedding or retrieval |
| Sensitive disclosure | Gateway redaction and classification boundary | Captured outbound request lacks sentinels and selected PII patterns |
| Excessive agency | Existing strict tools, proposal boundary, signed approval | No model-visible approval/execution tool; one authorized simulator effect |
| Consumption and malformed responses | Existing active deadline/tool budgets and parser/grounding checks | Rejected excessive turns, malformed model responses, fabricated citations |

Execute identity changes first, then trusted-source and outbound guards. Keep test-only claim fixtures, protocol fixtures, actual OTP and native-model evidence separate. Run focused tests, the full suite, and clean weekly patch reconstruction before moving to Week 32.

Keycloak is pinned to 26.8.1, the 13 October 2026 maintenance release checked against the official release notes. Boot 3.5.7 manages Spring Security resource-server/JOSE versions. No provider SDK moves outside the platform gateway.

References: [Keycloak step-up flow](https://www.keycloak.org/docs/latest/server_admin/index.html), [Keycloak 26.8.1 release](https://www.keycloak.org/2026/10/keycloak-2681-released), [Spring Security 6.5 JWT validation](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
