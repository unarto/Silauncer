package com.silauncer.cepat.storage

import com.tencent.mmkv.MMKV

class LauncherPreferences {
    private val kv: MMKV = checkNotNull(MMKV.mmkvWithID("silauncer_launcher")) {
        "MMKV initialization failed for silauncer_launcher"
    }

    var gridColumns: Int
        get() = kv.decodeInt("grid_columns", 5)
        set(value) { kv.encode("grid_columns", value) }

    var gridRows: Int
        get() = kv.decodeInt("grid_rows", 6)
        set(value) { kv.encode("grid_rows", value) }

    var iconSize: Int
        get() = kv.decodeInt("icon_size", 56)
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
        get() = kv.decodeInt("icon_spacing", 4)
        set(value) { kv.encode("icon_spacing", value) }

    var hiddenApps: Set<String>
        get() = kv.decodeStringSet("hidden_apps", emptySet()) ?: emptySet()
        set(value) { kv.encode("hidden_apps", value) }

    var dragDropEnabled: Boolean
        get() = kv.decodeBool("drag_drop_enabled", true)
        set(value) { kv.encode("drag_drop_enabled", value) }

    var appOrder: List<String>
        get() = kv.decodeString("app_order", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        set(value) { kv.encode("app_order", value.joinToString(",")) }
}

