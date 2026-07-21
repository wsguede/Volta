---
name: review-pr
description: >
  Review a pull request or local branch diff by dispatching the android-reviewer and architect
  personas as parallel subagents against both the diff and the linked issue's acceptance criteria,
  then synthesizing their findings into one merged report. Use this whenever the user asks to review
  a PR, review their changes, get a second opinion on code, review a diff, or wants both an
  Android/Compose-focused review and an architecture-focused review at the same time. Also use for
  phrases like "review PR #N", "review my branch", "check this over before I open a PR", or "run the
  reviewers on this."
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

## Step 3: Read the linked issue's requirements

A diff alone only tells a reviewer whether the code is well-written, not whether it does what was
asked. Reading the originating issue lets both personas also check the PR against its actual
acceptance criteria — catching things like a half-implemented requirement or a silently dropped edge
case that pure code review would miss.

- **PR mode:** read the PR body (`gh pr view <N> --json body -q .body`) and look for a `Closes #N` /
  `Fixes #N` / `Resolves #N` reference — the convention `create-pr` writes — requiring at least one
  digit after `#`. An unfilled template (`Closes #` with nothing after it, which happens whenever a PR
  is opened without linking an issue) is not a match — treat it the same as no reference at all rather
  than erroring or treating it as issue `#0`. When a number is found, fetch that issue:
  `gh issue view <N> --json title,body,labels`. If the issue itself references a parent PRD
  (`type:prd`), only pull the PRD too if the issue's own acceptance criteria are thin or ambiguous —
  don't add the extra fetch by default.
- **Local diff mode:** there's no PR body to read yet. Volta's branch naming convention is
  `feature/short-description` (see AGENTS.md) — it does not embed issue numbers, and squash-merge
  commit messages carry the *PR* number in parentheses, not the originating issue, so scanning the
  branch name or `git log` will usually come up empty here. Try it anyway since it's cheap, but treat
  asking the user which issue this addresses as the normal path in local-diff mode, not a rare
  fallback.
- **If no linked issue can be found either way**, proceed with just the diff — but say so explicitly
  in the final report, since a review with nothing to check requirements against is weaker than one
  that verified acceptance criteria were met. This is common and expected for chore/infra work that
  doesn't trace back to a tracked issue at all.

## Step 4: Dispatch both reviewers in parallel

This is the core of the skill — **both subagents must be launched in the same message**, not
sequentially. Sequential dispatch defeats the purpose (it's slower and the two reviews should be
independent, not influenced by seeing each other's output first).

Use the `Agent` tool twice, in one tool-call batch:

- `subagent_type: "android-reviewer"` — give it the diff (or PR number + `gh pr diff` command to
  run itself), the linked issue's title and acceptance criteria if found, and ask it to review per
  its persona instructions **and** flag any acceptance criteria the diff doesn't appear to satisfy.
- `subagent_type: "architect"` — same diff, same issue context, same instruction.

Each persona already defines its own output format (severity-bucketed lists for android-reviewer;
Architecture issues / ADR gaps / Dependency concerns for architect) — don't override that, just
point them at the diff and issue context and let them apply their own review priorities.

## Step 5: Merge into one report

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
- Treat an unmet acceptance criterion as Critical if the PR claims to close the issue but doesn't
  fully satisfy it — that's a correctness gap, not a nitpick. A criterion that's genuinely minor or
  out of scope for this PR belongs in Suggestions instead; use judgment rather than a blanket rule.
- Omit empty sections. If both personas found nothing, say so in one line rather than emitting
  empty headers.

## Step 6: Deliver the result

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

## Step 7: Update tracking labels (PR mode only)

A review that finds real problems means the work isn't actually done — the PR's own labels should
reflect that, not just the comment. This step only applies in PR mode; local-diff mode has no PR to
update. Apply this to the **PR only** — leave the linked issue's labels untouched; `whats-next` and
the rest of the workflow read state/priority off the issue, and that's `create-pr`'s and
`implement-feature`'s job to manage, not this skill's.

1. **Decide priority and state from severity:**
   - Any `Critical` finding → `priority:high`, and if currently `state:review`, move to
     `state:in-progress` — it's not mergeable as-is.
   - No `Critical` but at least one `Warning` → `priority:medium`, same `state:in-progress` move.
   - Only `Suggestions`, or no findings at all → leave both labels alone. Nothing here blocks merging,
     so it's still correctly sitting in `state:review`.
2. **Apply it to the PR** — check its current labels first (`gh pr view <N> --json labels`) so you
   only remove a label that's actually present, then swap:

   ```bash
   gh pr edit <N> --remove-label "priority:<old>" --add-label "priority:<new>"
   gh pr edit <N> --remove-label "state:review" --add-label "state:in-progress"
   ```

   `priority:*` labels are mutually exclusive (there's exactly one per PR), so always remove the old
   one rather than just adding the new one alongside it.

## Exit criteria

- Both subagents completed and their findings are reflected in the merged report
- Report is deduplicated, not a raw concatenation
- PR mode: comment posted (or explicitly skipped per user's answer) and report shown in chat
- PR mode: the PR's own priority/state labels reflect the severity found
- Local mode: report shown in chat, nothing posted anywhere
