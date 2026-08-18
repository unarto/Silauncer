package com.silauncer.cepat.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.silauncer.cepat.apps.AppRepository
import com.silauncer.cepat.storage.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object HiddenAppsDialog {
    fun show(context: Context, lifecycleScope: LifecycleCoroutineScope, prefs: LauncherPreferences) {
        lifecycleScope.launch {
            val repository = AppRepository(context)
            val apps = withContext(Dispatchers.IO) { repository.loadInitialApps() }
            
            if (context is Activity && (context.isFinishing || context.isDestroyed)) return@launch
            
            val appNames = apps.map { it.name }.toTypedArray()
            val packageNames = apps.map { it.componentName.packageName }.toTypedArray()
            val hiddenSet = prefs.hiddenApps.toMutableSet()
            
            val checkedItems = BooleanArray(apps.size) { i ->
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
}
