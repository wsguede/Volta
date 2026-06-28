# 0004 — Use OpenCV for On-Device Stitching

**Date:** 2026-06-27
**Status:** Accepted

## Context

Volta must stitch multiple camera frames into a single equirectangular JPEG entirely on-device (no network). The stitching pipeline involves feature detection, feature matching, homography estimation, warping, and blending — a non-trivial computer vision problem. Options considered:
1. OpenCV for Android (Stitcher API) — mature, battle-tested, runs on CPU.
2. Custom GLSL compute shader pipeline — faster on GPU but far higher development cost.
3. ML Kit / custom TFLite model — not suitable for geometric stitching.

## Decision

Use OpenCV for Android (version 4.x) via the `org.opencv:opencv` Maven artifact. The `Stitcher` API will be the primary entry point, with custom pre/post-processing as needed for GPano metadata output.

## Consequences

- Mature feature matching (SIFT/ORB) and blending pipeline with minimal custom code.
- Adds approximately 35 MB to APK size; acceptable for a sideloaded v1.
- CPU-bound: stitching must run on `Dispatchers.Default` and must not block the main thread.
- Performance on mid-range hardware must be validated; stitch times >30 s may require progress indication.
- OpenCV's `Stitcher` assumes overlapping fields of view — frame spacing must ensure sufficient overlap.
