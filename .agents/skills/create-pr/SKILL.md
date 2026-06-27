# create-pr

Create a pull request with standardized formatting.

## When to invoke

- When a feature or fix is complete and verified via `build-and-verify`
- Before invoking, ensure all tests pass and the build succeeds

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

**Body:**

```markdown
## Summary
- <1-3 bullet points describing what changed and why>

## Linked issues
Closes #<issue-number>

## ADRs
- <link to any new ADRs, or "None">

## Test plan
- [ ] <specific test scenarios covered>
- [ ] <edge cases tested>
- [ ] build-and-verify passes
```

## Steps

1. **Verify branch** — Confirm you're on a feature branch, not `main`.
2. **Verify build** — Run `build-and-verify` if not already done this session.
3. **Draft PR** — Generate title and body following the format above.
4. **Link issues** — Use `Closes #N` to auto-close the issue on merge.
5. **Create PR** — Push branch and open PR via `gh pr create`.
6. **Update issue** — Move the linked issue to `state:review`.

## Exit criteria

- PR is open with properly formatted title and body
- Linked issue moved to `state:review`
- CI is running (or has passed)
