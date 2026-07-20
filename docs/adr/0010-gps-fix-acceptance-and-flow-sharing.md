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
  seam; tests inject `TestScope.backgroundScope` for virtual-time control.
- Brief conflation window means a good fix immediately followed by a degraded one can collapse
  before `scan` sees the good fix — negligible at the 2 s update interval.
