<!--
============================================================================
FILE:        docs/evidence/week31/scenario-checklist.md
PURPOSE:     Distinguish actual identity and protocol abuse evidence.
OWNER:       Fraud Investigation Engineering
SINCE:       week-31
RELATED:     context.md, curriculum week 31
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Security evidence checklist

| Boundary                                      | Evidence category                        | Check                                                                                          |
| --------------------------------------------- | ---------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Password, OTP and PKCE                        | Real Keycloak 26.8.1                     | KeycloakStepUpIntegrationTest: password-only denial, wrong OTP rejection, valid simulator path |
| Issuer/audience/signature misuse              | Actual API requests                      | Alternate-issuer/audience Keycloak tokens and modified token rejected                          |
| Link revocation                               | Actual API request                       | Disabled verified subject link returns 401                                                     |
| Stale/future/insufficient authentication      | Claim-boundary unit fixtures             | StepUpClaimsTest not represented as an OTP challenge                                           |
| Source trust and poisoning                    | Real PostgreSQL/HTTP, fixture embeddings | Wrong hash, non-curator trust, untrusted index and revoked read rejected                       |
| Provider disclosure/injection                 | Captured loopback HTTP protocol fixture  | Identifiers redacted; restricted/direct/indirect override strings not dispatched               |
| Cross-case, tools and output grounding        | Existing backend regression scenarios    | RuntimeIntegrationTest and RetrievalIntegrationTest                                            |
| Production settings                           | Startup guards and profile unit test     | Basic forbidden in staging/prod, HTTPS and source trust required                               |
| Full regression / clean weekly reconstruction | Passed: 102 tests, three skips           | See verification.json                                                                          |
| Human review                                  | reviewed                                 | No automatic commit, tag or deployment                                                         |

The JDK test browser implements the browser exception for Secure cookies on the exact loopback Keycloak origin. It uses the actual password/OTP server and token exchange. It does not demonstrate production TLS or a browser UI automation run. No native-model jailbreak success-rate benchmark is claimed.
