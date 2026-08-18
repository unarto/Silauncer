package com.silauncer.cepat.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppDataSource(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    suspend fun getActivities(packageName: String?, user: UserHandle): List<LauncherActivityInfo> =
        withContext(Dispatchers.IO) {
            launcherApps.getActivityList(packageName, user)
        }
}
