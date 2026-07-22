# 0011 — Adopt Detekt 2.0.0-alpha for JVM 25 support

**Date:** 2026-07-22
**Status:** Accepted

## Context

Detekt 1.23.8 (the version Volta shipped with) crashes under JVM 25 with a bare `IllegalArgumentException: 25.0.3` — its embedded Kotlin compiler hardcodes an allowlist of supported JVM versions that stops short of 25. CI is unaffected (Temurin JDK 17), but any contributor on a JDK 25 machine cannot run `./gradlew detekt` locally without a `-Dorg.gradle.java.home` workaround pointing at a JDK 17 install (tracked as issue #20).

Detekt's 2.x line is the only line with JVM 25 support, built against the official Kotlin Analysis API instead of the legacy K1 compiler. As of this decision, 2.x has **no stable release** — the latest is `2.0.0-alpha.5` (June 2026); 2.0.0 GA has no announced date. Two options were considered:

1. **Stay on 1.23.8, keep the JDK 17 workaround** — zero risk, but the workaround is permanent friction for every contributor on a newer JDK, with no fix in sight on the 1.x line.
2. **Adopt `2.0.0-alpha.5` now** — unblocks local JDK 25 builds immediately, but pins core tooling to a pre-GA release whose config schema and rule set may still change before 2.0.0 ships.

Adopting 2.x also has a real ripple effect: it requires Kotlin ≥2.4.0 (2.0.0-alpha.5's stated compatibility floor), which in turn forced a project-wide Kotlin bump from 2.0.21 (see the AGP-9-built-in-Kotlin migration landed alongside the Dependabot dependency bump in PR #30, and the pin to exactly `2.4.0` rather than `2.4.10` to stay within CodeQL's supported Kotlin range).

## Decision

Adopt Detekt `2.0.0-alpha.5`, accepting alpha status, to unblock local development on JVM 25 without a workaround.

Changes required beyond the version bump:
- Gradle plugin coordinates: `io.gitlab.arturbosch.detekt` → `dev.detekt`
- Detekt task type: `io.gitlab.arturbosch.detekt.Detekt` → `dev.detekt.gradle.Detekt`; `jvmTarget` is now a `Property<String>` (`.set("17")`) rather than a direct assignment
- `config/detekt/detekt.yml`: the `build.maxIssues` concept is gone — 2.x fails the build on any issue with severity `Error`, which is every rule's default severity, so the removal is behavior-preserving. Renamed threshold keys: `LongMethod.threshold` → `allowedLines`, `LongParameterList.functionThreshold`/`constructorThreshold` → `allowedFunctionParameters`/`allowedConstructorParameters`, `TooManyFunctions.thresholdInFiles`/`thresholdInClasses` → `allowedFunctionsPerFile`/`allowedFunctionsPerClass`

Verified: `./gradlew detekt` succeeds under both the default JDK 25 and JDK 17 (matching CI's Temurin 17), and correctly fails the build when a real violation is introduced (confirmed with a scratch `MaxLineLength` violation before reverting it).

## Consequences

- Local `./gradlew detekt` works out of the box on JVM 25 — the workaround documented for issue #20 is no longer needed.
- Volta's static analysis now tracks a pre-GA tool. A future `2.0.0-alpha.x` or GA release may introduce further config-schema or rule-set changes requiring another pass over `config/detekt/detekt.yml`.
- `libs.versions.toml`'s `detekt` version should be revisited when 2.0.0 reaches GA, to move off the alpha track.
- No change to CI (`.github/workflows/ci.yml` already runs on Temurin JDK 17 and needs no modification).
