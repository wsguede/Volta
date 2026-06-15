# 0006 — Android-First Native Implementation

**Date:** 2026-06-15
**Status:** Accepted

## Context

Volta's core feature — real-time AR-guided photosphere capture — depends heavily on device AR capabilities. Platform options considered:
1. Native Android (Kotlin + ARCore) — best-in-class AR fidelity, direct hardware access.
2. Flutter — cross-platform, but AR plugin ecosystem is immature for production-quality photosphere capture.
3. React Native — similar cross-platform trade-offs; no first-party ARCore support.
4. Native iOS (Swift + ARKit) — equal AR quality but doubles development scope for v1.

## Decision

Build Volta as a native Android application using Kotlin and ARCore. iOS (Swift + ARKit) is deferred to post-v1.

## Consequences

- Best AR fidelity: ARCore provides accurate 6DOF pose estimation directly from Google, used natively without a plugin abstraction layer.
- Full access to Android camera hardware and CameraX, with no cross-platform bridging overhead.
- OpenCV native bindings are well-supported on Android (AAR/Maven artifact).
- iOS users cannot use Volta v1; this is an accepted scope constraint.
- A future iOS port would require a parallel Swift codebase (ARKit + Core Image or OpenCV iOS); the domain logic documented in ADRs will guide architectural parity.
