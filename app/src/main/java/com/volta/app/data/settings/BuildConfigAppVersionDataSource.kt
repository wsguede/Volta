package com.volta.app.data.settings

import com.volta.app.BuildConfig
import com.volta.app.domain.settings.AppVersionDataSource
import javax.inject.Inject

class BuildConfigAppVersionDataSource @Inject constructor() : AppVersionDataSource {
    override val versionName: String = BuildConfig.VERSION_NAME
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val isDebugBuild: Boolean = BuildConfig.DEBUG
}
