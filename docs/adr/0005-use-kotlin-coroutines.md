# 0005 — Use Kotlin Coroutines and Flow for Async

**Date:** 2026-06-15
**Status:** Accepted

## Context

Volta has multiple concurrent async concerns: camera frame streams, ARCore orientation updates, GPS location updates, stitching progress, and file I/O. A reactive/async abstraction is needed. Alternatives considered:
1. Kotlin Coroutines + Flow — first-class Kotlin, Jetpack-native.
2. RxJava — mature but non-Kotlin-native; adds a large dependency; Jetpack APIs no longer emit RxJava types.
3. Callbacks — unmanageable for complex pipelines.

## Decision

Use Kotlin Coroutines for structured concurrency and `kotlinx.coroutines.flow.Flow` for reactive data streams throughout all layers.

Key conventions:
- Launch coroutines from `viewModelScope` in ViewModels.
- `Dispatchers.IO` for file and sensor I/O.
- `Dispatchers.Default` for CPU-bound work (stitching, blur detection).
- All dispatchers are injected (never hardcoded) to allow `TestCoroutineDispatcher` substitution in tests.

## Consequences

- First-class Kotlin support; all Jetpack APIs (CameraX, Room, etc.) expose coroutine and Flow-based APIs.
- Structured concurrency ensures coroutines are cancelled when their scope is cancelled (e.g., ViewModel cleared).
- No need for RxJava — removes a significant dependency.
- Developers must understand coroutine scopes and cancellation; learning curve exists but is well-documented.
