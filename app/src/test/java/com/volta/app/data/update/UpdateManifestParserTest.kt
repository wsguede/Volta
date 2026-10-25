package com.volta.app.data.update

import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.update.UpdateManifest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric supplies the real org.json; android.jar's stubs would parse nothing (ADR 0019).
@RunWith(RobolectricTestRunner::class)
class UpdateManifestParserTest {

    private val parser = UpdateManifestParser()

    @Test
    fun `parses a valid manifest`() {
        val result = parser.parse(sample("manifest-valid.json"))

        assertThat(result.getOrThrow()).isEqualTo(VALID_MANIFEST)
    }

    @Test
    fun `ignores unknown extra fields`() {
        val result = parser.parse(sample("manifest-extra-fields.json"))

        assertThat(result.getOrThrow()).isEqualTo(VALID_MANIFEST)
    }

    @Test
    fun `fails on an unknown schema version`() {
        val result = parser.parse(sample("manifest-unknown-schema.json"))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fails on malformed json`() {
        val result = parser.parse(sample("manifest-malformed.json"))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fails when a required field is missing`() {
        val result = parser.parse(sample("manifest-missing-field.json"))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fails when apkUrl does not use https`() {
        val result = parser.parse(sample("manifest-http-apk-url.json"))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fails when schemaVersion is a string`() {
        val result = parser.parse(sample("manifest-string-schema.json"))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fails when schemaVersion is fractional`() {
        val json = sample(
            "manifest-valid.json"
        ).replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1.9,")

        assertThat(parser.parse(json).isFailure).isTrue()
    }

    @Test
    fun `fails when a required field is null`() {
        val result = parser.parse(sample("manifest-null-field.json"))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fails when content follows the json object`() {
        val result = parser.parse(sample("manifest-valid.json") + "{}")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `accepts trailing whitespace`() {
        val result = parser.parse(sample("manifest-valid.json") + "\n\n")

        assertThat(result.getOrThrow()).isEqualTo(VALID_MANIFEST)
    }

    @Test
    fun `fails when the document is not a json object`() {
        assertThat(parser.parse("[]").isFailure).isTrue()
        assertThat(parser.parse("").isFailure).isTrue()
    }

    @Test
    fun `fails when sha256 is not a 64 character hex digest`() {
        val json = sample("manifest-valid.json").replace("ab".repeat(32), "not-a-digest")

        assertThat(parser.parse(json).isFailure).isTrue()
    }

    private fun sample(name: String): String =
        checkNotNull(javaClass.getResource("/update/$name")) {
            "Missing test sample $name"
        }.readText()

    private companion object {
        val VALID_MANIFEST = UpdateManifest(
            schemaVersion = 1,
            versionCode = 202610002,
            versionName = "2026.10.2",
            minSdk = 30,
            sha256 = "ab".repeat(32),
            apkUrl = "https://github.com/wsguede/Volta/releases/download/" +
                "v2026.10.2/app-release.apk",
            notes = "Fixes stitching at the poles."
        )
    }
}
