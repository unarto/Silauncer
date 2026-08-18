package com.silauncer.cepat.cache

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.silauncer.cepat.apps.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class IconLoader(private val scope: CoroutineScope) {
    private var defaultIcon: Drawable? = null
    
    // Menyimpan job loading yang sedang berjalan (In-Flight deduplication)
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Drawable>>()

    private fun getDefaultIcon(context: Context): Drawable {
        if (defaultIcon == null) {
            defaultIcon = context.packageManager.defaultActivityIcon
        }
        return defaultIcon!!
    }

    fun loadIconAsync(context: Context, appInfo: AppInfo, onLoaded: (Drawable, String) -> Unit) {
        val cacheKey = appInfo.cacheKey
        
        // 1. Cek Cache Memory (Cepat, sinkron)
        val cached = IconCache.get(cacheKey)
        if (cached != null) {
            onLoaded(cached, cacheKey)
            return
        }

        // 2. Placeholder instan agar view hasil recycle bersih
        onLoaded(getDefaultIcon(context), cacheKey)

        val appContext = context.applicationContext

        scope.launch {
            // 3. Gabung ke request in-flight yang ada, atau buat yang baru
            val deferred = inFlightRequests.computeIfAbsent(cacheKey) {
                scope.async(Dispatchers.IO) {
                    val pm = appContext.packageManager
                    val icon = try {
                        pm.getActivityIcon(appInfo.componentName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        pm.defaultActivityIcon
                    } catch (e: Exception) {
                        pm.defaultActivityIcon // Fallback jika OS bermasalah
                    }
                    
                    IconCache.put(cacheKey, icon)
                    icon
                }
            }

            try {
                // Tunggu request selesai (entah request baru atau numpang yang lama)
                val icon = deferred.await()
                withContext(Dispatchers.Main) {
                    onLoaded(icon, cacheKey)
                }
            } catch (e: Exception) {
                // Gagal, buang dari in-flight agar bisa di-retry nanti
            } finally {
                // Bersihkan in-flight map agar memory tidak bocor
                inFlightRequests.remove(cacheKey)
            }
        }
    }
}
