package com.volta.app.di

import com.volta.app.data.update.BuildVersionDeviceSdkDataSource
import com.volta.app.domain.time.Clock
import com.volta.app.domain.update.DeviceSdkDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindDeviceSdkDataSource(impl: BuildVersionDeviceSdkDataSource): DeviceSdkDataSource

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock { System.currentTimeMillis() }
    }
}
