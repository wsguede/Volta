package com.volta.app.di

import android.opengl.GLSurfaceView
import com.volta.app.data.ar.ArCameraRepository
import com.volta.app.domain.ar.ArSessionManager
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

    // Exposed as the plain framework type (rather than ArCameraRepository) so ui/capture can
    // embed it in a GLSurfaceView without depending on the data/ layer — see ADR 0014.
    @Binds
    @Singleton
    abstract fun bindArCameraRenderer(impl: ArCameraRepository): GLSurfaceView.Renderer
}
