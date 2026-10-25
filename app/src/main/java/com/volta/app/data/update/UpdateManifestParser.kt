package com.volta.app.data.update

import com.volta.app.domain.update.UpdateManifest
import java.net.URI
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses `update.json` (ADR 0018). Accepts only schema version 1 and ignores fields it does not
 * know, so new fields can be added to the manifest without a version bump.
 */
class UpdateManifestParser @Inject constructor() {

    fun parse(json: String): Result<UpdateManifest> = try {
        Result.success(parseObject(JSONObject(json)))
    } catch (e: JSONException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }

    private fun parseObject(json: JSONObject): UpdateManifest {
        val schemaVersion = json.getInt("schemaVersion")
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported schemaVersion $schemaVersion"
        }

        val sha256 = json.getString("sha256")
        require(SHA256_PATTERN.matches(sha256)) { "sha256 is not a SHA-256 hex digest" }

        val apkUrl = json.getString("apkUrl")
        require(isHttpsUrl(apkUrl)) { "apkUrl must use https" }

        return UpdateManifest(
            schemaVersion = schemaVersion,
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            minSdk = json.getInt("minSdk"),
            sha256 = sha256,
            apkUrl = apkUrl,
            notes = json.getString("notes")
        )
    }

    companion object {
        private const val SUPPORTED_SCHEMA_VERSION = 1
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")

        /** The host is not restricted: GitHub redirects release downloads to another host. */
        fun isHttpsUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
        }
    }
}
