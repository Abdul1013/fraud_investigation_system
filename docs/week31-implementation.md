<!--
============================================================================
FILE:        docs/week31-implementation.md
PURPOSE:     Explain the security boundary, Spring wiring and defended implementation choices.
OWNER:       Fraud Investigation Engineering
SINCE:       week-31
RELATED:     context.md, curriculum week 31
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Week 31 implementation walkthrough

Status: actual OTP and clean reconstructed-source regression passed (102 passed, three skipped). 

Start with `OidcSecurity`. Spring Security's resource-server filter extracts a bearer token and verifies its RS256 signature using only the configured issuer's JWKS. Validators additionally require the configured issuer and API audience, expiration, subject and Bearer token type. This uses maintained library validation rather than a custom JWT parser.

`OidcIdentityLinks` then translates the verified issuer/subject pair to a provisioned application subject and role. Keycloak display usernames and role claims are not application permissions. A disabled identity fails on the next request. Existing case grants still determine which case and sources the application subject may access. Worker identity reconstruction reads the same enabled link and remains limited to investigator READ/PROPOSAL permissions, without approval authority.

`OidcStepUpVerifier` requires an authenticated JwtAuthenticationToken for that linked subject, ACR 2, a recent authentication time and an unexpired access token. The pinned realm config maps level 2 to password plus mandatory OTP, with level reuse disabled. The learning client requests fresh login with max_age=0. Authentication claims must come from the trusted flow; a body boolean cannot satisfy step-up. Real tests must exercise that flow because claim-only unit fixtures cannot prove OTP happened.

The tiny `/auth/index.html` client demonstrates authorization code with S256 PKCE. It stores only the short-lived verifier/state in sessionStorage and keeps access tokens in memory. The callback removes the code from browser history, exchanges it with the public client and asks the protected API for identity. It does not render tokens, decode ID-token claims for authority or implement a full approval UI. The application API uses bearer headers without cookie authentication, so its CSRF policy differs from the explicit Basic learning profile, which retains CSRF protection.

`SecurityConfig` rejects Basic mode unless the local-learning/test profile is active and rejects that mode if staging/prod is also active. `UnavailableStepUpVerifier` remains the fail-closed Basic adapter. An explicit test-only mock in older Week 28 recovery tests is still labelled as simulation; staging cannot use that to claim real MFA.

`SourceTrustPolicy` checks the exact source ID, revision and checksum against a curator-owned trust registry. Both indexing and scoped retrieval check provenance before model work. Curators use `/v1/knowledge/source-trust`; ingestion does not automatically trust imported files. A mixed corpus with an untrusted eligible source fails closed. This trades availability for a simple reviewable boundary; a future SQL-level trust filter could preserve safe partial availability. Revocation is checked afresh. Trusted provenance never upgrades source prose into system instructions.

`RedactionFilter` is the shared final guard on all local Ollama POST payloads. Known restricted markers, credential material and instruction-override patterns fail before HTTP dispatch. Selected email and long numeric identifier patterns are redacted. This is deliberately limited pattern matching, not comprehensive DLP: source classification and source admission remain essential. Redaction can make a quote differ from its original source; the exact-quote answer check then abstains rather than accepting fabricated support.

Provider response shape checks, strict tool schemas, case-scope SQL, model/tool budgets and signed human decisions remain complementary controls. No regex can prove arbitrary prompt injection impossible. The tests and threat model report which attacks were exercised and retain this limitation.

Local setup: provision Keycloak administrator credentials outside Git, start the identity Compose profile, enroll password/OTP for your analyst, and explicitly insert the verified issuer/subject mapping into `platform.identity_links` through an operator database session. Do not use the display username as the external subject. Provision case grants separately. The imported realm contains no users, passwords, OTP seeds or client secrets. Its start-dev/H2 service is for loopback learning; the Week 34 production service must use dedicated PostgreSQL ownership and TLS.

The real Keycloak test also rejects correctly signed tokens issued for an alternate issuer host or audience. Its small test browser implements Secure-cookie localhost behavior explicitly; that is a local protocol test, not evidence of production TLS. The OTP credential encoding follows Keycloak 26.8.1's BASE32 import contract.
