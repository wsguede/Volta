package com.volta.app.di

import com.volta.app.data.export.MediaStorePhotoExporter
import com.volta.app.data.export.PhotoExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindPhotoExporter(impl: MediaStorePhotoExporter): PhotoExporter
}
