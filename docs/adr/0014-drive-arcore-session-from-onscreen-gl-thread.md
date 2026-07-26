# 0014 — Drive the ARCore Session from the On-Screen GLSurfaceView Thread

**Date:** 2026-07-24
**Status:** Accepted (supersedes the headless-thread design from ADR 0013)

## Context

ADR 0013 gave `ArCameraRepository` exclusive ownership of the ARCore `Session`, driven by a
dedicated headless background thread with its own off-screen EGL pbuffer surface and camera
texture. That design deliberately avoided any dependency on a UI-owned `GLSurfaceView`, and
flagged an explicit open follow-up: issue #16 (AR capture screen) needs camera image access too,
but ARCore only allows one owner of `session.update()`/the camera texture at a time, so #16 would
need to either consume frames from the existing headless repository or the headless-loop design
would need to be revisited.

Rendering the camera feed on screen requires the external OES camera texture to be sampled by a
shader running against the *visible* surface's EGL context. Sharing that texture across two
independently-created EGL contexts (the headless pbuffer context and a separate on-screen
context) is possible via `EGL_CONTEXT_SHARE`, but it doubles the amount of EGL/GL lifecycle code
to write and reason about, for no actual benefit: nothing in the product requires camera or pose
access while the capture screen isn't visible — the PRD's only flow is open app → capture screen
→ sweep → export.

## Decision

Move Session/EGL/texture ownership into the GL thread that `GLSurfaceView` itself creates and
manages. `ArCameraRepository` implements `android.opengl.GLSurfaceView.Renderer` directly:

- `onSurfaceCreated` creates the external OES camera texture (and rebinds it to any already-live
  `Session` if the GL surface was torn down and recreated, e.g. across a backgrounding cycle).
- `onSurfaceChanged` records the current display rotation/size for `session.setDisplayGeometry` to
  use so ARCore can correct for device/display orientation.
- `onDrawFrame` drives the existing `ArSessionOrchestrator.tick(isResumed)` state machine — that
  class is pure and thread-agnostic, so it is reused unmodified — and draws the camera passthrough
  quad when a session is active. The ARCore `Session` itself is still constructed lazily, the same
  way the old `ArSessionThread.createRealSession` did it: on the first `onDrawFrame` tick after
  `resume()`, not in `onSurfaceCreated`. Opening the session this way on the render thread can
  produce a brief visible hitch on (re)creation — the same trade-off ARCore's own HelloAR sample
  makes by driving the session from `onDrawFrame` — but that's judged acceptable since it happens
  at most once per screen visit (session resume), not per frame.

`GLSurfaceView` calls `onDrawFrame` continuously at the display refresh rate (~60 Hz) rather than
on a sleep-controlled loop, so a small `TickScheduler` gate (elapsed-time check against the delay
`tick()` returns) is added to avoid re-invoking `createSession()`/`resumeSession()` on every frame
while backing off — without it, a sustained `UNAVAILABLE_RETRY_INTERVAL_MS` backoff would collapse
to a `Session(context)` construction call ~60 times/sec instead of once every 5 s. `GatedSessionTicker`
(`data/ar/`) owns this gated-vs-forced distinction: `onDrawFrame` drives it via
`tickIfScheduled` (rate-limited by `TickScheduler`), while `flushPendingPause` (below) drives it
via `forceTick` (unconditional).

The domain-facing `ArSessionManager` interface (`isAvailable`, `currentPose`, `cameraFrames`,
`trackingState`, `resume()`, `pause()`) is unchanged — consumers outside `data/ar/` (the
ViewModel, and future frame-capture/blur-detection consumers) are unaffected by this rework.

To let the `ui/capture` layer embed the `GLSurfaceView` without importing anything from `data/`,
`ArModule` adds a second `@Binds` exposing the same `ArCameraRepository` singleton as
`android.opengl.GLSurfaceView.Renderer`. `CaptureViewModel` injects both interfaces; the
Composable reads `viewModel.cameraRenderer` to construct the `GLSurfaceView`. `GLSurfaceView.Renderer`
is a plain Android framework type, so this satisfies the `ui/` → `domain/` + Android framework
dependency rule without `ui/` depending on `data/`.

## Consequences

- The headless design's one advantage — camera/pose data available with no visible surface — is
  given up. Nothing in the current PRD needs it; if a future feature does, this decision should be
  revisited rather than reintroducing the shared-EGL-context approach preemptively.
- `ArCameraRepository` now has no life-cycle path independent of the capture screen's
  `GLSurfaceView`: no renderer attached means no `Session`, no pose/frame Flow emissions, no
  tracking state. Callers that only inject `ArSessionManager` (e.g. a future capture-trigger
  consumer) implicitly depend on the capture screen having created and attached the
  `GLSurfaceView` first.
- `GLSurfaceView.onPause()`/`onResume()` must be paired with `ArSessionManager.pause()`/`resume()`,
  or the GL thread keeps rendering (and ARCore keeps pumping frames) while the app is backgrounded.
  `ArCameraPreview` (`CaptureScreen.kt`) is the single authoritative trigger for both — an earlier
  version of this design also paused/resumed from a separate Activity-lifecycle observer, but that
  observer's calls were redundant everywhere they mattered (this composable exists only when
  camera permission is granted, the same condition under which the observer's calls did anything)
  and their independent teardown timing relative to this composable's own `onDispose` was an actual
  source of a camera-reservation bug, not just redundancy. Do not reintroduce a second trigger
  point without removing this one.
- Calling `GLSurfaceView.onPause()` alone does not guarantee `Session.pause()` runs first: it only
  blocks until the render thread *acknowledges* the pause request, not until it has drawn one more
  frame with an updated `resumed = false`. `Session.pause()` only happens inside
  `ArSessionOrchestrator.tick()`, called from `onDrawFrame`. `ArCameraPreview` closes this gap by
  calling `GLSurfaceView.queueEvent { ... }` and blocking on a latch until that runs — queued events
  are drained on the render thread ahead of its next pause/exit check — before calling
  `GLSurfaceView.onPause()`. This has not been verified with an instrumentation test against a real
  device; it is reasoned from `GLSurfaceView`'s documented `queueEvent` contract, not empirically
  confirmed — tracked as issue #43, which also covers the `connectedAndroidTest` CI job needed to
  run such a test (this repo's CI has no instrumentation-test job at all today).
  - The queued call must not be a plain `renderer.onDrawFrame(null)`: `onDrawFrame`'s normal path
    (`GatedSessionTicker.tickIfScheduled`) gates `orchestrator.tick()` behind
    `TickScheduler.shouldTick()`, which stays `false` while a scheduled retry is still pending
    (mid-backoff after `CAMERA_UNAVAILABLE`, or up to `PAUSED_POLL_INTERVAL_MS` after any prior
    tick). A forced `onDrawFrame(null)` call landing in that window would silently skip `tick()`
    entirely while the latch still counts down, giving false confidence the pause landed.
    `ArSessionManager.flushPendingPause()` exists specifically to reach `GatedSessionTicker.forceTick`
    instead, bypassing `TickScheduler` for this one-shot flush — it must not go through
    `onDrawFrame`/`tickIfScheduled`.
- This ADR covers only the passthrough camera quad — the sphere coverage overlay, frame counter,
  and coverage percentage from issue #16's full acceptance criteria are deferred to follow-up work
  once #10 (frame capture trigger) and #11 (blur detection) exist to supply real data.
