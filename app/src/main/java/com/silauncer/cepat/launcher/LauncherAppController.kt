package com.silauncer.cepat.launcher

import android.content.Intent
import android.os.Process
import com.silauncer.cepat.apps.AppDataSource
import com.silauncer.cepat.apps.AppInfo
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
        val user = Process.myUserHandle()
        val installedApps = appDataSource.getInstalledApps(null, user)
        appStateHolder.setApps(installedApps)
        return getSortedVisibleApps()
    }

    suspend fun refreshApps(): List<AppInfo> {
        return getSortedVisibleApps()
    }

    suspend fun saveCustomAppOrder(visibleApps: List<AppInfo>) {
        val allApps = appStateHolder.getApps()
        val newOrder = calculateMergedOrder(allApps, visibleApps, prefs.appOrder)
        prefs.appOrder = newOrder
        if (prefs.sortMode != "custom") {
            prefs.sortMode = "custom"
        }
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
        val user = Process.myUserHandle()

        var changed = false
        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val newApps = appDataSource.getInstalledApps(packageName, user)
                val added = appStateHolder.addApps(newApps)
                if (added.isNotEmpty()) changed = true
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!replacing) {
                    val removed = appStateHolder.removePackage(packageName, user)
                    IconCache.removePackage(packageName)
                    
                    val currentHidden = prefs.hiddenApps
                    if (currentHidden.contains(packageName)) {
                        prefs.hiddenApps = currentHidden - packageName
                    }
                    
                    val currentOrder = prefs.appOrder
                    if (currentOrder.isNotEmpty()) {
                        val filteredOrder = currentOrder.filter { key ->
                            key.substringBefore('/') != packageName
                        }
                        if (filteredOrder.size != currentOrder.size) {
                            prefs.appOrder = filteredOrder
                        }
                    }
                    
                    changed = removed
                }
            }
            Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_REPLACED -> {
                val removed = appStateHolder.removePackage(packageName, user)
                val newApps = appDataSource.getInstalledApps(packageName, user)
                val updated = appStateHolder.addApps(newApps)
                IconCache.removePackage(packageName)
                changed = removed || updated.isNotEmpty()
            }
        }
        return changed
    }

    companion object {
        fun calculateMergedOrder(
            allApps: List<AppInfo>,
            visibleReordered: List<AppInfo>,
            currentSavedOrder: List<String>
        ): List<String> {
            val allInstalledKeys = allApps.map { it.componentName.flattenToString() }.toSet()
            val visibleKeys = visibleReordered.map { it.componentName.flattenToString() }
            val visibleKeySet = visibleKeys.toSet()

            val baseOrder = if (currentSavedOrder.isNotEmpty()) {
                val pruned = currentSavedOrder.filter { allInstalledKeys.contains(it) }
                val missing = allApps.map { it.componentName.flattenToString() }.filter { !pruned.contains(it) }
                pruned + missing
            } else {
                allApps.sortedBy { it.name.lowercase() }.map { it.componentName.flattenToString() }
            }

            var visibleIndex = 0
            val result = mutableListOf<String>()
            for (key in baseOrder) {
                if (visibleKeySet.contains(key)) {
                    if (visibleIndex < visibleKeys.size) {
                        result.add(visibleKeys[visibleIndex])
                        visibleIndex++
                    }
                } else {
                    result.add(key)
                }
            }
            while (visibleIndex < visibleKeys.size) {
                result.add(visibleKeys[visibleIndex])
                visibleIndex++
            }
            return result
        }
    }
}
