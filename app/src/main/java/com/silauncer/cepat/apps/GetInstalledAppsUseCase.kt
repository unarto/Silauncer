package com.silauncer.cepat.apps

import android.content.Context
import android.os.Process
import android.os.UserHandle

class GetInstalledAppsUseCase(private val appDataSource: AppDataSource) {
    constructor(context: Context) : this(AppDataSource(context.applicationContext))

    suspend operator fun invoke(user: UserHandle = Process.myUserHandle()): List<AppInfo> {
        return appDataSource.getInstalledApps(null, user)
    }
}
