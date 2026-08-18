package com.silauncer.cepat.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppDataSource(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    suspend fun getInstalledApps(
        packageName: String? = null,
        user: UserHandle = Process.myUserHandle()
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        val activities = launcherApps.getActivityList(packageName, user)
        activities.map { activity ->
            val component = activity.componentName
            AppInfo(
                name = activity.label?.toString() ?: component.packageName,
                componentName = component,
                packageName = component.packageName,
                user = user
            )
        }.distinctBy { it.componentName }
    }
}
