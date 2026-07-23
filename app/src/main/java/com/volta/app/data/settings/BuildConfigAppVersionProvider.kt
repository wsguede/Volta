package com.volta.app.data.settings

import com.volta.app.BuildConfig
import com.volta.app.domain.settings.AppVersionProvider
import javax.inject.Inject

class BuildConfigAppVersionProvider @Inject constructor() : AppVersionProvider {
    override val versionName: String = BuildConfig.VERSION_NAME
}
