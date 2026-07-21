---
name: build-and-verify
description: >
  Run Volta's full local quality pipeline — ktlint, Detekt, Android lint, unit tests, and a debug
  build — and diagnose any failures before reporting. Use this before opening any PR, after making
  significant code changes, or when CI fails and you need to reproduce and diagnose it locally.
  Also invoke when the user says things like "run the checks", "make sure this passes CI", "verify
  my changes", "run lint and tests", or asks to confirm the build is green before committing.
---

# build-and-verify

Run the full quality pipeline and report results. Diagnose failures before reporting.

## When to invoke

- Before opening any PR
- After significant code changes to validate nothing is broken
- When CI fails and you need to reproduce locally

## Steps

1. **Format check** — Run ktlint. On failure: show the violating file and line, attempt auto-fix with `ktlintFormat`, then re-check.
2. **Code quality** — Run Detekt. On failure: show the rule violation and suggest the minimal fix.
3. **Android lint** — Run `./gradlew lint`. On failure: show the violating file and rule, suggest the minimal fix. CI runs this as its own `Android Lint` job; skipping it here means this skill hasn't actually matched what CI checks.
4. **Unit tests** — Run tests. On failure: read the failing test, identify whether it's a test bug or a code bug, and report.
5. **Build** — Assemble the debug APK. On failure: if the cause is obvious (typo, missing import), fix it directly. If it isn't — dependency resolution, SDK mismatches, Hilt/ARCore errors — invoke `debug-android` for full diagnosis rather than guessing.

## Failure handling

Do not just paste raw Gradle output. For each failure:
- Identify the root cause (not the symptom)
- Suggest a specific fix
- If multiple failures, fix them in dependency order (format → quality → lint → tests → build) — a failure early in the chain can cascade into false failures downstream

## Exit criteria

All five steps pass. Report a one-line summary per step.
