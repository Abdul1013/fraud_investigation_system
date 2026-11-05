<!--
============================================================================
FILE:        docs/evidence/week31/review-commands.md
PURPOSE:     Reconstruct and review the exact Week 31 milestone.
OWNER:       Fraud Investigation Engineering
SINCE:       week-31
RELATED:     context.md, curriculum week 31
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Review commands

Apply only after the reviewed Week 30 commit:

```bash
git apply --check /absolute/path/to/week-31.patch
git apply /absolute/path/to/week-31.patch
git add --pathspec-from-file=docs/evidence/week31/commit-files.txt
git diff --cached --check
git diff --cached --stat
mvn -q clean verify
# After human review:
git commit -m "feat(security): verify OIDC step-up and enforce source trust"
git tag -l week-31
# Only if the preceding lookup is empty:
git tag -a week-31 -m "Week 31: AI security and verified step-up authentication"
```

Run the tag command immediately after the corresponding commit. Do not overwrite an existing tag. Keycloak tests generate ephemeral credentials and use the real OTP challenge; no seeds or bearer credentials are included in the evidence.
