<!--
============================================================================
FILE:        docs/evidence/week31/verification.md
PURPOSE:     Report the actual reconstructed security milestone result.
OWNER:       Fraud Investigation Engineering
SINCE:       week-31
RELATED:     context.md, curriculum week 31
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Week 31 verification

Technical checks passed; human review pending.

The exact sequential Week 31 patch reconstructed cleanly and its `mvn -q clean verify` run passed: 105 discovered, 102 passed, three skipped, zero failures/errors. The three optional native-model methods remain skipped in this regression; this week's identity demonstration uses an actual local Keycloak server, not a mocked verifier.

`otp-authentication.json` records the real password-only denial, OTP challenge and wrong-code rejection, successful approval, exactly one simulator freeze, alternate issuer/audience rejection and disabled-link rejection. Credentials and OTP seed material were generated only in ephemeral test memory/container storage and are absent from evidence.

SourceTrustIntegrationTest used real PostgreSQL/HTTP with labelled fixture embeddings and verified that inference did not run for untrusted/revoked sources. OutboundPrivacyTest captured actual loopback HTTP against a protocol fixture and checked redaction/blocked dispatch. StepUpClaimsTest used claim-boundary fixtures for stale/future/insufficient authentication; these are separate from real OTP evidence.

A prior full run correctly failed the legacy MCP fixture when it lacked reviewed source trust. The fixture now explicitly trusts its synthetic source; the real stdio process continues enforcing the policy. No security bypass or deployment exception was added.

No commit, tag, push or cloud rollout was performed. Production TLS, native-model jailbreak rates and a long-window security guarantee are not claimed.
