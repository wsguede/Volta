package com.volta.app.di

import com.volta.app.domain.capture.BlurDetector
import com.volta.app.domain.capture.DefaultFrameCaptureTrigger
import com.volta.app.domain.capture.FrameCaptureTrigger
import com.volta.app.domain.capture.LaplacianBlurDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {

    @Provides
    @Singleton
    fun provideBlurDetector(): BlurDetector = LaplacianBlurDetector()

    @Provides
    @Singleton
    fun provideFrameCaptureTrigger(blurDetector: BlurDetector): FrameCaptureTrigger =
        DefaultFrameCaptureTrigger(
            blurDetector = blurDetector,
            onFrameDropped = { Timber.w("Frame store cap exceeded — dropping oldest frame") }
        )
}
