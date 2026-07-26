package com.volta.app.domain.capture

interface BlurDetector {
    fun sharpnessScore(frameData: ByteArray, width: Int, height: Int): Float
    fun isSharp(score: Float): Boolean
}
