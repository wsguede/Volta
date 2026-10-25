# 0019 — Use Robolectric for Unit Tests That Need Real Android Framework Classes

**Date:** 2026-10-25
**Status:** Accepted

## Context

The update repository (#58, ADR 0018) parses `update.json` with the framework's `org.json`, and
its tests must parse saved JSON samples: a valid manifest, an unknown `schemaVersion`, and
malformed JSON. In local JVM unit tests, `android.jar` only contains placeholder classes. Because
`app/build.gradle.kts` sets `isReturnDefaultValues = true`, those placeholders return `null`, `0`,
or `false` instead of throwing. Parsing tests would run against an `org.json` that does nothing,
so they could pass or fail for the wrong reason.

Alternatives considered:

- **`org.json:json` as a test-only dependency.** Small and fast, but it is a different
  implementation of the same API. It only fixes `org.json`; the next framework class (for example
  `PackageManager` or `Uri`) would need its own workaround.
- **Instrumentation tests (`androidTest`).** Real framework classes, but they need a device or
  emulator. CI runs only `testDebugUnitTest`, so these tests would not gate merges.
- **Hide `org.json` behind an interface and test only the mapping.** Leaves the real parsing,
  which is where malformed input fails, untested.

## Decision

Add Robolectric as a `testImplementation` dependency, with its version in
`gradle/libs.versions.toml`. Tests that need real framework behavior run with
`@RunWith(RobolectricTestRunner::class)`. All other unit tests stay plain JVM tests.

- Use Robolectric only where a test needs real framework behavior. Keep pure logic in `domain/`
  and in plain JVM tests, which are faster.
- Keep `isReturnDefaultValues = true`. Tests that run without Robolectric, such as
  `PanoramaMetadataWriterTest`, still depend on it.
- Choose a Robolectric release that supports the project's `targetSdk`. If it lags behind a
  `targetSdk` bump, pin the SDK level in `src/test/resources/robolectric.properties` until it
  catches up.

## Consequences

- `GitHubUpdateRepository` parsing and file-handling tests run in CI with real `org.json`, real
  `Context.cacheDir`, and real `java.io` behavior.
- Robolectric tests are slower. On first run, Robolectric also downloads an Android framework
  jar for each SDK level it uses. It stores the jar outside the Gradle cache, so CI downloads it
  on every run unless that directory is cached too.
- Robolectric's `PackageManager.getPackageArchiveInfo` does not parse real APKs. APK inspection
  stays behind a small seam so the verification logic can be tested with fakes. One
  physical-device check covers the real call (#59).
- Each `targetSdk` bump now also depends on Robolectric support for that SDK level.
