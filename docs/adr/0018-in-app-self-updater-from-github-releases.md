# 0018 — Self-Update from GitHub Releases via an `update.json` Manifest and `PackageInstaller`

**Date:** 2026-10-16
**Status:** Accepted

## Context

Every push to `main` publishes a signed `app-release.apk` to GitHub Releases (`release.yml`), but
an installed Volta has no way to learn about or install a newer build — updating means manually
downloading the APK. The release pipeline already satisfies Android's two preconditions for an
in-place update: every release is signed with the same keystore, and `versionCode`
(`generateVersionCode()` in `app/build.gradle.kts`) increases monotonically.

Alternatives considered:

- **Google Play (internal track) + Play In-App Updates.** True background auto-update, but
  requires a Play Console account, an AAB pipeline, and a Play Core dependency, for an app that is
  distributed only through GitHub today.
- **Third-party updater (Obtainium).** Zero code, but lives outside the app and depends on each
  user installing and configuring it.
- **GitHub REST API (`/releases/latest`) as the update source.** No CI change, but couples the app
  to GitHub's JSON shape and the `vYYYY.M.MICRO` tag format, and is rate-limited to 60
  unauthenticated requests/hour per IP.
- **`DownloadManager` for the APK.** Survives process death and gives a system notification for
  free, but writes to shared/external storage and reports progress awkwardly into Compose state.
- **`ACTION_VIEW`/`ACTION_INSTALL_PACKAGE` intent install.** Simpler, but deprecated, gives no
  install-status callback, and can never skip the confirmation prompt.

## Decision

Build a self-updater with no new dependencies:

- **Manifest.** `release.yml` uploads an `update.json` asset next to the APK containing
  `versionCode`, `versionName`, `sha256`, `minSdk`, and release notes, sourced from Gradle's
  `output-metadata.json` and `sha256sum`. The app fetches
  `https://github.com/wsguede/Volta/releases/latest/download/update.json` — a plain asset redirect,
  not the rate-limited API — and parses it with the framework's `org.json`.
  The app accepts only `schemaVersion` 1 and ignores fields it does not recognize, so new fields
  can be added without changing the version. Raise `schemaVersion` only for a breaking change,
  knowing that every installed build that does not understand it stops seeing updates.
  The fields the app knows are required and strictly typed: `schemaVersion`, `versionCode`, and
  `minSdk` are integers, the rest are strings, and `sha256` is 64 hex characters. The body must be
  a single JSON object of at most 64 KiB. An additive change may add fields, but must not remove
  a known field or change its type; doing so silently stops updates for every installed build.
- **When to check.** On app launch, at most once per 24 h (last-check timestamp in DataStore,
  ADR 0011), plus a manual "Check for updates" action in Settings. Auto-check is **on by default**
  with an opt-out toggle in Settings. The updater is **disabled in debug builds**: they are signed
  with the debug key (an install would fail the signature check), and their date-derived
  `versionName` would compare as newer than older release builds.
  Any successful check, manual ones included, restarts the 24 h window. A last-check timestamp in
  the future (the wall clock moved backwards) counts as due, so a bad timestamp can't block checks
  for days. If saving the timestamp fails, the window does not restart and the next launch checks
  again.
- **Surfacing.** A badge on the Settings entry point and an "Update to X" row in Settings with the
  release notes and an Install button. Never a dialog or banner — the update flow must not
  interrupt capture, stitching, or export.
- **Download.** `HttpURLConnection` on the injected IO dispatcher into `cacheDir/updates/`, only
  after the user taps Install. The repository returns progress as a cold `Flow`; the Settings
  ViewModel exposes it to the UI as `StateFlow`. `apkUrl` must use `https`, and so must the final
  URL after redirects; the host is not restricted, because GitHub redirects release downloads to
  another host. The file is named after the manifest's `versionCode`. Downloads run one at a
  time, and `cacheDir/updates/` is emptied before each one, so a leftover file is never mistaken
  for the new APK. The next download replaces the verified file, so the caller installs it before
  starting another download.
- **Verify.** SHA-256 of the file must match `update.json`. `PackageManager.getPackageArchiveInfo`
  must report the installed app's package name and the manifest's `versionCode`, and that
  `versionCode` must be greater than the installed one. Android's own signature check rejects any
  APK not signed with the release key. A download stops above 512 MiB, or above the length the
  server announced, so a bad response can't fill the disk.
  Download and verification failures surface to the UI as one message with a Retry action; the
  failure cause is logged but not shown.
- **Install.** `PackageInstaller` session API with a `PendingIntent` status receiver; on
  `STATUS_PENDING_USER_ACTION` the confirmation intent is launched. On API 31+ the session requests
  `USER_ACTION_NOT_REQUIRED`: the first in-app update (Volta was installed by a browser/adb)
  still prompts, but once Volta has installed itself it is the installer of record and later
  updates can complete without a prompt.
- **Cleanup.** The downloaded APK is deleted on success, failure, or cancellation, and on
  `MY_PACKAGE_REPLACED`, so the app keeps no lasting files.

Layering follows ADR 0001: `domain/update/` holds the version comparison, manifest model, and
check policy (throttle, opt-out, debug guard) as pure Kotlin; `data/update/` holds the HTTP
fetcher, downloader, and `PackageInstaller` wrapper; the Settings screen consumes the result.
The fetch and download boundary (`UpdateRepository`) and the install boundary (`UpdateInstaller`)
are separate domain interfaces, so each is implemented and bound on its own.

## Consequences

- Volta's first network access: adds `INTERNET` and `REQUEST_INSTALL_PACKAGES` to the manifest,
  and the user must grant "Install unknown apps" for Volta once. Capture and export stay fully
  offline; the updater is the only network caller and fails silently when offline.
- Each check reveals the device's IP to GitHub, which is why the opt-out exists.
- Release-pipeline coupling moves from tag format to `update.json`'s schema — changes to it must
  stay backward compatible with already-installed versions, or old builds stop seeing updates.
  Each `update.json` must also describe the exact APK it ships with: a `versionCode` that differs
  from the APK's fails verification, so that release can't be installed.
- Silent installs depend on platform installer-of-record rules that tighten over Android releases
  (e.g., minimum `targetSdk` for the installer); they need verification on a physical device, and
  the prompted path must remain the fallback.
- Android kills the process during a self-update and does not relaunch it; the user reopens Volta
  manually.
- Rotating the release keystore would break the update path for every installed copy.
