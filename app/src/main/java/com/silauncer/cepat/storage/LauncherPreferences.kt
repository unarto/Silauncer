package com.silauncer.cepat.storage

import com.tencent.mmkv.MMKV

class LauncherPreferences {
    private val kv: MMKV = MMKV.mmkvWithID("silauncer_launcher")!!

    init {
        migrateFromOldPrefsIfNeeded()
    }

    private fun migrateFromOldPrefsIfNeeded() {
        if (kv.decodeBool("migrated_from_silauncer_prefs", false)) {
            return
        }
        val oldKv = MMKV.mmkvWithID("silauncer_prefs") ?: return
        if (oldKv.totalSize() == 0L) {
            kv.encode("migrated_from_silauncer_prefs", true)
            return
        }

        synchronized(LauncherPreferences::class.java) {
            if (kv.decodeBool("migrated_from_silauncer_prefs", false)) {
                return
            }
            if (oldKv.containsKey("grid_columns") && !kv.containsKey("grid_columns")) {
                kv.encode("grid_columns", oldKv.decodeInt("grid_columns", 5))
            }
            if (oldKv.containsKey("grid_rows") && !kv.containsKey("grid_rows")) {
                kv.encode("grid_rows", oldKv.decodeInt("grid_rows", 6))
            }
            if (oldKv.containsKey("icon_size") && !kv.containsKey("icon_size")) {
                kv.encode("icon_size", oldKv.decodeInt("icon_size", 48))
            }
            if (oldKv.containsKey("sort_mode") && !kv.containsKey("sort_mode")) {
                oldKv.decodeString("sort_mode", null)?.let { kv.encode("sort_mode", it) }
            }
            if (oldKv.containsKey("show_app_label") && !kv.containsKey("show_app_label")) {
                kv.encode("show_app_label", oldKv.decodeBool("show_app_label", true))
            }
            if (oldKv.containsKey("label_size") && !kv.containsKey("label_size")) {
                kv.encode("label_size", oldKv.decodeFloat("label_size", 12f))
            }
            if (oldKv.containsKey("icon_spacing") && !kv.containsKey("icon_spacing")) {
                kv.encode("icon_spacing", oldKv.decodeInt("icon_spacing", 8))
            }
            if (oldKv.containsKey("hidden_apps") && !kv.containsKey("hidden_apps")) {
                val hidden = oldKv.decodeStringSet("hidden_apps", null)
                if (hidden != null) {
                    kv.encode("hidden_apps", hidden)
                }
            }
            if (oldKv.containsKey("drag_drop_enabled") && !kv.containsKey("drag_drop_enabled")) {
                kv.encode("drag_drop_enabled", oldKv.decodeBool("drag_drop_enabled", true))
            }
            if (oldKv.containsKey("app_order") && !kv.containsKey("app_order")) {
                oldKv.decodeString("app_order", null)?.let { kv.encode("app_order", it) }
            }

            oldKv.clearAll()
            kv.encode("migrated_from_silauncer_prefs", true)
        }
    }

    var gridColumns: Int
        get() = kv.decodeInt("grid_columns", 5)
        set(value) { kv.encode("grid_columns", value) }

    var gridRows: Int
        get() = kv.decodeInt("grid_rows", 6)
        set(value) { kv.encode("grid_rows", value) }

    var iconSize: Int
        get() = kv.decodeInt("icon_size", 48)
        set(value) { kv.encode("icon_size", value) }

    var sortMode: String
        get() = kv.decodeString("sort_mode", "a_z") ?: "a_z"
        set(value) { kv.encode("sort_mode", value) }

    var showAppLabel: Boolean
        get() = kv.decodeBool("show_app_label", true)
        set(value) { kv.encode("show_app_label", value) }

    var labelSize: Float
        get() = kv.decodeFloat("label_size", 12f)
        set(value) { kv.encode("label_size", value) }

    var iconSpacing: Int
        get() = kv.decodeInt("icon_spacing", 8)
        set(value) { kv.encode("icon_spacing", value) }

    var hiddenApps: Set<String>
        get() = kv.decodeStringSet("hidden_apps", emptySet()) as? Set<String> ?: emptySet()
        set(value) { kv.encode("hidden_apps", value) }

    var dragDropEnabled: Boolean
        get() = kv.decodeBool("drag_drop_enabled", true)
        set(value) { kv.encode("drag_drop_enabled", value) }

    var appOrder: List<String>
        get() = kv.decodeString("app_order", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        set(value) { kv.encode("app_order", value.joinToString(",")) }
}

