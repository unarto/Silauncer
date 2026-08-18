package com.silauncer.cepat.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.silauncer.cepat.R
import com.silauncer.cepat.apps.AppInfo
import com.silauncer.cepat.cache.IconLoader
import kotlinx.coroutines.CoroutineScope

class AppAdapter(
    private val coroutineScope: CoroutineScope,
    private var iconSizePx: Int,
    private var showAppLabel: Boolean,
    private var labelSizeSp: Float,
    private var iconSpacingPx: Int,
    private var gridRows: Int,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private val apps = mutableListOf<AppInfo>()
    private val iconLoader = IconLoader(coroutineScope)
    private var recyclerView: RecyclerView? = null
    private var lastHeight = 0

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
            val newHeight = recyclerView.measuredHeight
            if (newHeight > 0 && newHeight != lastHeight) {
                lastHeight = newHeight
                notifyDataSetChanged()
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    fun submitList(newList: List<AppInfo>) {
        val diffResult = DiffUtil.calculateDiff(AppDiffCallback(apps, newList))
        apps.clear()
        apps.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= apps.size || toPosition >= apps.size) return
        val item = apps.removeAt(fromPosition)
        apps.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getItems(): List<AppInfo> = apps

    fun updateConfig(newIconSizePx: Int, newShowLabel: Boolean, newLabelSizeSp: Float, newIconSpacingPx: Int, newGridRows: Int) {
        var changed = false
        if (iconSizePx != newIconSizePx) {
            iconSizePx = newIconSizePx
            changed = true
        }
        if (showAppLabel != newShowLabel) {
            showAppLabel = newShowLabel
            changed = true
        }
        if (labelSizeSp != newLabelSizeSp) {
            labelSizeSp = newLabelSizeSp
            changed = true
        }
        if (iconSpacingPx != newIconSpacingPx) {
            iconSpacingPx = newIconSpacingPx
            changed = true
        }
        if (gridRows != newGridRows) {
            gridRows = newGridRows
            changed = true
        }
        if (changed) {
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app)
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.app_icon)
        private val nameView: TextView = itemView.findViewById(R.id.app_name)

        fun bind(app: AppInfo) {
            recyclerView?.let { rv ->
                val availableHeight = rv.measuredHeight - rv.paddingTop - rv.paddingBottom
                if (availableHeight > 0 && gridRows > 0) {
                    itemView.layoutParams = itemView.layoutParams.apply {
                        height = availableHeight / gridRows
                    }
                }
            }

            itemView.setPadding(iconSpacingPx, iconSpacingPx, iconSpacingPx, iconSpacingPx)
            
            iconView.layoutParams = iconView.layoutParams.apply {
                width = iconSizePx
                height = iconSizePx
            }
            
            val currentCacheKey = app.cacheKey
            iconView.tag = currentCacheKey
            iconLoader.loadIconAsync(itemView.context, app) { drawable, loadedKey ->
                if (iconView.tag == loadedKey) {
                    iconView.setImageDrawable(drawable)
                }
            }
            
            if (showAppLabel) {
                nameView.visibility = View.VISIBLE
                nameView.text = app.name
                nameView.textSize = labelSizeSp
            } else {
                nameView.visibility = View.GONE
            }
            
            itemView.setOnClickListener { onClick(app) }
            itemView.setOnLongClickListener {
                onLongClick?.invoke(app)
                true
            }
        }
    }
}

class AppDiffCallback(
    private val oldList: List<AppInfo>,
    private val newList: List<AppInfo>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].componentName == newList[newItemPosition].componentName && 
               oldList[oldItemPosition].user == newList[newItemPosition].user
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
