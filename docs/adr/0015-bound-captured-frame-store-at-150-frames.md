# 0015 — Bound the In-Memory Captured-Frame Store at 150 Frames, Drop-Oldest on Overflow

**Date:** 2026-07-26
**Status:** Accepted

## Context

Issue #10 (`domain/capture/DefaultFrameCaptureTrigger`) holds every accepted sweep frame in memory
as a compressed JPEG (`CapturedFrame(jpeg: ByteArray, pose: DevicePose)`) until export, per ADR-level
constraints in the PRD (#1): the app is session-focused and retains nothing on disk once a
photosphere is exported, and stitching (#13) needs the full frame set available at once rather than
streamed from storage.

Holding frames unboundedly risks an `OutOfMemoryError` if a user sweeps for an extended period
without exporting — min-API-30 devices are the floor, and low-memory devices are common in that
range. Some bound was needed; the question was where to put it and how to behave once hit.

Alternatives considered:

- **Disk-backed cache** (spill to app-private storage once a threshold is hit) — rejected: adds
  disk I/O to the capture hot path, complicates the "retains nothing once exported" guarantee (now
  there's a spill directory to clean up on crash/kill), and none of `domain/`, `data/export/`, or
  the PRD called for surviving process death mid-sweep.
- **Dynamic cap via `ActivityManager.getMemoryClass()`** — rejected for v1: more accurate per-device
  headroom, but pulls an Android framework dependency into the capture-trigger boundary (the cap
  itself is decided in `di/CaptureModule.kt`, which is allowed to touch Android APIs, so this
  remains possible later without violating layer boundaries) and adds complexity not yet justified
  by a concrete OOM report.
- **Reject-new instead of drop-oldest** once the cap is hit — rejected: a photosphere sweep's most
  recent frames are the ones the user is actively capturing; silently discarding *new* frames would
  make the tail of a long sweep never register, which is more surprising than trimming the oldest
  (least likely to still be needed once coverage has moved on).

## Decision

`DefaultFrameCaptureTrigger` caps its in-memory frame store at 150 frames
(`DEFAULT_MAX_STORED_FRAMES`, a named, tunable constant). Once a capture would push the store past
that cap, the oldest frame is dropped before the new one is added. Dropping fires a caller-supplied
`onFrameDropped: () -> Unit` callback rather than logging directly — `domain/` has no Timber/Android
dependency (per AGENTS.md), so the `di/` binding (`CaptureModule.kt`) wires the callback to
`Timber.w(...)`, keeping the warning at the Android boundary while the domain class stays pure
Kotlin and unit-testable without a logging framework.

150 frames is a starting point sized against the worst-case memory budget below, not a tuned value
— it will need adjustment once real-device sweep durations and frame sizes are measured.

## Consequences

- **Memory ceiling:** 150 frames × ~4 MB compressed JPEG ≈ 600 MB worst case. This is a real risk on
  low-memory API 30 devices; if this proves too aggressive in practice, the fix is either a smaller
  cap or the dynamic `ActivityManager`-based cap considered above and rejected for v1.
- **No disk-spill fallback:** a sweep that hits the cap silently loses its earliest frames rather
  than persisting them anywhere. This is consistent with the app's "retains nothing" design but
  means a very long, slow sweep can quietly under-cover the parts of the sphere captured first.
- **Silent-by-default risk:** because the cap-exceeded signal is a plain callback rather than an
  enforced side effect, a future `di/` wiring that reuses `DefaultFrameCaptureTrigger` without
  supplying `onFrameDropped` will drop frames with no warning at all, exactly as `CaptureModule.kt`
  did before this ADR. There's no compile-time guard against that regression.
- Follow-up: #16 (AR capture screen) will need to surface capture/coverage feedback to the user;
  whether a sustained cap-exceeded warning should also reach the UI (not just Timber) is open for
  that issue to decide.
