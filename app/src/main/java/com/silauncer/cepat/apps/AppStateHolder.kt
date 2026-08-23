package com.silauncer.cepat.apps

import android.os.UserHandle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppStateHolder {
    private val apps = ArrayList<AppInfo>()
    private val mutex = Mutex()

    suspend fun getApps(): List<AppInfo> = mutex.withLock {
        apps.toList()
    }

    suspend fun setApps(newApps: List<AppInfo>) {
        mutex.withLock {
            apps.clear()
            val uniqueApps = newApps.distinctBy { it.cacheKey }
            apps.addAll(uniqueApps)
        }
    }

    suspend fun addApps(newApps: List<AppInfo>): List<AppInfo> {
        val added = ArrayList<AppInfo>()
        mutex.withLock {
            val existingKeys = apps.map { it.cacheKey }.toSet()
            val uniqueNewApps = newApps.distinctBy { it.cacheKey }.filter { it.cacheKey !in existingKeys }
            apps.addAll(uniqueNewApps)
            added.addAll(uniqueNewApps)
        }
        return added
    }

    suspend fun removePackage(packageName: String, user: UserHandle): Boolean = mutex.withLock {
        apps.removeAll {
            it.user == user && it.packageName == packageName
        }
    }
}
