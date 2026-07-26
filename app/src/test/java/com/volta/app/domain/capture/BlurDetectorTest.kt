package com.volta.app.domain.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val WIDTH = 5
private const val HEIGHT = 5

private fun flatImage(value: Int): ByteArray = ByteArray(WIDTH * HEIGHT) { value.toByte() }

private fun checkerboardImage(): ByteArray = ByteArray(WIDTH * HEIGHT) { i ->
    val x = i % WIDTH
    val y = i / WIDTH
    (if ((x + y) % 2 == 0) 255 else 0).toByte()
}

class BlurDetectorTest {

    private val detector = LaplacianBlurDetector()

    @Test
    fun `sharpness score is zero for a flat uniform image`() {
        val score = detector.sharpnessScore(flatImage(128), WIDTH, HEIGHT)

        assertThat(score).isEqualTo(0f)
    }

    @Test
    fun `sharpness score is higher for a high-contrast image than a flat image`() {
        val flatScore = detector.sharpnessScore(flatImage(128), WIDTH, HEIGHT)
        val checkerboardScore = detector.sharpnessScore(checkerboardImage(), WIDTH, HEIGHT)

        assertThat(checkerboardScore).isGreaterThan(flatScore)
    }

    @Test
    fun `isSharp is true when score meets the threshold`() {
        val sharp = LaplacianBlurDetector(threshold = 50f)

        assertThat(sharp.isSharp(50f)).isTrue()
        assertThat(sharp.isSharp(100f)).isTrue()
    }

    @Test
    fun `isSharp is false when score is below the threshold`() {
        val sharp = LaplacianBlurDetector(threshold = 50f)

        assertThat(sharp.isSharp(49.99f)).isFalse()
        assertThat(sharp.isSharp(0f)).isFalse()
    }
}
