package com.volta.app.domain.capture

interface BlurDetector {
    fun isSharp(frameData: ByteArray, width: Int, height: Int): Boolean
    fun sharpnessScore(frameData: ByteArray, width: Int, height: Int): Float
}
