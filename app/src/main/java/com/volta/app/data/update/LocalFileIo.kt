package com.volta.app.data.update

import java.io.File
import java.io.IOException

/**
 * Runs [block] and rethrows its `IOException` as `IllegalStateException`. A plain `IOException`
 * means the device looks offline, so local file failures must not surface as one.
 */
internal inline fun <T> localFileIo(file: File, block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw IllegalStateException("Local file I/O failed for $file", e)
}
