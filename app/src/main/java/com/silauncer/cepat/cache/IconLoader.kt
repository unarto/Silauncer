package com.silauncer.cepat.cache

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.silauncer.cepat.apps.AppInfo

object IconLoader {
    fun getIcon(context: Context, appInfo: AppInfo): Drawable {
        val cached = IconCache.get(appInfo.cacheKey)
        if (cached != null) {
            return cached
        }
        val pm = context.packageManager
        val icon = try {
            pm.getActivityIcon(appInfo.componentName)
        } catch (e: PackageManager.NameNotFoundException) {
            pm.defaultActivityIcon
        }
        
        IconCache.put(appInfo.cacheKey, icon)
        return icon
    }
}
