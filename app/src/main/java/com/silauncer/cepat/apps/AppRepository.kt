package com.silauncer.cepat.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle

/**
 * Repository aplikasi untuk Home Screen Silauncer.
 *
 * Mengambil konsep AllAppsList Launcher3:
 * - satu sumber data aplikasi
 * - add/remove/update per package
 * - tidak melakukan full reload setiap onResume()
 *
 * Icon dikelola terpisah oleh IconCache.
 */
class AppRepository(
    private val context: Context
) {

    private val launcherApps =
        context.getSystemService(LauncherApps::class.java)

    private val apps = ArrayList<AppInfo>()

    @Synchronized
    fun getApps(): List<AppInfo> {
        return apps.toList()
    }

    @Synchronized
    fun loadInitialApps(): List<AppInfo> {
        apps.clear()

        val user = android.os.Process.myUserHandle()
        val activities = launcherApps.getActivityList(null, user)

        for (activity in activities) {
            addActivity(activity, user)
        }
        
        return getApps()
    }

    @Synchronized
    fun addPackage(
        packageName: String,
        user: UserHandle
    ): List<AppInfo> {

        val activities =
            launcherApps.getActivityList(packageName, user)

        val added = ArrayList<AppInfo>()

        for (activity in activities) {
            val app = addActivity(activity, user)

            if (app != null) {
                added.add(app)
            }
        }

        return added
    }

    @Synchronized
    fun removePackage(
        packageName: String,
        user: UserHandle
    ) {
        apps.removeAll {
            it.user == user &&
                it.packageName == packageName
        }
    }

    @Synchronized
    fun updatePackage(
        packageName: String,
        user: UserHandle
    ): List<AppInfo> {

        removePackage(packageName, user)

        return addPackage(packageName, user)
    }

    private fun addActivity(
        activity: LauncherActivityInfo,
        user: UserHandle
    ): AppInfo? {

        val component = activity.componentName

        if (apps.any {
                it.componentName == component &&
                    it.user == user
            }) {
            return null
        }

        val app = AppInfo(
            name = activity.label?.toString()
                ?: component.packageName,
            componentName = component,
            packageName = component.packageName,
            user = user
        )

        apps.add(app)

        return app
    }


}
