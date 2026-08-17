package com.silauncer.cepat.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.silauncer.cepat.apps.AppRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        
        // Title
        layout.addView(TextView(this).apply {
            text = "Launcher Settings"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 48)
        })

        // Grid Layout Setting
        layout.addView(TextView(this).apply { text = "Grid Layout" })
        val gridLayoutSpinner = Spinner(this)
        val gridLayoutAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("4x4", "4x5", "5x5", "5x6", "6x6"))
        gridLayoutSpinner.adapter = gridLayoutAdapter
        val currentGridStr = "${prefs.gridColumns}x${prefs.gridRows}"
        val gridPos = gridLayoutAdapter.getPosition(currentGridStr).takeIf { it >= 0 } ?: 3 // default 5x6
        gridLayoutSpinner.setSelection(gridPos)
        gridLayoutSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val selected = gridLayoutAdapter.getItem(pos)!!
                val parts = selected.split("x")
                if (parts.size == 2) {
                    prefs.gridColumns = parts[0].toIntOrNull() ?: 5
                    prefs.gridRows = parts[1].toIntOrNull() ?: 6
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        layout.addView(gridLayoutSpinner)

        // Icon Size Setting
        layout.addView(TextView(this).apply { 
            text = "Icon Size (dp)" 
            setPadding(0, 32, 0, 0)
        })
        val sizeSpinner = Spinner(this)
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf(32, 48, 64, 72))
        sizeSpinner.adapter = sizeAdapter
        sizeSpinner.setSelection(sizeAdapter.getPosition(prefs.iconSize))
        sizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.iconSize = sizeAdapter.getItem(pos)!!
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        layout.addView(sizeSpinner)

        // Sort Setting
        layout.addView(TextView(this).apply { 
            text = "Sorting Mode" 
            setPadding(0, 32, 0, 0)
        })
        val sortSpinner = Spinner(this)
        val sortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("a_z", "z_a", "custom"))
        sortSpinner.adapter = sortAdapter
        sortSpinner.setSelection(sortAdapter.getPosition(prefs.sortMode))
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.sortMode = sortAdapter.getItem(pos)!!
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        layout.addView(sortSpinner)

        // Show Label Setting
        layout.addView(TextView(this).apply { 
             text = "Show App Labels"
             setPadding(0, 32, 0, 0)
        })
        val showLabelSpinner = Spinner(this)
        val showLabelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Yes", "No"))
        showLabelSpinner.adapter = showLabelAdapter
        showLabelSpinner.setSelection(if (prefs.showAppLabel) 0 else 1)
        showLabelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.showAppLabel = pos == 0
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        layout.addView(showLabelSpinner)

        // Label Size Setting
        layout.addView(TextView(this).apply { 
             text = "Label Size (sp)"
             setPadding(0, 32, 0, 0)
        })
        val labelSizeSpinner = Spinner(this)
        val labelSizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf(10f, 12f, 14f, 16f))
        labelSizeSpinner.adapter = labelSizeAdapter
        labelSizeSpinner.setSelection(labelSizeAdapter.getPosition(prefs.labelSize))
        labelSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.labelSize = labelSizeAdapter.getItem(pos)!!
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        layout.addView(labelSizeSpinner)

        // Icon Spacing Setting
        layout.addView(TextView(this).apply { 
             text = "Icon Spacing (dp)"
             setPadding(0, 32, 0, 0)
        })
        val spacingSpinner = Spinner(this)
        val spacingAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf(4, 8, 12, 16, 24))
        spacingSpinner.adapter = spacingAdapter
        spacingSpinner.setSelection(spacingAdapter.getPosition(prefs.iconSpacing))
        spacingSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.iconSpacing = spacingAdapter.getItem(pos)!!
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        layout.addView(spacingSpinner)

        // Hidden Apps Setting
        val hiddenAppsBtn = Button(this).apply {
            text = "Manage Hidden Apps"
            setOnClickListener {
                lifecycleScope.launch {
                    val repository = AppRepository(this@SettingsActivity)
                    val apps = withContext(Dispatchers.IO) { repository.loadInitialApps() }
                    
                    val appNames = apps.map { it.name }.toTypedArray()
                    val packageNames = apps.map { it.componentName.packageName }.toTypedArray()
                    val hiddenSet = prefs.hiddenApps.toMutableSet()
                    
                    val checkedItems = BooleanArray(apps.size) { i ->
                        hiddenSet.contains(packageNames[i])
                    }
                    
                    AlertDialog.Builder(this@SettingsActivity)
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
        layout.addView(hiddenAppsBtn)

        // Drag & Drop Setting
        layout.addView(TextView(this).apply { 
             text = "Drag & Drop"
             setPadding(0, 32, 0, 0)
        })
        val dragDropSpinner = Spinner(this)
        val dragDropAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Enabled", "Disabled"))
        dragDropSpinner.adapter = dragDropAdapter
        dragDropSpinner.setSelection(if (prefs.dragDropEnabled) 0 else 1)
        dragDropSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.dragDropEnabled = pos == 0
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        layout.addView(dragDropSpinner)

        // Reset Layout
        val resetLayoutBtn = Button(this).apply {
            text = "Reset Layout"
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Reset Layout")
                    .setMessage("Are you sure you want to reset home screen layout settings?")
                    .setPositiveButton("Reset") { _, _ ->
                        prefs.gridColumns = 5
                        prefs.gridRows = 6
                        prefs.iconSize = 48
                        prefs.sortMode = "a_z"
                        prefs.showAppLabel = true
                        prefs.labelSize = 12f
                        prefs.iconSpacing = 8
                        prefs.dragDropEnabled = true
                        prefs.appOrder = emptyList()
                        
                        // Recreate activity to refresh the UI spinners
                        recreate()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        layout.addView(resetLayoutBtn)

        val scrollView = android.widget.ScrollView(this)
        scrollView.addView(layout)
        setContentView(scrollView)
        } catch (e: Exception) {
            android.util.Log.e("SILAUNCER", "SETTINGS CRASH: " + e.message, e)
            android.widget.Toast.makeText(this, "SETTINGS CRASH: " + e.message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
