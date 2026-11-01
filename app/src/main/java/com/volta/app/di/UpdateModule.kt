package com.volta.app.di

import com.volta.app.data.update.ApkInspector
import com.volta.app.data.update.BuildVersionDeviceSdkDataSource
import com.volta.app.data.update.GitHubUpdateRepository
import com.volta.app.data.update.PackageManagerApkInspector
import com.volta.app.data.update.UpdateHttpClient
import com.volta.app.data.update.UrlConnectionUpdateHttpClient
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

    // UpdateInstaller is bound in #59.
    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: GitHubUpdateRepository): UpdateRepository

    @Binds
    abstract fun bindUpdateHttpClient(impl: UrlConnectionUpdateHttpClient): UpdateHttpClient

    @Binds
    abstract fun bindApkInspector(impl: PackageManagerApkInspector): ApkInspector

    companion object {
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
