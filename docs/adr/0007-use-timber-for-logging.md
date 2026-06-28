# 0007 — Use Timber for Logging

**Date:** 2026-06-28
**Status:** Accepted

## Context

Android provides `android.util.Log` for logging, but it requires a manual `TAG` constant in every class, lacks lifecycle-aware tree management, and offers no way to suppress logs in release builds without ProGuard rules or wrapper boilerplate. Alternatives considered:
1. `android.util.Log` directly — zero dependencies, but verbose and error-prone.
2. Timber — lightweight wrapper with auto-tagging and pluggable tree architecture.
3. Custom logging wrapper — full control, but maintenance overhead for a solved problem.

## Decision

Use Timber (`com.jakewharton.timber`) for all application logging. Plant `Timber.DebugTree()` in debug builds only via `VoltaApplication.onCreate()`. Never use `android.util.Log` directly.

## Consequences

- Auto-inferred class tags eliminate `TAG` boilerplate and prevent copy-paste tag errors.
- Release builds produce no log output without any ProGuard configuration — just don't plant a tree.
- The `Tree` abstraction allows adding crash-reporting trees (e.g., Firebase Crashlytics) later without changing call sites.
- Adds a single lightweight dependency (~50 KB).
