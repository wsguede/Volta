# 0016 — Wire the Capture Pipeline into the Render Thread, with JPEG Compression Deferred to Dispatchers.Default

**Date:** 2026-07-26
**Status:** Accepted

## Context

Issue #16 (AR capture screen) needs the capture screen's frame counter and coverage percentage to
reflect real captures, not static placeholders. By the time this work picked back up, the pieces
existed independently but nothing wired them together: `BlurDetector` (#11), `FrameCaptureTrigger`
(#10), and `CoverageTracker` (#12) were all implemented and unit-tested in isolation, but no caller
invoked them against real ARCore pose/frame data.

`ArCameraRepository` (`data/ar/`, ADR 0014) is the only place that has both a `DevicePose` and the
raw camera `Image` for a given frame — it drives `ArSessionOrchestrator.tick()` from
`onDrawFrame`, which runs on the `GLSurfaceView`'s own render thread at up to 60 Hz.

`FrameCaptureTrigger.evaluate`'s own docstring is explicit that JPEG compression must not run
inside `evaluate`/`record`, or "whichever thread drives them" gets blocked by CPU-bound work — and
`DefaultFrameCaptureTrigger`'s class doc says the same. Doing the actual
`YuvImage.compressToJpeg` call synchronously on the GL thread would violate that constraint
directly: it's genuine CPU-bound work, running on the same thread that must keep up with the
display's refresh rate to avoid visibly janking the camera preview.

## Decision

Wire the pipeline directly into `ArCameraRepository`, split across two phases:

1. **Synchronous, on the GL thread** (`processFrame`/`emitCameraFrame`): extract the luma plane
   (already done for the `cameraFrames` flow), score it via `BlurDetector.sharpnessScore`, and call
   `FrameCaptureTrigger.evaluate(pose, sharpness)`. If approved, also copy the full NV21 byte array
   out of the still-open `Image` (`extractNv21` in `ArFrameExtractor.kt` — a plain array copy, cheap
   like `extractLuma`already was).
2. **Asynchronous, on `Dispatchers.Default`**: only the actual `YuvImage.compressToJpeg` call
   (`encodeNv21ToJpeg`) is dispatched off the GL thread, via `applicationScope.launch(defaultDispatcher)`
   using the existing `@ApplicationScope`/`@DefaultDispatcher` Hilt qualifiers (`AppModule.kt`).
   `FrameCaptureTrigger.record` and `CoverageTracker.markCovered` are called after compression
   completes, from that same coroutine.

`ArCameraRepository` gains `BlurDetector`, `FrameCaptureTrigger`, and `CoverageTracker` as
constructor dependencies (all already `@Singleton`-bound in `di/CaptureModule.kt` and
`di/CoverageModule.kt`); `BlurDetector` needed a new `@Provides` binding since it previously only
existed as `DefaultFrameCaptureTrigger`'s internal default.

Alternatives considered:

- **A separate `CapturePipeline` coordinator class**, injected into `ArCameraRepository` instead of
  the four raw dependencies. Rejected: the sequencing here (evaluate → extract → compress → record
  → markCovered) is linear with no branching complex enough to justify testing in isolation, unlike
  `ArSessionOrchestrator`/`GatedSessionTicker`, which were extracted because they have real
  conditional/timing logic (retry backoff, tick gating). `ArCameraRepository` itself is already
  untested directly (see below); adding a wrapper class wouldn't change that.
- **Compressing to JPEG on the GL thread anyway**, accepting an occasional dropped frame. Rejected
  outright — it directly contradicts `FrameCaptureTrigger`'s documented contract and would
  reintroduce the exact hazard that contract exists to prevent.

## Consequences

- `ArCameraRepository` is not unit tested directly (it never was — `Session`, `Frame`, and `Image`
  are real ARCore/Android types that can't be constructed in Volta's local JVM unit tests). This
  wiring is therefore verified only by the already-existing unit tests for `BlurDetector`,
  `FrameCaptureTrigger`, and `CoverageTracker` individually, plus manual on-device verification —
  consistent with how the rest of `ArCameraRepository`'s Session/GL logic is covered today.
- A capture's `record()`/`markCovered()` call lands slightly after the frame that triggered it,
  bounded by however long JPEG compression takes on `Dispatchers.Default`. This is expected and
  harmless: coverage/frame-count UI updating a frame or two late during a fast sweep is not
  perceptible, and nothing in the app depends on `record()` completing synchronously with capture.
- `record()` can now run on a `Dispatchers.Default` thread pool thread while `evaluate()` runs on
  the GL thread — a genuinely different thread than `DefaultFrameCaptureTrigger`'s own docstring
  literally describes ("evaluate and record run on the AR processing thread"). Its `@Synchronized`
  methods already handle this correctly (that's the "cross-thread handoff" the class doc already
  calls out for `capturedFrames`/`capturedFrameCount` reads), so no change was needed there — but
  the docstring's phrasing undersells how real that cross-thread case now is.
- `LaplacianBlurDetector.DEFAULT_SHARPNESS_THRESHOLD` remains a provisional, untuned value (#46);
  this wiring makes it live in the real capture path for the first time; #46 tracks tuning it
  against real device data.

### Known limitation: residual session-boundary TOCTOU race

`ArSessionManager.cancelPendingCaptures()` (called from `CaptureViewModel.startSession()`, before
`FrameCaptureTrigger.reset()`/`CoverageTracker.reset()`) closes the practical version of the race
where a frame approved right at a session boundary could compress after the *next* session's reset
already ran: `ArCameraRepository` scopes each compression coroutine under a `SupervisorJob`
(`captureJobs`) that `cancelPendingCaptures()` cancels-and-replaces, and each coroutine calls
`ensureActive()` immediately before `record()`/`markCovered()`.

This is not, however, a fully airtight barrier. `Job.cancel()` is fire-and-forget rather than
`cancelAndJoin()`, and `ensureActive()` is a check-then-act call, not an atomic one: if a
compression coroutine's `ensureActive()` happens to observe "not yet cancelled" immediately before
`cancelPendingCaptures()` runs, nothing prevents it from calling `record()`/`markCovered()` right
after — there is no synchronization tying "the next session's reset has fully applied" to "every
in-flight job from the previous session has observed cancellation." The window shrank from an
entire JPEG-compression duration (milliseconds) to the gap between two adjacent non-suspending
statements (a handful of CPU instructions) — vanishingly unlikely in practice, but not structurally
impossible.

Closing it fully would mean either making `cancelPendingCaptures()` suspend and `cancelAndJoin()`
before `startSession()` proceeds to `reset()`, or tagging each capture with a session/epoch id that
`record()`/`markCovered()` check before writing. Neither is implemented — the residual risk is
judged negligible enough not to justify making `startSession()` asynchronous (a `LaunchedEffect`
already tolerates a `suspend` call, so this is possible later without a `ui/` layer-boundary
problem, if the risk ever needs to be closed for real).
