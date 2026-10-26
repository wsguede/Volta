package com.volta.app.data.update

import com.volta.app.domain.update.UpdateManifest
import java.net.URI
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Parses `update.json` (ADR 0018). Accepts only schema version 1 and ignores fields it does not
 * know, so new fields can be added to the manifest without a version bump.
 *
 * Known fields are type-checked: `org.json`'s getters would otherwise accept `"1"` or `1.9` as the
 * integer 1, and turn `null` into the string `"null"`.
 */
class UpdateManifestParser @Inject constructor() {

    fun parse(json: String): Result<UpdateManifest> = try {
        Result.success(parseObject(readObject(json)))
    } catch (e: JSONException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }

    private fun readObject(json: String): JSONObject {
        val tokener = JSONTokener(json)
        val value = tokener.nextValue()
        require(value is JSONObject) { "Update manifest is not a JSON object" }
        // JSONObject(String) ignores anything after the closing brace; a truncated or
        // concatenated response should fail instead.
        require(tokener.nextClean() == END_OF_INPUT) { "Unexpected content after the JSON object" }
        return value
    }

    private fun parseObject(json: JSONObject): UpdateManifest {
        val schemaVersion = json.requireInt("schemaVersion")
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported schemaVersion $schemaVersion"
        }

        val sha256 = json.requireString("sha256")
        require(SHA256_PATTERN.matches(sha256)) { "sha256 is not a SHA-256 hex digest" }

        val apkUrl = json.requireString("apkUrl")
        require(isHttpsUrl(apkUrl)) { "apkUrl must use https" }

        return UpdateManifest(
            schemaVersion = schemaVersion,
            versionCode = json.requireInt("versionCode"),
            versionName = json.requireString("versionName"),
            minSdk = json.requireInt("minSdk"),
            sha256 = sha256,
            apkUrl = apkUrl,
            notes = json.requireString("notes")
        )
    }

    private fun JSONObject.requireInt(name: String): Int {
        val value = opt(name)
        require(value is Int) { "$name must be an integer" }
        return value
    }

    private fun JSONObject.requireString(name: String): String {
        val value = opt(name)
        require(value is String) { "$name must be a string" }
        return value
    }

    companion object {
        private const val SUPPORTED_SCHEMA_VERSION = 1
        private const val END_OF_INPUT = '\u0000'
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")

        /** The host is not restricted: GitHub redirects release downloads to another host. */
        fun isHttpsUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
        }
    }
}
