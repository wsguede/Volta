# 0003 — Use CameraX for Camera Access

**Date:** 2026-06-15
**Status:** Accepted

## Context

Volta needs camera access for two purposes: a live AR preview shown to the user while they sweep the camera, and per-frame image analysis for blur detection and capture triggering. The alternative is Camera2 (the lower-level API), which provides maximum control but requires substantial boilerplate and manual device-compatibility handling.

## Decision

Use CameraX over Camera2. Specifically:
- `Preview` use case for the AR viewfinder.
- `ImageAnalysis` use case for per-frame access (blur detection, capture triggering).
- `ImageCapture` use case for saving full-resolution frames to disk.

## Consequences

- Automatic device compatibility across the Android ecosystem via CameraX's device quirks database.
- `ImageAnalysis` provides the frame-level access needed for `FrameCaptureController`.
- Lifecycle-aware binding simplifies camera teardown on activity/fragment lifecycle changes.
- Slightly less control than Camera2; however, Volta's needs (preview + analysis + capture) are well within CameraX's capabilities.
- ARCore camera sharing requires care: ARCore must be given camera ownership, and CameraX must be configured to share the camera session. This integration point must be validated on target devices.
