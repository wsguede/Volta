# debug-android

Diagnose and resolve common Android build and runtime failures.

## When to invoke

- Gradle build fails with non-obvious errors
- SDK version conflicts
- ARCore or CameraX initialization issues
- Dependency resolution failures

## Diagnostic checklist

1. **Read the error** — Parse the Gradle output for the actual error (not the "build failed" summary). Look for `FAILURE:` and `Caused by:` lines.
2. **Classify the failure:**

| Category | Symptoms | Common fixes |
|---|---|---|
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
- If the fix involves changing a dependency version, check if an ADR update is needed

## Exit criteria

- Build succeeds
- Root cause identified and explained
- If a workaround was applied, it's documented
