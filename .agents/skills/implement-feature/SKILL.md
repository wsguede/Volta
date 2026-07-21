---
name: implement-feature
description: >
  Implement a feature end-to-end from a GitHub type:feature issue using red-green-refactor TDD,
  respecting Volta's MVVM layer boundaries (ui/domain/data), then verify and open the PR. Use this
  whenever the user asks to implement, build, or start work on a feature issue, references an issue
  number with intent to build it ("implement issue #15", "let's build the settings screen"), or asks
  to pick up the next ready issue — trigger even if they don't say "test-driven" or name the skill.
---

# implement-feature

Implement a feature from a GitHub issue using test-driven development.

## When to invoke

- Starting work on a `type:feature` issue
- Invoked as `/implement-feature #<issue-number>`

## Prerequisites

- The issue must exist and have `state:ready` label (requirements are clear)
- If the issue has a parent PRD (`type:prd`), read the PRD for context first

## Steps

1. **Read the issue** — Fetch the GitHub issue. Understand the acceptance criteria. If the issue references a PRD, read that too.
2. **Check the architecture** — Identify which layers (`ui/`, `domain/`, `data/`) are affected. Read existing code in those packages.
3. **Plan** — Outline the implementation approach. Identify new files, modified files, and any ADR-worthy decisions.
4. **Update issue state** — Move the issue to `state:in-progress`.
5. **Red** — Write failing tests that express the acceptance criteria. Tests must fail for the right reason.
6. **Green** — Write the minimal code to make tests pass. Follow existing patterns in the codebase.
7. **Refactor** — Clean up without changing behavior. Ensure naming conventions match AGENTS.md.
8. **Verify** — Invoke `build-and-verify` skill to run the full pipeline. If it fails with a non-obvious build or dependency error (not just a failing test you can fix directly), invoke `debug-android` to diagnose before retrying.
9. **ADR check** — If you introduced a new dependency or made an architecture choice, invoke `write-adr`.
10. **Open PR** — Invoke `create-pr` to open the pull request.

## Layer boundary rules

- `domain/` classes must not import anything from `android.*` or `androidx.*`
- `ui/` Composables must be stateless — hoist state to the ViewModel
- `data/` repositories are the only layer that touches Android framework APIs

## Exit criteria

- All tests pass (new and existing)
- Build succeeds
- Issue updated to `state:in-progress`
- ADR written if applicable
- PR opened via `create-pr`
