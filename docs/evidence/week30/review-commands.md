<!--
FILE: docs/evidence/week30/review-commands.md
PURPOSE: Reproduce and review the Week 30 evaluation milestone.
OWNER: Platform & Operations
SINCE: week-30
RELATED: docs/week30-implementation.md
NOTES: Human review remains pending.
-->

# Review commands

Reconstruct from the reviewed Week 29 commit in a clean checkout. The private patch lives outside source delivery in `.runtime/delivery/weeks29-34/week-30.patch`; retain that directory when transferring review work.

```bash
git apply --check /absolute/path/to/week-30.patch
git apply /absolute/path/to/week-30.patch
git add --pathspec-from-file=docs/evidence/week30/commit-files.txt
git diff --cached --check
git diff --cached --stat
mvn -q clean verify
mvn -q -Deval.native=true -Dtest=EvaluationIntegrationTest test
# After human review:
git commit -m "feat(eval): pin prompts and gate measured semantic quality"
git tag -l week-30
# Only if the preceding tag lookup is empty:
git tag -a week-30 -m "Week 30: evaluation harness and regression gates"
```

Run the tag command immediately after this week's commit, before applying Week 31. Never overwrite an existing tag. The known-regression test must pass by observing a failing child JVM; its process output contains `passed:false` by design.
