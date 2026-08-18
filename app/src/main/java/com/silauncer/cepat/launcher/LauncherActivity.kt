package com.silauncer.cepat.launcher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.silauncer.cepat.R
import com.silauncer.cepat.apps.AppActionHandler
import com.silauncer.cepat.apps.AppChangeReceiver
import com.silauncer.cepat.apps.AppDataSource
import com.silauncer.cepat.apps.AppStateHolder
import com.silauncer.cepat.home.AppAdapter
import com.silauncer.cepat.home.OverScroll
import com.silauncer.cepat.storage.LauncherPreferences
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var prefs: LauncherPreferences
    private lateinit var appChangeReceiver: AppChangeReceiver
    
    private lateinit var appController: LauncherAppController
    private lateinit var actionHandler: AppActionHandler
    
    private var isLoaded = false

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
                        startActivity(android.content.Intent(this, com.silauncer.cepat.settings.SettingsActivity::class.java))
                    } catch (e: Exception) {
                        android.util.Log.e("SILAUNCER", "CRASH: " + e.message, e)
                    }
                } else {
                    actionHandler.launchApp(app)
                }
            },
            onLongClick = { app ->
                if (!prefs.dragDropEnabled) {
                    actionHandler.showAppMenu(app)
                }
            }
        )
        recyclerView.adapter = adapter
        
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            private var dragStartedPosition = RecyclerView.NO_POSITION

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    dragStartedPosition = viewHolder.bindingAdapterPosition
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (!prefs.dragDropEnabled) return false
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION) {
                    adapter.moveItem(from, to)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val dropPosition = viewHolder.bindingAdapterPosition
                if (dropPosition != RecyclerView.NO_POSITION && dragStartedPosition == dropPosition) {
                    // Long press without moving in drag mode -> show app menu
                    val app = adapter.getItems().getOrNull(dropPosition)
                    if (app != null) {
                        actionHandler.showAppMenu(app)
                    }
                } else if (dropPosition != RecyclerView.NO_POSITION && dragStartedPosition != RecyclerView.NO_POSITION && prefs.dragDropEnabled) {
                    // Moved -> save new order deterministically via controller
                    val currentItems = adapter.getItems().toList()
                    lifecycleScope.launch {
                        appController.saveCustomAppOrder(currentItems)
                    }
                }
                dragStartedPosition = RecyclerView.NO_POSITION
            }

            override fun isLongPressDragEnabled(): Boolean {
                return prefs.dragDropEnabled
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
        
        appChangeReceiver = AppChangeReceiver { action, packageName, replacing ->
            lifecycleScope.launch {
                val changed = appController.handlePackageEvent(action, packageName, replacing)
            if (changed) {
                refreshAppsUI()
            }
            }
        }
        appChangeReceiver.register(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing on back button as this is a launcher
            }
        })

        loadAppsInitialUI()
    }

    override fun onResume() {
        super.onResume()
        if (recyclerView.layoutManager is GridLayoutManager) {
            val currentColumns = (recyclerView.layoutManager as GridLayoutManager).spanCount
            if (currentColumns != prefs.gridColumns) {
                recyclerView.layoutManager = GridLayoutManager(this, prefs.gridColumns)
            }
        }
        val currentIconSizePx = (prefs.iconSize * resources.displayMetrics.density).toInt()
        val currentSpacingPx = (prefs.iconSpacing * resources.displayMetrics.density).toInt()
        adapter.updateConfig(currentIconSizePx, prefs.showAppLabel, prefs.labelSize, currentSpacingPx, prefs.gridRows)
        
        if (isLoaded) {
            refreshAppsUI()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appChangeReceiver.unregister(this)
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
