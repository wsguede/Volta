package com.volta.app.di

import com.volta.app.domain.coverage.CoverageTracker
import com.volta.app.domain.coverage.SphereCoverageTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoverageModule {

    @Provides
    @Singleton
    fun provideCoverageTracker(): CoverageTracker = SphereCoverageTracker()
}
