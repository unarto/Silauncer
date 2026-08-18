package com.silauncer.cepat.apps

import android.os.UserHandle

class GetInstalledAppsUseCase(private val appDataSource: AppDataSource) {
    suspend operator fun invoke(user: UserHandle = android.os.Process.myUserHandle()): List<AppInfo> {
        val activities = appDataSource.getActivities(null, user)
        return activities.map { activity ->
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
