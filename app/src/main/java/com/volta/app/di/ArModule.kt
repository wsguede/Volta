package com.volta.app.di

import com.volta.app.data.ar.ArCameraRepository
import com.volta.app.data.ar.ArSessionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ArModule {

    @Binds
    @Singleton
    abstract fun bindArSessionManager(impl: ArCameraRepository): ArSessionManager
}
