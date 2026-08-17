package com.silauncer.cepat.launcher

import android.content.Intent
import com.silauncer.cepat.apps.AppInfo
import com.silauncer.cepat.apps.AppRepository
import com.silauncer.cepat.apps.AppSorter
import com.silauncer.cepat.cache.IconCache
import com.silauncer.cepat.storage.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LauncherAppController(
    private val appRepository: AppRepository,
    private val prefs: LauncherPreferences
) {

    suspend fun loadAppsInitial(): List<AppInfo> {
        val apps = withContext(Dispatchers.IO) {
            appRepository.loadInitialApps()
        }
        return withContext(Dispatchers.Default) {
            val hidden = prefs.hiddenApps
            val visibleApps = apps.filter { !hidden.contains(it.componentName.packageName) }
            AppSorter.sort(visibleApps, prefs.sortMode, prefs.appOrder)
        }
    }

    suspend fun refreshApps(): List<AppInfo> {
        val apps = appRepository.getApps()
        return withContext(Dispatchers.Default) {
            val hidden = prefs.hiddenApps
            val visibleApps = apps.filter { !hidden.contains(it.componentName.packageName) }
            AppSorter.sort(visibleApps, prefs.sortMode, prefs.appOrder)
        }
    }

    fun handlePackageEvent(action: String?, packageName: String?, replacing: Boolean): Boolean {
        if (action == null || packageName == null) return false
        val user = android.os.Process.myUserHandle()
        
        var changed = false
        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val added = appRepository.addPackage(packageName, user)
                if (added.isNotEmpty()) changed = true
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!replacing) {
                    appRepository.removePackage(packageName, user)
                    IconCache.removePackage(packageName)
                    changed = true
                }
            }
            Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_REPLACED -> {
                val updated = appRepository.updatePackage(packageName, user)
                IconCache.removePackage(packageName)
                if (updated.isNotEmpty()) changed = true
            }
        }
        return changed
    }
}
