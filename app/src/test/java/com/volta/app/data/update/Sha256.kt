package com.volta.app.data.update

import java.security.MessageDigest

/** Lowercase hex SHA-256 of [bytes], as written to `update.json`. */
fun sha256Of(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
