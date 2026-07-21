---
name: review-pr
description: >
  Review a pull request or local branch diff by dispatching the android-reviewer and architect
  personas as parallel subagents, then synthesizing their findings into one merged report. Use this
  whenever the user asks to review a PR, review their changes, get a second opinion on code, review
  a diff, or wants both an Android/Compose-focused review and an architecture-focused review at the
  same time. Also use for phrases like "review PR #N", "review my branch", "check this over before
  I open a PR", or "run the reviewers on this."
---

# review-pr

Review code changes in Volta by running the `android-reviewer` and `architect` subagents **in
parallel**, then merging their output into a single report. Running them together (rather than one
after another, or relying on a single generalist pass) surfaces both Android-specific correctness
issues and structural/ADR issues in one shot, and each persona stays focused on what it's actually
good at instead of trying to cover both.

## Step 1: Determine what to review

**If the user gave a PR number** ("review PR #12", "review #12") — that's PR mode. Skip to Step 2
using that number.

**If the user didn't give a PR number** ("review my changes", "review this branch", "review my
PR") — don't assume. Check whether the current branch already has an open PR:

```bash
gh pr view --json number,url,state 2>/dev/null
```

This can return a PR that's already `MERGED` or `CLOSED` — that's not a live review target, so check
the `state` field specifically:

- If `state` is `OPEN`, confirm with the user before proceeding: tell them you found PR #N and ask
  whether to review that PR (findings get posted as a PR comment) or just review the local diff
  without posting anywhere. Don't silently pick one — posting a comment is visible to others, so the
  choice matters.
- If `state` is `MERGED`, `CLOSED`, or the command returns nothing, there's no live PR to post to —
  proceed straight to local-diff mode without asking.

## Step 2: Get the diff

**PR mode:**

```bash
gh pr diff <N>
```

**Local diff mode:**

```bash
git diff main...HEAD
```

If there are also uncommitted changes the user clearly wants included, add `git diff` (working
tree) to the picture too — use judgment based on what the user asked for.

If the diff is empty, say so and stop rather than dispatching subagents for nothing.

## Step 3: Dispatch both reviewers in parallel

This is the core of the skill — **both subagents must be launched in the same message**, not
sequentially. Sequential dispatch defeats the purpose (it's slower and the two reviews should be
independent, not influenced by seeing each other's output first).

Use the `Agent` tool twice, in one tool-call batch:

- `subagent_type: "android-reviewer"` — give it the diff (or PR number + `gh pr diff` command to
  run itself) and ask it to review per its persona instructions.
- `subagent_type: "architect"` — same diff, same instruction.

Each persona already defines its own output format (severity-bucketed lists for android-reviewer;
Architecture issues / ADR gaps / Dependency concerns for architect) — don't override that, just
point them at the diff and let them apply their own review priorities.

## Step 4: Merge into one report

Wait for both subagents to return, then synthesize — don't just concatenate the two raw outputs.
Read both carefully and produce one report:

```
## Critical
- [file:line] Issue — why it matters, suggested fix

## Warnings
- [file:line] Issue — why it matters, suggested fix

## Suggestions
- [file:line] Issue — alternative approach

## Architecture & ADR notes
- Dependency/ADR-specific findings that don't map to a file:line severity bucket
```

While merging:
- **Dedupe** — if both personas flag the same file:line for essentially the same reason, list it
  once. If it's worth noting both independently caught it, say so briefly ("also flagged by
  architect") — that agreement is a signal the issue is real, not noise to hide.
- **Keep the sharper of two descriptions** when the same issue is described differently — don't
  average them into something vaguer.
- Fold architect's Architecture/Dependency findings into Critical or Warnings when they clearly have
  that severity (e.g. a `domain/` class importing `android.*` is Critical, not a footnote); reserve
  the "Architecture & ADR notes" section for things that are genuinely their own category, like a
  missing ADR.
- Omit empty sections. If both personas found nothing, say so in one line rather than emitting
  empty headers.

## Step 5: Deliver the result

**PR mode:** Post the merged report as a PR comment, then also show it in the conversation:

```bash
gh pr comment <N> --body-file <path-to-merged-report>
```

Confirm with the user before posting if there's any ambiguity about whether they wanted it public —
posting a comment is visible to anyone with access to the repo. If they explicitly asked you to
"review PR #N" that's implicit permission to post; if they said "look over my PR" more casually,
a quick check first is safer.

**Local diff mode:** Show the merged report in the conversation only. Do not post anything to
GitHub — there may not even be a PR yet.

## Exit criteria

- Both subagents completed and their findings are reflected in the merged report
- Report is deduplicated, not a raw concatenation
- PR mode: comment posted (or explicitly skipped per user's answer) and report shown in chat
- Local mode: report shown in chat, nothing posted anywhere
