package com.volta.app.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class PackageManagerApkInspector @Inject constructor(
    @ApplicationContext private val context: Context
) : ApkInspector {

    private val packageManager: PackageManager get() = context.packageManager

    override val installedPackage: ApkPackageInfo
        get() = packageInfo(context.packageName).toApkPackageInfo()

    override fun inspectArchive(apk: File): ApkPackageInfo? =
        archiveInfo(apk.absolutePath)?.toApkPackageInfo()

    private fun packageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }

    private fun archiveInfo(path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(path, 0)
        }

    private fun PackageInfo.toApkPackageInfo() = ApkPackageInfo(packageName, longVersionCode)
}
