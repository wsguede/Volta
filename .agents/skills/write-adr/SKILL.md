# write-adr

Create an Architecture Decision Record following the project template.

## When to invoke

- Adding a new dependency
- Changing the architecture pattern
- Choosing between two significant implementation approaches
- When in doubt — write one

## Steps

1. **Determine the next number** — List existing ADRs in `docs/adr/` and increment.
2. **Write the ADR** — Copy `docs/adr/TEMPLATE.md` and fill in each section.
3. **File naming** — `docs/adr/NNNN-short-title.md` (e.g., `0007-use-room-for-caching.md`).
4. **Link to code** — Reference specific files or packages affected by the decision.
5. **Check for superseded ADRs** — If this decision replaces an earlier one, update the old ADR's status to `Superseded by NNNN`.

## Guidelines

- Keep it concise — one page maximum
- Focus on the *why*, not the *what* — the code shows what; the ADR explains why
- Document alternatives considered in the Context section
- Be honest about trade-offs in Consequences

## Exit criteria

- ADR file created in `docs/adr/` with correct numbering
- Any superseded ADRs updated
- ADR referenced in the PR body if part of a feature branch
