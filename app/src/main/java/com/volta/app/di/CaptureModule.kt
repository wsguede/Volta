package com.volta.app.di

import com.volta.app.domain.capture.DefaultFrameCaptureTrigger
import com.volta.app.domain.capture.FrameCaptureTrigger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {

    @Provides
    @Singleton
    fun provideFrameCaptureTrigger(): FrameCaptureTrigger = DefaultFrameCaptureTrigger()
}
