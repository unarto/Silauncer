package com.silauncer.cepat.cache

import android.graphics.drawable.Drawable
import android.util.LruCache

object IconCache {
    private val cache = LruCache<String, Drawable>(150)

    fun get(key: String): Drawable? {
        return cache.get(key)
    }

    fun put(key: String, drawable: Drawable) {
        cache.put(key, drawable)
    }

    fun removePackage(packageName: String) {
        val keysToRemove = cache.snapshot().keys.filter { key ->
            key.substringAfter(':').substringBefore('/') == packageName
        }
        for (key in keysToRemove) {
            cache.remove(key)
        }
    }

    fun clear() {
        cache.evictAll()
    }
}
