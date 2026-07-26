# 0013 — Use ARCore for Camera Access, Superseding CameraX

**Date:** 2026-07-22
**Status:** Accepted (supersedes ADR 0003; threading/EGL-ownership detail superseded by ADR 0014)

## Context

ADR 0003 chose CameraX for camera access on the basis of its device-compatibility layer and
lifecycle-aware preview/analysis/capture use cases. Issue #7 established that the capture screen
needs ARCore-fused device orientation (gyroscope + accelerometer + visual odometry) in addition to
camera frames — not achievable through CameraX alone. ARCore requires exclusive ownership of the
camera session to perform sensor fusion; running CameraX alongside it for preview or analysis
would mean two independent consumers contending for one physical camera, which is not a supported
configuration.

The CameraX scaffolding added under ADR 0003 (`CameraRepository`, `CameraModule`, the
`camerax-*` dependencies) was never implemented beyond empty interfaces/modules and has no
consumers.

## Decision

ARCore owns the camera exclusively for the capture screen. `ArCameraRepository` (`data/ar/`)
wraps ARCore's `Session`, driving an update loop that exposes device pose, camera frames, and
tracking state. The CameraX dependency is removed entirely: `CameraRepository`, `CameraModule`,
and the `camerax-*` entries in `gradle/libs.versions.toml` / `app/build.gradle.kts` are deleted.

At the time this ADR was written, that update loop was headless (non-rendering), driven by a
dedicated background thread with its own off-screen EGL surface — see the now-resolved follow-up
below. ADR 0014 replaced that threading/EGL-ownership detail; the decision on this page (ARCore
over CameraX, exclusive camera ownership) is unaffected and still stands.

## Consequences

- Loses CameraX's device-quirks compatibility database for camera access; ARCore has its own
  compatibility gate via `ArCoreApk.checkAvailability` and the manifest's `com.google.ar.core`
  required feature declaration.
- One fewer camera-access path to reason about — no risk of CameraX and ARCore contending for the
  same camera.
- Resolved: issue #16 (AR capture screen — on-screen camera passthrough + sphere overlay) needed
  camera image access too, and ARCore only allows one owner of `session.update()`/the camera
  texture at a time. This was resolved by moving Session/EGL/texture ownership onto the on-screen
  `GLSurfaceView`'s own render thread rather than the headless background thread described above —
  see ADR 0014 for the full decision and its own consequences (notably, the camera preview now has
  no life-cycle independent of the capture screen's `GLSurfaceView`, which was not true of the
  original headless design).
