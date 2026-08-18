package com.silauncer.cepat.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.silauncer.cepat.apps.AppActionHandler
import com.silauncer.cepat.apps.AppInfo
import com.silauncer.cepat.home.AppAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

class GridDragAndDropHandler(
    context: Context,
    private val recyclerView: RecyclerView,
    private val adapter: AppAdapter,
    private val appController: LauncherAppController,
    private val actionHandler: AppActionHandler,
    private val coroutineScope: CoroutineScope
) : RecyclerView.OnItemTouchListener {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    // Default long press timeout (AOSP Launcher typically uses a slightly faster factor, we use 0.75f)
    private val longPressTimeout = (ViewConfiguration.getLongPressTimeout() * 0.75f).toLong()
    private val handler = Handler(Looper.getMainLooper())

    private var initialX = 0f
    private var initialY = 0f
    private var activePointerId = -1

    private var currentTarget: RecyclerView.ViewHolder? = null
    private var currentApp: AppInfo? = null

    private var hasPerformedLongPress = false
    private var isDragging = false

    private val touchHelper: ItemTouchHelper

    private val longPressRunnable = Runnable {
        triggerLongPress()
    }

    init {
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            private var hasMoved = false
            private var startPos = RecyclerView.NO_POSITION

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    startPos = viewHolder.bindingAdapterPosition
                    hasMoved = false
                }
            }

            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION) {
                    adapter.moveItem(from, to)
                    hasMoved = true
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                if (hasMoved) {
                    val currentItems = adapter.getItems().toList()
                    coroutineScope.launch {
                        appController.saveCustomAppOrder(currentItems)
                    }
                }
                hasMoved = false
                startPos = RecyclerView.NO_POSITION
                isDragging = false
                cancelLongPress()
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
        recyclerView.addOnItemTouchListener(this)
    }

    private fun triggerLongPress() {
        if (currentTarget != null && currentApp != null && !hasPerformedLongPress) {
            hasPerformedLongPress = true
            actionHandler.showAppMenu(currentApp!!)
            // At this point, the finger is still down.
            // If the user moves the finger further, we will dismiss the popup and start drag.
        }
    }

    private fun cancelLongPress() {
        handler.removeCallbacks(longPressRunnable)
        hasPerformedLongPress = false
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (isDragging) return false // Let ItemTouchHelper handle it

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = e.getPointerId(0)
                initialX = e.x
                initialY = e.y
                cancelLongPress()
                isDragging = false

                val child = rv.findChildViewUnder(e.x, e.y)
                if (child != null) {
                    currentTarget = rv.getChildViewHolder(child)
                    val pos = currentTarget?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
                    if (pos != RecyclerView.NO_POSITION) {
                        currentApp = adapter.getItems().getOrNull(pos)
                        if (currentApp != null) {
                            handler.postDelayed(longPressRunnable, longPressTimeout)
                        }
                    }
                } else {
                    currentTarget = null
                    currentApp = null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == -1) return false
                val pointerIndex = e.findPointerIndex(activePointerId)
                if (pointerIndex == -1) return false

                val x = e.getX(pointerIndex)
                val y = e.getY(pointerIndex)
                val dx = abs(x - initialX)
                val dy = abs(y - initialY)

                if (dx > touchSlop || dy > touchSlop) {
                    if (hasPerformedLongPress && currentTarget != null) {
                        // User moved finger AFTER long press fired
                        actionHandler.dismissAppMenu()
                        isDragging = true
                        touchHelper.startDrag(currentTarget!!)
                        return true // Intercept to give to ItemTouchHelper
                    } else if (!hasPerformedLongPress) {
                        // User moved finger BEFORE long press fired
                        cancelLongPress()
                        currentTarget = null
                        currentApp = null
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                activePointerId = -1
                currentTarget = null
                currentApp = null
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == activePointerId) {
                    cancelLongPress()
                    activePointerId = -1
                }
            }
        }
        
        return false 
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            cancelLongPress()
        }
    }
}
