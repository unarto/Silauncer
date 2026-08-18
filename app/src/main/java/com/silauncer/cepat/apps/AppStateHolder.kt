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
            for (app in newApps) {
                if (apps.none { it.componentName == app.componentName && it.user == app.user }) {
                    apps.add(app)
                }
            }
        }
    }

    suspend fun addApps(newApps: List<AppInfo>): List<AppInfo> {
        val added = ArrayList<AppInfo>()
        mutex.withLock {
            for (app in newApps) {
                if (apps.none { it.componentName == app.componentName && it.user == app.user }) {
                    apps.add(app)
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
}
