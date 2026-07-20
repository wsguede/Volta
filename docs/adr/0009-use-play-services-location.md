# 0009 — Use Play Services Location for GPS

**Date:** 2026-06-28
**Status:** Accepted

## Context

Volta embeds GPS coordinates in exported photosphere EXIF metadata. Two location APIs are available on Android:
1. `android.location.LocationManager` — built-in, no dependencies, works on all Android devices.
2. Google Play Services `FusedLocationProviderClient` — fuses GPS, Wi-Fi, and cell signals for faster, more accurate fixes with lower battery drain.

## Decision

Use Google Play Services Location (`com.google.android.gms:play-services-location`) via `FusedLocationProviderClient` for all location acquisition.

## Consequences

- Faster time-to-first-fix: fused provider returns a location in seconds by combining GPS with network signals, critical for the capture session UX where users want to start immediately.
- Lower battery impact than raw GPS polling — the fused provider manages sensor duty-cycling automatically.
- Requires Google Play Services on the device. ARCore already requires Play Services, so this adds no new device compatibility constraint.
- GPS remains optional per the PRD — if Play Services is unavailable or the user denies permission, the app proceeds without coordinates and embeds no EXIF location data.
- The `GpsRepository` interface abstracts the provider, so swapping to `LocationManager` for Play Services-free builds is a single implementation change.
