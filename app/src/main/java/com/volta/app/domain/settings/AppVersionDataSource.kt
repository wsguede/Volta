package com.volta.app.domain.settings

interface AppVersionDataSource {
    val versionName: String
    val versionCode: Int
    val isDebugBuild: Boolean
}
