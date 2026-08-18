package com.silauncer.cepat.settings

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.app.AlertDialog
import com.silauncer.cepat.apps.GetInstalledAppsUseCase
import com.silauncer.cepat.storage.LauncherPreferences

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: LauncherPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        try {
            prefs = LauncherPreferences()

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 64, 48, 100)
            }

            layout.addView(SettingsUi.createTitle(this, "Launcher Settings"))

            setupGridSpinner(layout)
            setupIconSizeSpinner(layout)
            setupSortSpinner(layout)
            setupShowLabelSpinner(layout)
            setupLabelSizeSpinner(layout)
            setupIconSpacingSpinner(layout)
            setupHiddenAppsButton(layout)
            setupDragDropSpinner(layout)
            setupResetButton(layout)

            val scrollView = ScrollView(this)
            scrollView.addView(layout)
            setContentView(scrollView)
        } catch (e: Exception) {
            android.util.Log.e("SILAUNCER", "SETTINGS CRASH: " + e.message, e)
            android.widget.Toast.makeText(this, "SETTINGS CRASH: " + e.message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun setupGridSpinner(layout: LinearLayout) {
        val currentGridStr = "${prefs.gridColumns}x${prefs.gridRows}"
        SettingsUi.addSpinnerSetting(
            this, layout, "Grid Layout",
            arrayOf("4x4", "4x5", "5x5", "5x6", "6x6"),
            currentGridStr,
            defaultFallbackIndex = 3, // default 5x6
            paddingTop = 0
        ) { selected ->
            val parts = selected.split("x")
            if (parts.size == 2) {
                prefs.gridColumns = parts[0].toIntOrNull() ?: 5
                prefs.gridRows = parts[1].toIntOrNull() ?: 6
            }
        }
    }

    private fun setupIconSizeSpinner(layout: LinearLayout) {
        SettingsUi.addSpinnerSetting(
            this, layout, "Icon Size (dp)",
            arrayOf(32, 48, 64, 72),
            prefs.iconSize
        ) { selected ->
            prefs.iconSize = selected
        }
    }

    private fun setupSortSpinner(layout: LinearLayout) {
        SettingsUi.addSpinnerSetting(
            this, layout, "Sorting Mode",
            arrayOf("a_z", "z_a", "custom"),
            prefs.sortMode
        ) { selected ->
            prefs.sortMode = selected
        }
    }

    private fun setupShowLabelSpinner(layout: LinearLayout) {
        SettingsUi.addSpinnerSetting(
            this, layout, "Show App Labels",
            arrayOf("Yes", "No"),
            if (prefs.showAppLabel) "Yes" else "No"
        ) { selected ->
            prefs.showAppLabel = selected == "Yes"
        }
    }

    private fun setupLabelSizeSpinner(layout: LinearLayout) {
        SettingsUi.addSpinnerSetting(
            this, layout, "Label Size (sp)",
            arrayOf(10f, 12f, 14f, 16f),
            prefs.labelSize
        ) { selected ->
            prefs.labelSize = selected
        }
    }

    private fun setupIconSpacingSpinner(layout: LinearLayout) {
        SettingsUi.addSpinnerSetting(
            this, layout, "Icon Spacing (dp)",
            arrayOf(4, 8, 12, 16, 24),
            prefs.iconSpacing
        ) { selected ->
            prefs.iconSpacing = selected
        }
    }

    private fun setupHiddenAppsButton(layout: LinearLayout) {
        val hiddenAppsBtn = Button(this).apply {
            text = "Manage Hidden Apps"
            setOnClickListener {
                lifecycleScope.launch {
                    val useCase = GetInstalledAppsUseCase(this@SettingsActivity)
                    val apps = useCase()
                    if (!isFinishing && !isDestroyed) {
                        HiddenAppsDialog.show(this@SettingsActivity, apps, prefs)
                    }
                }
            }
        }
        layout.addView(hiddenAppsBtn)
    }

    private fun setupDragDropSpinner(layout: LinearLayout) {
        SettingsUi.addSpinnerSetting(
            this, layout, "Drag & Drop",
            arrayOf("Enabled", "Disabled"),
            if (prefs.dragDropEnabled) "Enabled" else "Disabled"
        ) { selected ->
            prefs.dragDropEnabled = selected == "Enabled"
        }
    }

    private fun setupResetButton(layout: LinearLayout) {
        val resetLayoutBtn = Button(this).apply {
            text = "Reset Layout"
            setOnClickListener {
                showResetConfirmDialog()
            }
        }
        layout.addView(resetLayoutBtn)
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Layout")
            .setMessage("Are you sure you want to reset home screen layout settings?")
            .setPositiveButton("Reset") { _, _ ->
                resetSettings()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetSettings() {
        prefs.gridColumns = 5
        prefs.gridRows = 6
        prefs.iconSize = 48
        prefs.sortMode = "a_z"
        prefs.showAppLabel = true
        prefs.labelSize = 12f
        prefs.iconSpacing = 8
        prefs.dragDropEnabled = true
        prefs.appOrder = emptyList()
    }
}
