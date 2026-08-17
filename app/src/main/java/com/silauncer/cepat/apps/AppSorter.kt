package com.silauncer.cepat.apps

object AppSorter {
    fun sort(apps: List<AppInfo>, sortMode: String, customOrder: List<String> = emptyList()): List<AppInfo> {
        return when (sortMode) {
            "z_a" -> apps.sortedByDescending { it.name.lowercase() }
            "custom" -> {
                val orderMap = customOrder.withIndex().associate { it.value to it.index }
                apps.sortedWith(compareBy({ orderMap[it.componentName.flattenToString()] ?: Int.MAX_VALUE }, { it.name.lowercase() }))
            }
            else -> apps.sortedBy { it.name.lowercase() }
        }
    }
}
