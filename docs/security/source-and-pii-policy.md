<!--
============================================================================
FILE:        docs/security/week31-source-and-pii-policy.md
PURPOSE:     State source admission and PII handling rules without claiming comprehensive detection.
OWNER:       Fraud Investigation Engineering
SINCE:       week-31
RELATED:     context.md, curriculum week 31
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Source trust and PII handling

1. Preserve classification as a floor. C3 content is never embedded or sent to a provider.
2. Imported sources begin untrusted. A knowledge curator reviews the actual revision and records its immutable checksum. New revisions need new approval; revocation is immediate on the next request.
3. Case source assignment and source trust are separate requirements. Neither substitutes for the other.
4. Documents and tool results are data. They cannot redefine roles, tools, budgets, approval authority or system instructions.
5. The model gateway rejects known credential/restricted markers and instruction overrides, and redacts selected email/long-number patterns before outbound HTTP. Unrecognized PII remains a risk requiring source review; no comprehensive automated DLP claim is made.
6. Keep raw immutable snapshots in restricted local storage and preserve source/citation hashes. Do not change originals to match a model response. If redaction prevents exact quotation validation, abstain.
7. Evidence stores sanitized metadata and aggregate outcomes. Never commit passwords, OTP seed material, bearer/approval credentials, raw restricted text or full authentication exchanges.
