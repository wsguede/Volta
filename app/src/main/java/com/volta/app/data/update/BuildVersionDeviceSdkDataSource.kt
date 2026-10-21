package com.volta.app.data.update

import android.os.Build
import com.volta.app.domain.update.DeviceSdkDataSource
import javax.inject.Inject

class BuildVersionDeviceSdkDataSource @Inject constructor() : DeviceSdkDataSource {
    override val sdkInt: Int = Build.VERSION.SDK_INT
}
