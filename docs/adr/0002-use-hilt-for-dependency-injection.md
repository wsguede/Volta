# 0002 — Use Hilt for Dependency Injection

**Date:** 2026-06-15
**Status:** Accepted

## Context

Volta requires dependency injection to achieve testability: CoroutineDispatchers must be injectable so tests can substitute `TestCoroutineDispatcher`; repositories (Camera, AR, GPS, Export) must be mockable in ViewModel unit tests. Manual DI would become unwieldy at scale; a DI framework is needed.

## Decision

Use Hilt (Google's official Android DI framework, built on Dagger 2) as the DI solution. Modules are annotated with `@InstallIn` and scoped to the appropriate Hilt component (`SingletonComponent`, `ActivityRetainedComponent`, etc.).

## Consequences

- Compile-time dependency graph validation prevents runtime DI failures.
- Excellent integration with Jetpack ViewModel (`@HiltViewModel`) and Compose (`hiltViewModel()`).
- Widely recognised by Android contributors — low onboarding friction.
- Annotation processing (kapt) adds build time; consider KSP migration if build times become a problem.
- Hilt components and scopes require some upfront learning but provide clear lifetime semantics.
