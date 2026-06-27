---
name: whats-next
description: >
  Show the next most important things to work on by analyzing GitHub issues, labels, and project workflows.
  Use this skill whenever the user asks what to do next, what's left, what's the status, what needs attention,
  what's blocking progress, or wants a prioritized task list. Also use when the user says things like
  "what should I work on", "what's on deck", "priorities", "backlog", or "show me the board".
---

# whats-next

Analyze GitHub issues and their labels to produce a prioritized list of next actions, using the project's workflow chains to determine what each issue needs.

## Steps

### 1. Gather issue data

Fetch all open GitHub issues with their labels, assignees, and linked references:

```bash
gh issue list --state open --limit 100 --json number,title,labels,assignees,body,createdAt,updatedAt
```

Also check for any open PRs that may correspond to in-progress work:

```bash
gh pr list --state open --limit 50 --json number,title,labels,headRefName,body
```

### 2. Classify each issue

Use the label taxonomy to classify every open issue by type and state:

| Type label | State label | What it means |
|---|---|---|
| `type:prd` | any | A product requirements document — check if it has child feature issues |
| `type:feature` | `state:triage` | Needs scoping and requirements before it can be worked on |
| `type:feature` | `state:ready` | Ready to implement — this is actionable work |
| `type:feature` | `state:in-progress` | Someone is working on it — check for staleness or blockers |
| `type:feature` | `state:review` | PR is open — needs review |
| `type:bug` | any state | Bugs follow the same state flow but are generally higher urgency |
| `type:chore` | any state | Infrastructure/maintenance work |

Issues missing a state label should be flagged — they need triage.

### 3. Determine next action for each issue

Apply the project's workflow chains to figure out what comes next:

**PRD without child features:**
→ "Break this PRD into `type:feature` issues with `state:triage`"

**PRD with all child features closed:**
→ "Close this PRD — all work is complete"

**Feature/bug in `state:triage`:**
→ "Scope and clarify requirements, then move to `state:ready`"

**Feature/bug in `state:ready`:**
→ "Implement using `/implement-feature #N` (or `/debug-android` for bugs)"

**Feature/bug in `state:in-progress`:**
→ Check if a branch or PR exists. If not, it may be stalled.
→ If work is in progress, suggest "Continue implementation" or "Run `/build-and-verify`"

**Feature/bug in `state:review`:**
→ "Review the open PR and merge or request changes"

**No state label:**
→ "Triage this issue — add a state label"

### 4. Prioritize the list

Sort the actions using this priority order:

1. **Blocking items first** — PRs in `state:review` (unblock merges), bugs with `priority:high`
2. **Actionable work** — `state:ready` issues, ordered by priority label (`high` > `medium` > `low` > unlabeled)
3. **Planning work** — PRDs that need decomposition, `state:triage` items that need scoping
4. **Stalled work** — `state:in-progress` items with no recent activity

### 5. Present the results

Output a concise, scannable list. For each item:

```
## Next up

### 1. [Title] (#number)
   Type: feature | State: ready | Priority: high | Layer: domain
   → **Action:** Implement this — run `/implement-feature #N`

### 2. [Title] (#number)
   Type: prd | State: — | Priority: —
   → **Action:** Break into feature issues — this PRD has no child issues yet

### 3. [Title] (#number)
   Type: bug | State: triage | Priority: medium | Layer: data
   → **Action:** Scope this bug — reproduce it, clarify expected behavior, then mark `state:ready`
```

Keep each entry to 2-3 lines. The user wants a quick answer, not a wall of text.

If there are no open issues, say so and suggest creating one.

## Finding child issues of a PRD

PRDs link to child issues through references in the body text (e.g., `#2`, `#3`) or through GitHub's sub-issue / tasklist features. To find children:

1. Parse the PRD body for issue references (`#N`)
2. Check if any open issues mention the PRD number in their body
3. Look for task lists in the PRD body (`- [ ] #N`)

```bash
gh issue view <prd-number> --json body,title
```

## Edge cases

- **Empty backlog:** No open issues at all → suggest the user create issues or a PRD
- **Everything in triage:** Nothing is `state:ready` → highlight that planning is the bottleneck
- **Multiple PRDs:** Group features under their parent PRD for context
- **Stale in-progress:** If an issue has been `state:in-progress` for a long time with no corresponding branch or PR, flag it as potentially stalled
