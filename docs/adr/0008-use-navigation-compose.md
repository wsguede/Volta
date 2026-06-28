# 0008 — Use Navigation Compose for Screen Navigation

**Date:** 2026-06-28
**Status:** Accepted

## Context

Volta has three screens (Capture, Export, Settings) that require navigation with proper back-stack management. Options considered:
1. Navigation Compose (Jetpack) — first-party, ViewModel-scoped, Hilt-integrated.
2. Voyager / Decompose — third-party libraries with more features but additional dependencies and learning curve.
3. Manual Composable switching — simple but no back-stack, no deep linking, no lifecycle scoping.

## Decision

Use Jetpack Navigation Compose (`androidx.navigation:navigation-compose`) with string-based routes. The navigation graph is defined in a single `VoltaNavGraph` composable. Screen Composables receive navigation callbacks as lambdas, not `NavController` references.

String-based routes were chosen over type-safe navigation (available in Navigation 2.8+) because the app has three fixed screens with no arguments. Type-safe navigation adds Kotlin serialization as a dependency for negligible benefit at this scale. Revisit if the app grows argument-passing routes.

## Consequences

- First-party Jetpack library with stable API and long-term support.
- `hiltViewModel()` integration scopes ViewModels to navigation destinations automatically.
- Back-stack is managed by the framework — no manual state tracking.
- Screens are decoupled from navigation mechanics via lambda callbacks, making them independently testable and previewable.
- String routes are not compile-time validated — route typos become runtime errors. Acceptable for three routes; reconsider for larger route graphs.
