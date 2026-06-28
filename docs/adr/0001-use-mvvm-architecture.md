# 0001 — Use MVVM Architecture

**Date:** 2026-06-27
**Status:** Accepted

## Context

Volta requires an architecture pattern for a Compose-based Android app that manages complex, real-time AR state: live camera frames, ARCore orientation data, GPS coordinates, sphere coverage percentages, and stitching progress. The pattern must support testability (isolated ViewModels, injectable dependencies) and the reactive UI requirements of Jetpack Compose.

## Decision

Adopt MVVM (Model-View-ViewModel) with the following specifics:
- `ViewModel` (Jetpack Architecture Components) holds and manages UI-related state.
- `StateFlow` exposes state to the UI; the UI collects it reactively.
- Jetpack Compose serves as the View layer — stateless Composables that react to state.
- Unidirectional data flow: UI emits events → ViewModel updates state → UI reacts.

## Consequences

- Well-documented pattern with large community support and extensive Jetpack library alignment.
- Real-time sensor state (coverage, GPS, stitching progress) maps naturally to `StateFlow`.
- ViewModels survive configuration changes automatically, which is critical during the long AR capture session.
- Business logic remains outside Composables, improving testability.
- Android framework dependencies are kept out of the `domain/` layer, enabling pure JVM unit tests.
