# 0010 — GPS Fix Acceptance and Flow-Sharing Semantics

**Date:** 2026-07-19
**Status:** Accepted

## Context

`GpsRepository.location` is the single source of GPS coordinates for the app; issue #9 (export)
and the capture UI will both consume it. The first implementation was a cold `callbackFlow`
behind a `@Singleton` binding, which had three contract problems surfaced in review of PR #23:

1. Every collector opened its own `PRIORITY_HIGH_ACCURACY` GMS subscription — duplicated
   high-power GPS usage that the `@Singleton` binding misleadingly appeared to share.
2. A fix with accuracy worse than the 50 m threshold emitted `null`, erasing a previously good
   fix from the stream. Every consumer would have had to reimplement "hold the last good fix" to
   satisfy the project constraint "warn but never block — embed best available coordinates."
3. Permission was checked once per collection; a grant made while collecting (the normal flow
   with the issue-#18 permission dialog) never recovered without an explicit re-collection.

A second review round on PR #23 surfaced two more contract gaps in that same fix:

4. `FusedLocationProviderClient.requestLocationUpdates` performs its own synchronous permission
   check and throws `SecurityException` if permission isn't actually held at call time — a
   TOCTOU race with the repository's own check. Nothing in the `location` chain caught it, so it
   would propagate through `shareIn`'s sharing coroutine uncaught and crash the app process,
   directly contradicting "GPS is optional, never block" (worse: never crash).
5. Permission checked once per collection also meant a *revocation* mid-collection (the user
   flips the system setting while a long-lived subscriber, e.g. a capture ViewModel, stays
   subscribed) was never detected — the repository would keep serving the stale last-good fix
   indefinitely, or eventually hit the same `SecurityException` crash path when GMS internally
   re-validated.

## Decision

The repository owns the acceptance-and-retention policy; consumers just take the latest value:

- **Acceptance:** a fix is acceptable iff its horizontal accuracy is ≤
  `GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS` (50 m). The rule lives on the
  domain model; the repository applies it.
- **Retention:** the stream carries "latest acceptable fix, or `null` if none yet" (`scan`).
  Degraded fixes, missing fixes, and availability loss never erase the last acceptable fix
  while the stream is active.
- **Sharing:** one upstream GMS subscription shared via
  `shareIn(applicationScope, WhileSubscribed(5 s), replay = 1)`. `replay = 1` gives late
  subscribers (export) the current best fix immediately; the 5 s stop-timeout survives UI
  churn such as configuration changes. The GMS callback channel is conflated — location is
  latest-wins data.
- **Permission recovery:** the upstream polls the permission checker (1 s interval) while
  denied, emitting `null`, and starts GMS updates once granted. Recovery therefore requires no
  re-collection, and `WhileSubscribed` restarts cannot mask it.
- **Permission revocation and the `SecurityException` race:** while GMS updates are active, a
  second 1 s poll re-verifies permission and, if it's gone, unregisters and re-enters the
  permission-await stage — the same path used for the initial-denial case, so the last
  acceptable fix is retained rather than erased. `requestLocationUpdates` throwing
  `SecurityException` (the TOCTOU race) is caught at the call site and handled identically: emit
  `null`, back off for the same 1 s interval, and let the outer loop retry via the permission-await
  stage instead of propagating the exception.
- **Crash defense-in-depth:** `@ApplicationScope` (`AppModule`) now carries a
  `CoroutineExceptionHandler` that logs via Timber, so any future uncaught exception in a
  `shareIn`-backed repository logs instead of crashing the app. This is a backstop, not a
  substitute for catching expected exceptions at their source.
- **Never block:** `scan`'s initial `null` is emitted immediately on subscription, so
  `first()`-style consumers always get a prompt value.

## Consequences

- Retention state resets when all subscribers leave for longer than the stop-timeout: the next
  subscription re-emits `null` and re-acquires. A stale fix from a previous session is not
  presented as current — acceptable for session-focused capture, and honest.
- Consumers cannot distinguish "no fix yet" from "permission denied" from this stream alone;
  permission UI state is owned by the permissions flow (issue #18). If a consumer ever needs
  the distinction, extend the stream to a sealed status type rather than adding a second
  permission check downstream.
- The application-scoped `CoroutineScope` (`@ApplicationScope` in `AppModule`) is a new DI
  seam backed by `Dispatchers.IO` (GMS binder calls and `delay`-based polling are sensor I/O,
  not CPU-bound work, per this project's own dispatcher convention); tests inject
  `TestScope.backgroundScope` for virtual-time control.
- Brief conflation window means a good fix immediately followed by a degraded one can collapse
  before `scan` sees the good fix — negligible at the 2 s update interval.
- Revocation detection has up to a 1 s lag (the poll interval), during which a stale fix could
  still be embedded at export time if the export happens in that window — acceptable given the
  same 1 s bound already applies to recovery, and export already tolerates missing/stale GPS by
  design.
