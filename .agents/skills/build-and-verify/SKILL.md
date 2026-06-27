# build-and-verify

Run the full quality pipeline and report results. Diagnose failures before reporting.

## When to invoke

- Before opening any PR
- After significant code changes to validate nothing is broken
- When CI fails and you need to reproduce locally

## Steps

1. **Format check** — Run ktlint. On failure: show the violating file and line, attempt auto-fix with `ktlintFormat`, then re-check.
2. **Code quality** — Run Detekt. On failure: show the rule violation and suggest the minimal fix.
3. **Unit tests** — Run tests. On failure: read the failing test, identify whether it's a test bug or a code bug, and report.
4. **Build** — Assemble the debug APK. On failure: parse the Gradle error output and diagnose (missing dependency, SDK version mismatch, etc.).

## Failure handling

Do not just paste raw Gradle output. For each failure:
- Identify the root cause (not the symptom)
- Suggest a specific fix
- If multiple failures, fix them in dependency order (format → quality → tests → build)

## Exit criteria

All four steps pass. Report a one-line summary per step.
