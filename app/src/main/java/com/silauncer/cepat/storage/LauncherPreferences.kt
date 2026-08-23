package com.silauncer.cepat.storage

import android.util.Log
import com.tencent.mmkv.MMKV

/**
 * Preferences wrapper using MMKV for high-performance persistent storage.
 *
 * Handles all launcher configuration and app ordering with thread-safe operations.
 * Uses lazy initialization to ensure MMKV is ready before access.
 */
class LauncherPreferences {
    
    companion object {
        private const val TAG = "LauncherPreferences"
        private const val MMKV_ID = "silauncer_launcher"
        
        // Default values
        private const val DEFAULT_GRID_COLUMNS = 5
        private const val DEFAULT_GRID_ROWS = 6
        private const val DEFAULT_ICON_SIZE = 56
        private const val DEFAULT_SORT_MODE = "a_z"
        private const val DEFAULT_SHOW_LABEL = true
        private const val DEFAULT_LABEL_SIZE = 12f
        private const val DEFAULT_ICON_SPACING = 4
        
        // Key constants
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_GRID_ROWS = "grid_rows"
        private const val KEY_ICON_SIZE = "icon_size"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_SHOW_LABEL = "show_app_label"
        private const val KEY_LABEL_SIZE = "label_size"
        private const val KEY_ICON_SPACING = "icon_spacing"
        private const val KEY_HIDDEN_APPS = "hidden_apps"
        private const val KEY_APP_ORDER = "app_order"
    }

    /**
     * Lazy initialization of MMKV instance.
     * FIX: Uses lazy delegate instead of checkNotNull to prevent crash on recreation.
     * This ensures MMKV is only initialized when first accessed.
     */
    private val kv: MMKV by lazy {
        MMKV.mmkvWithID(MMKV_ID)
            ?.also { Log.d(TAG, "MMKV initialized successfully") }
            ?: run {
                Log.e(TAG, "MMKV initialization failed")
                throw IllegalStateException("MMKV initialization failed for $MMKV_ID")
            }
    }

    var gridColumns: Int
        get() = kv.decodeInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)
        set(value) { kv.encode(KEY_GRID_COLUMNS, value) }

    var gridRows: Int
        get() = kv.decodeInt(KEY_GRID_ROWS, DEFAULT_GRID_ROWS)
        set(value) { kv.encode(KEY_GRID_ROWS, value) }

    var iconSize: Int
        get() = kv.decodeInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE)
        set(value) { kv.encode(KEY_ICON_SIZE, value) }

    var sortMode: String
        get() = kv.decodeString(KEY_SORT_MODE, DEFAULT_SORT_MODE) ?: DEFAULT_SORT_MODE
        set(value) { kv.encode(KEY_SORT_MODE, value) }

    var showAppLabel: Boolean
        get() = kv.decodeBool(KEY_SHOW_LABEL, DEFAULT_SHOW_LABEL)
        set(value) { kv.encode(KEY_SHOW_LABEL, value) }

    var labelSize: Float
        get() = kv.decodeFloat(KEY_LABEL_SIZE, DEFAULT_LABEL_SIZE)
        set(value) { kv.encode(KEY_LABEL_SIZE, value) }

    var iconSpacing: Int
        get() = kv.decodeInt(KEY_ICON_SPACING, DEFAULT_ICON_SPACING)
        set(value) { kv.encode(KEY_ICON_SPACING, value) }

    var hiddenApps: Set<String>
        get() = kv.decodeStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
        set(value) { kv.encode(KEY_HIDDEN_APPS, value) }

    /**
     * FIX: Improved app order parsing with better null safety.
     * Handles edge cases where stored value might be null or empty.
     */
    var appOrder: List<String>
        get() {
            val encoded = kv.decodeString(KEY_APP_ORDER, "") ?: ""
            return if (encoded.isEmpty()) {
                emptyList()
            } else {
                encoded.split(",").filter { it.isNotEmpty() }
            }
        }
        set(value) { kv.encode(KEY_APP_ORDER, value.joinToString(",")) }
}
