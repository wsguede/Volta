# 0017 — Render the Sphere Coverage Overlay with a Custom OpenGL Renderer, Not a Scene-Graph Library

**Date:** 2026-07-26
**Status:** Accepted

## Context

Issue #16's acceptance criteria call for a 3D sphere overlay on the capture screen showing which
parts of the 24×12 coverage grid (`SphereCoverageTracker`) have been captured, world-locked to the
device's real orientation as the user sweeps. The issue itself left this as an open question:
"Should the sphere overlay use a custom OpenGL renderer or a scene graph library (e.g.,
SceneView)?" — deliberately left unresolved pending the camera-rendering approach, which ADR 0014
has since settled.

ADR 0014 already committed `ArCameraRepository` to a custom `GLSurfaceView.Renderer` implementation
driving raw GLES20 calls directly (`CameraQuadRenderer` draws the passthrough camera quad this
way). A scene-graph library (SceneView, Filament, etc.) would need to either take over rendering
entirely — conflicting with ADR 0014's GLES20/`GLSurfaceView` ownership model and ADR 0013's
single-owner-of-the-camera-texture constraint — or run alongside the existing pipeline, adding a
second rendering system and a new Gradle dependency for what is, geometrically, a small set of flat
semi-transparent quads.

## Decision

Extend the existing custom GLES20 pipeline. `SphereOverlayRenderer` (`data/ar/`) mirrors
`CameraQuadRenderer`'s structure: a hand-written vertex/fragment shader pair, compiled once in
`createOnGlThread()`, driven from `ArCameraRepository.onDrawFrame` after the camera quad is drawn.

Two further simplifications came out of re-reading the acceptance criteria closely:

- **Only uncovered cells are drawn at all.** The criteria describe captured cells as "clear (no
  fill or bright outline)" — i.e., nothing rendered, not even a wireframe. `buildUncoveredCellVertices`
  (`SphereMeshBuilder.kt`) therefore skips covered cells entirely rather than tracking a
  covered/uncovered color per vertex, which would need a second per-vertex attribute and buffer.
- **World-locking reuses ARCore's own camera matrices** (`Camera.getViewMatrix`/
  `getProjectionMatrix`, computed once per frame in `ArCameraRepository.processFrame` into a shared
  `viewProjectionMatrix`) rather than deriving a model transform from `DevicePose` by hand. This
  keeps the overlay's world-locking bug-for-bug consistent with however ARCore itself defines the
  camera's pose — the same source of truth `CameraQuadRenderer`'s passthrough quad implicitly
  relies on — instead of maintaining a second, independent orientation pipeline that could drift
  from it.

The mesh-building math (`buildUncoveredCellVertices`, `sphericalToCartesian`) is pure Kotlin with no
GLES/Android dependency, so it's unit-tested directly (`SphereMeshBuilderTest`) the same way
`extractLuma`/`extractNv21` are — only the actual GLES draw calls in `SphereOverlayRenderer` itself
are left untested, consistent with `CameraQuadRenderer`'s precedent.

`SphereMeshBuilder.kt` itself lives in `domain/coverage/`, alongside `SphereCoverageTracker`, not
`data/ar/` next to the renderer that consumes it — raised independently by both reviewers of the PR
that introduced this ADR. Nothing about it needs Android or GLES; keeping domain-pure logic in
`domain/` regardless of which layer happens to be its only current caller is the more consistent
reading of AGENTS.md's layer table, and it puts the mesh math next to the `CoverageGrid`/
`SphereCoverageTracker` types it's built from. `SphereOverlayRenderer` (`data/ar/`) imports
`buildUncoveredCellVertices` from `domain/coverage/` the same way it already imports `CoverageGrid`.

## Consequences

- No new Gradle dependency, no new ADR-worthy dependency choice beyond this one.
- The overlay mesh rebuilds (`SphereOverlayRenderer.updateGrid`) only when the `CoverageGrid`
  reference actually changes (once per capture event, not every frame), so the per-frame draw cost
  is just a `glDrawArrays` call over an already-built buffer.
- If a future requirement needs captured cells to render *something* (e.g., a completion pulse, a
  bright outline), `buildUncoveredCellVertices`'s "skip covered cells" shortcut will need revisiting
  — it currently has no path for rendering covered cells differently, only omitting them.
- The overlay is drawn at a fixed radius (5 world units) with near/far clipping planes chosen
  around it (0.1–100). If ARCore's camera ever needs a different near/far range for other purposes,
  these three values in `ArCameraRepository`/`SphereMeshBuilder.kt` need to stay consistent with
  each other.
