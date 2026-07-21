---
name: debug-android
description: >
  Diagnose and resolve Android build and runtime failures in Volta — Gradle errors, missing SDK
  configuration, SDK/version mismatches, dependency conflicts, ARCore/CameraX initialization issues,
  and Hilt wiring errors. Use this whenever a Gradle build fails with a non-obvious error, CI fails
  but the cause isn't clear, an unfamiliar stack trace shows up, or the user says things like "the
  build is broken", "why is this failing", "SDK location not found", or "this dependency won't
  resolve" — trigger even if they don't mention Android or Gradle by name.
---

# debug-android

Diagnose and resolve common Android build and runtime failures.

## When to invoke

- Gradle build fails with non-obvious errors
- Fresh clone or new machine fails with `SDK location not found`
- SDK version conflicts
- ARCore or CameraX initialization issues
- Dependency resolution failures

## Steps

1. **Read the error** — Parse the Gradle output for the actual error (not the "build failed" summary). Look for `FAILURE:` and `Caused by:` lines.
2. **Classify the failure:**

| Category | Symptoms | Common fixes |
|---|---|---|
| SDK not configured | `SDK location not found` | `local.properties` is gitignored (per-machine) — set `sdk.dir` there or export `ANDROID_HOME`. This blocks `lint`, tests, and build entirely (not just the affected task), so check this first on a fresh machine |
| SDK version mismatch | `compileSdk`, `targetSdk`, or `minSdk` errors | Check `build.gradle.kts` and `libs.versions.toml` for version alignment |
| Dependency conflict | `DuplicateClass`, version resolution errors | Check dependency tree with `dependencies` task, add exclusions or version constraints |
| ARCore compatibility | Theme or API removed errors | ARCore may reference deprecated platform APIs — check ARCore version compatibility |
| ktlint/Detekt | Style violations blocking build | Run the specific check task, read violations, fix or configure rules |
| Missing dependency | `Unresolved reference` | Check imports against `libs.versions.toml`, ensure dependency is declared |
| Hilt | `MissingBinding`, `ComponentProcessor` errors | Verify `@Inject`, `@Module`, `@InstallIn` annotations are correct |

3. **Check version catalog** — `gradle/libs.versions.toml` is the single source of truth for dependency versions.
4. **Check CI parity** — If a build passes locally but fails in CI (or vice versa), compare Java version, Gradle version, and SDK versions.

## Resolution approach

- Fix the root cause, not the symptom
- If a workaround is necessary, document it with a code comment explaining *why*
- If the fix involves changing a dependency version, invoke `write-adr`

## Handoff

A build fix can pass in isolation and still break something else — always confirm before moving on:

- Run `build-and-verify` to confirm the full pipeline passes, not just the piece that was broken
- If this was tracked as a GitHub issue, continue to `create-pr` to open the pull request

## Exit criteria

- Build succeeds
- Root cause identified and explained
- If a workaround was applied, it's documented
- `build-and-verify` passes
