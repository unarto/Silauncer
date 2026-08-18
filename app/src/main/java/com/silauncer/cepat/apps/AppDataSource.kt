package com.silauncer.cepat.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppDataSource(private val context: Context) {
    private val launcherApps: LauncherApps? by lazy {
        try {
            context.getSystemService(LauncherApps::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getInstalledApps(
        packageName: String? = null,
        user: UserHandle = Process.myUserHandle()
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val appsService = launcherApps ?: return@withContext emptyList()
            val activities = appsService.getActivityList(packageName, user) ?: return@withContext emptyList()
            activities.map { activity ->
                val component = activity.componentName
                AppInfo(
                    name = activity.label?.toString() ?: component.packageName,
                    componentName = component,
                    packageName = component.packageName,
                    user = user
                )
            }.distinctBy { it.componentName }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
