package com.silauncer.cepat.launcher

import android.content.Intent
import com.silauncer.cepat.apps.AppInfo
import com.silauncer.cepat.apps.AppDataSource
import com.silauncer.cepat.apps.AppStateHolder
import com.silauncer.cepat.apps.AppSorter
import com.silauncer.cepat.cache.IconCache
import com.silauncer.cepat.storage.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LauncherAppController(
    private val appDataSource: AppDataSource,
    private val appStateHolder: AppStateHolder,
    private val prefs: LauncherPreferences
) {
    suspend fun loadAppsInitial(): List<AppInfo> {
        val user = android.os.Process.myUserHandle()
        val activities = appDataSource.getActivities(null, user)
        appStateHolder.resetApps(activities, user)
        return getSortedVisibleApps()
    }

    suspend fun refreshApps(): List<AppInfo> {
        return getSortedVisibleApps()
    }
    
    private suspend fun getSortedVisibleApps(): List<AppInfo> {
        val apps = appStateHolder.getApps()
        return withContext(Dispatchers.Default) {
            val hidden = prefs.hiddenApps
            val visibleApps = apps.filter { !hidden.contains(it.componentName.packageName) }
            AppSorter.sort(visibleApps, prefs.sortMode, prefs.appOrder)
        }
    }

    suspend fun handlePackageEvent(action: String?, packageName: String?, replacing: Boolean): Boolean {
        if (action == null || packageName == null) return false
        val user = android.os.Process.myUserHandle()
        
        var changed = false
        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val activities = appDataSource.getActivities(packageName, user)
                val added = appStateHolder.addActivities(activities, user)
                if (added.isNotEmpty()) changed = true
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!replacing) {
                    appStateHolder.removePackage(packageName, user)
                    IconCache.removePackage(packageName)
                    changed = true
                }
            }
            Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_REPLACED -> {
                appStateHolder.removePackage(packageName, user)
                val activities = appDataSource.getActivities(packageName, user)
                val updated = appStateHolder.addActivities(activities, user)
                IconCache.removePackage(packageName)
                if (updated.isNotEmpty()) changed = true
            }
        }
        return changed
    }
}
