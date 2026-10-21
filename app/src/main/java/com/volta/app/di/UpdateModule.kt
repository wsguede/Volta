package com.volta.app.di

import com.volta.app.data.update.BuildVersionDeviceSdkDataSource
import com.volta.app.domain.settings.AppVersionDataSource
import com.volta.app.domain.settings.SettingsRepository
import com.volta.app.domain.time.Clock
import com.volta.app.domain.update.CheckForUpdateUseCase
import com.volta.app.domain.update.DeviceSdkDataSource
import com.volta.app.domain.update.UpdateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindDeviceSdkDataSource(impl: BuildVersionDeviceSdkDataSource): DeviceSdkDataSource

    companion object {
        // UpdateRepository is bound in #58; nothing injects this until then.
        @Provides
        fun provideCheckForUpdateUseCase(
            updateRepository: UpdateRepository,
            settingsRepository: SettingsRepository,
            appVersionDataSource: AppVersionDataSource,
            deviceSdkDataSource: DeviceSdkDataSource,
            clock: Clock,
            @IoDispatcher dispatcher: CoroutineDispatcher
        ): CheckForUpdateUseCase = CheckForUpdateUseCase(
            updateRepository = updateRepository,
            settingsRepository = settingsRepository,
            appVersionDataSource = appVersionDataSource,
            deviceSdkDataSource = deviceSdkDataSource,
            clock = clock,
            dispatcher = dispatcher
        )
    }
}
