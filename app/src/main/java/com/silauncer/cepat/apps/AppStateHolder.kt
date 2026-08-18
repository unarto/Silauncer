package com.silauncer.cepat.apps

import android.content.pm.LauncherActivityInfo
import android.os.UserHandle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppStateHolder {
    private val apps = ArrayList<AppInfo>()
    private val mutex = Mutex()

    suspend fun getApps(): List<AppInfo> = mutex.withLock {
        apps.toList()
    }

    suspend fun resetApps(activities: List<LauncherActivityInfo>, user: UserHandle) {
        mutex.withLock {
            apps.clear()
            for (activity in activities) {
                addActivityLocked(activity, user)
            }
        }
    }

    suspend fun addActivities(activities: List<LauncherActivityInfo>, user: UserHandle): List<AppInfo> {
        val added = ArrayList<AppInfo>()
        mutex.withLock {
            for (activity in activities) {
                val app = addActivityLocked(activity, user)
                if (app != null) {
                    added.add(app)
                }
            }
        }
        return added
    }

    suspend fun removePackage(packageName: String, user: UserHandle) {
        mutex.withLock {
            apps.removeAll {
                it.user == user && it.packageName == packageName
            }
        }
    }

    private fun addActivityLocked(
        activity: LauncherActivityInfo,
        user: UserHandle
    ): AppInfo? {
        val component = activity.componentName
        if (apps.any {
                it.componentName == component && it.user == user
            }) {
            return null
        }
        val app = AppInfo(
            name = activity.label?.toString() ?: component.packageName,
            componentName = component,
            packageName = component.packageName,
            user = user
        )
        apps.add(app)
        return app
    }
}
