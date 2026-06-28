# 0002 — Use Hilt for Dependency Injection

**Date:** 2026-06-27
**Status:** Accepted

## Context

Volta requires dependency injection to achieve testability: CoroutineDispatchers must be injectable so tests can substitute `TestCoroutineDispatcher`; repositories (Camera, AR, GPS, Export) must be mockable in ViewModel unit tests. Manual DI would become unwieldy at scale; a DI framework is needed.

## Decision

Use Hilt (Google's official Android DI framework, built on Dagger 2) as the DI solution. Modules are annotated with `@InstallIn` and scoped to the appropriate Hilt component (`SingletonComponent`, `ActivityRetainedComponent`, etc.). KSP is used for annotation processing instead of kapt for faster build times.

## Consequences

- Compile-time dependency graph validation prevents runtime DI failures.
- Excellent integration with Jetpack ViewModel (`@HiltViewModel`) and Compose (`hiltViewModel()`).
- Widely recognised by Android contributors — low onboarding friction.
- KSP annotation processing is faster than kapt; however, KSP Hilt support requires a recent Hilt version.
- Hilt components and scopes require some upfront learning but provide clear lifetime semantics.
