package com.volta.app.domain.update

/**
 * The v1 `update.json` manifest published with each GitHub release (see
 * `.github/scripts/generate-update-manifest.sh` and ADR 0018).
 */
data class UpdateManifest(
    val schemaVersion: Int,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val sha256: String,
    val apkUrl: String,
    val notes: String
)
