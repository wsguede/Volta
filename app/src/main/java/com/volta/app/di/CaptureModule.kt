package com.volta.app.di

import com.volta.app.domain.capture.DefaultFrameCaptureTrigger
import com.volta.app.domain.capture.FrameCaptureTrigger
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
    fun provideFrameCaptureTrigger(): FrameCaptureTrigger = DefaultFrameCaptureTrigger(
        onFrameDropped = { Timber.w("Frame store cap exceeded — dropping oldest frame") }
    )
}
