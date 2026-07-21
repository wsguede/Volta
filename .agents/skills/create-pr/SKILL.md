---
name: create-pr
description: >
  Open a pull request for Volta following the standardized title and body format that mirrors
  .github/PULL_REQUEST_TEMPLATE.md, link the originating issue with "Closes #N", and move it to
  state:review. Use this whenever the user asks to open a PR, create a pull request, submit their
  changes for review, or says things like "PR this", "open a pull request for this branch", or
  "I'm done, let's ship it" — requires build-and-verify to have passed first.
---

# create-pr

Create a pull request with standardized formatting.

## When to invoke

- When a feature or fix is complete and verified via `build-and-verify`

## Prerequisites

- On a feature branch (not `main`)
- `build-and-verify` has passed
- All changes are committed

## PR format

**Title:** Short, imperative, under 70 characters. Follows conventional commit style.

```
feat(capture): add blur detection to frame analysis
fix(stitching): correct projection at poles
```

**Body:** Follow `.github/PULL_REQUEST_TEMPLATE.md` exactly — this is what GitHub pre-fills, so drifting from it produces a PR that doesn't match what reviewers expect:

```markdown
## Summary

- <1-3 bullet points describing what changed and why>

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor
- [ ] Docs

## Checklist

- [ ] Unit tests added or updated
- [ ] `./gradlew ktlintCheck` passes
- [ ] `./gradlew detekt` passes
- [ ] ADR written if an architecture decision was made

## Related issues

Closes #<issue-number>
```

Only check a box once it's actually true — if an item doesn't apply (e.g. no ADR was needed), leave it unchecked rather than checking it to look complete.

## Steps

1. **Verify branch** — Confirm you're on a feature branch, not `main`.
2. **Verify build** — Run `build-and-verify` if not already done this session.
3. **Draft PR** — Generate title and body following `.github/PULL_REQUEST_TEMPLATE.md` above.
4. **Link issues** — Use `Closes #N` to auto-close the issue on merge.
5. **Create PR** — Push branch and open PR via `gh pr create`.
6. **Update labels** — Move the linked issue to `state:review`, and apply the same `state:review`
   label to the PR itself (`gh pr edit <N> --add-label "state:review"`) — the issue stays the source
   of truth `whats-next` reads from, but the label on the PR makes its status visible at a glance in
   GitHub's own PR list without cross-referencing the issue.

## Exit criteria

- PR is open with properly formatted title and body
- Linked issue and the PR itself are both labeled `state:review`
- CI is running (or has passed)
