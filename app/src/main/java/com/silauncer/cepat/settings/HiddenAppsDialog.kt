package com.silauncer.cepat.settings

import android.app.AlertDialog
import android.content.Context
import com.silauncer.cepat.apps.AppInfo
import com.silauncer.cepat.storage.LauncherPreferences

object HiddenAppsDialog {
    fun show(context: Context, apps: List<AppInfo>, prefs: LauncherPreferences) {
        val sortedApps = apps.sortedBy { it.name.lowercase() }
        val appNames = sortedApps.map { it.name }.toTypedArray()
        val packageNames = sortedApps.map { it.componentName.packageName }.toTypedArray()
        val hiddenSet = prefs.hiddenApps.toMutableSet()
        
        val checkedItems = BooleanArray(sortedApps.size) { i ->
            hiddenSet.contains(packageNames[i])
        }
        
        AlertDialog.Builder(context)
            .setTitle("Select Apps to Hide")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    hiddenSet.add(packageNames[which])
                } else {
                    hiddenSet.remove(packageNames[which])
                }
            }
            .setPositiveButton("Save") { _, _ ->
                prefs.hiddenApps = hiddenSet
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
