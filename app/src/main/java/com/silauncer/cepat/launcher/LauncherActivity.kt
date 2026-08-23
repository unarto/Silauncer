package com.silauncer.cepat.launcher

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.silauncer.cepat.R
import com.silauncer.cepat.apps.AppActionHandler
import com.silauncer.cepat.apps.AppChangeReceiver
import com.silauncer.cepat.apps.AppDataSource
import com.silauncer.cepat.apps.AppStateHolder
import com.silauncer.cepat.home.AppAdapter
import com.silauncer.cepat.home.OverScroll
import com.silauncer.cepat.settings.SettingsActivity
import com.silauncer.cepat.storage.LauncherPreferences
import kotlinx.coroutines.launch

/**
 * Main launcher activity displaying the app grid.
 *
 * Handles app loading, display, drag-and-drop, and lifecycle management.
 * Ensures proper cleanup of BroadcastReceiver and other resources.
 */
class LauncherActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LauncherActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var prefs: LauncherPreferences
    private lateinit var appChangeReceiver: AppChangeReceiver
    
    private lateinit var appController: LauncherAppController
    private lateinit var actionHandler: AppActionHandler
    private lateinit var dragHandler: GridDragAndDropHandler
    
    private var isLoaded = false
    private var isReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        
        prefs = LauncherPreferences()
        val appDataSource = AppDataSource(applicationContext)
        val appStateHolder = AppStateHolder()
        
        appController = LauncherAppController(appDataSource, appStateHolder, prefs)
        actionHandler = AppActionHandler(this)

        recyclerView = findViewById(R.id.app_grid)
        recyclerView.layoutManager = GridLayoutManager(this, prefs.gridColumns)
        OverScroll.setup(recyclerView)

        val iconSizePx = (prefs.iconSize * resources.displayMetrics.density).toInt()
        val spacingPx = (prefs.iconSpacing * resources.displayMetrics.density).toInt()
        
        adapter = AppAdapter(
            lifecycleScope,
            iconSizePx,
            prefs.showAppLabel,
            prefs.labelSize,
            spacingPx,
            prefs.gridRows,
            onClick = { app ->
                if (app.packageName == applicationContext.packageName) {
                    try {
                        val settingsIntent = android.content.Intent(this, SettingsActivity::class.java)
                        startActivity(settingsIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch Settings: ${e.message}", e)
                    }
                } else {
                    actionHandler.launchApp(app)
                }
            }
        )
        recyclerView.adapter = adapter
        
        dragHandler = GridDragAndDropHandler(
            context = this,
            recyclerView = recyclerView,
            adapter = adapter,
            appController = appController,
            actionHandler = actionHandler,
            coroutineScope = lifecycleScope
        )
        
        appChangeReceiver = AppChangeReceiver { action, packageName, replacing ->
            lifecycleScope.launch {
                val changed = appController.handlePackageEvent(action, packageName, replacing)
                if (changed) {
                    refreshAppsUI()
                }
            }
        }
        registerReceiver()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing on back button as this is a launcher
            }
        })

        loadAppsInitialUI()
    }

    override fun onResume() {
        super.onResume()
        updateGridLayout()
        val currentIconSizePx = (prefs.iconSize * resources.displayMetrics.density).toInt()
        val currentSpacingPx = (prefs.iconSpacing * resources.displayMetrics.density).toInt()
        adapter.updateConfig(currentIconSizePx, prefs.showAppLabel, prefs.labelSize, currentSpacingPx, prefs.gridRows)
        
        if (isLoaded) {
            refreshAppsUI()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver()
    }

    /**
     * Safely updates the grid layout manager with proper null checking.
     * Prevents NPE when layoutManager is modified from other threads.
     * FIX: Uses safe smart cast instead of direct type cast.
     */
    private fun updateGridLayout() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentColumns = layoutManager.spanCount
            if (currentColumns != prefs.gridColumns) {
                recyclerView.layoutManager = GridLayoutManager(this, prefs.gridColumns)
            }
        }
    }

    /**
     * Registers the BroadcastReceiver for package change events.
     * Uses a flag to ensure only one registration.
     * FIX: Prevents duplicate receiver registration on activity recreation.
     */
    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            try {
                appChangeReceiver.register(this)
                isReceiverRegistered = true
                Log.d(TAG, "BroadcastReceiver registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register BroadcastReceiver: ${e.message}", e)
            }
        }
    }

    /**
     * Safely unregisters the BroadcastReceiver.
     * Handles IllegalArgumentException if receiver is already unregistered.
     * FIX: Prevents memory leaks by ensuring proper cleanup.
     */
    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                appChangeReceiver.unregister(this)
                isReceiverRegistered = false
                Log.d(TAG, "BroadcastReceiver unregistered successfully")
            } catch (e: IllegalArgumentException) {
                // Receiver was already unregistered, safe to ignore
                Log.w(TAG, "BroadcastReceiver already unregistered: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister BroadcastReceiver: ${e.message}", e)
            }
        }
    }

    private fun loadAppsInitialUI() {
        lifecycleScope.launch {
            val sortedApps = appController.loadAppsInitial()
            adapter.submitList(sortedApps)
            isLoaded = true
        }
    }
    
    private fun refreshAppsUI() {
        lifecycleScope.launch {
            val sortedApps = appController.refreshApps()
            adapter.submitList(sortedApps)
        }
    }
}
