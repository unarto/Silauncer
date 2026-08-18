package com.silauncer.cepat.settings

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

object SettingsUi {
    fun createTitle(context: Context, title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 48)
        }
    }

    fun createSectionLabel(context: Context, label: String, paddingTop: Int): TextView {
        return TextView(context).apply {
            text = label
            setPadding(0, paddingTop, 0, 0)
        }
    }

    fun <T> addSpinnerSetting(
        context: Context,
        layout: LinearLayout,
        label: String,
        items: Array<T>,
        currentValue: T,
        defaultFallbackIndex: Int = 0,
        paddingTop: Int = 32,
        onItemSelected: (T) -> Unit
    ) {
        layout.addView(createSectionLabel(context, label, paddingTop))

        val spinner = Spinner(context)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items)
        spinner.adapter = adapter
        
        val pos = adapter.getPosition(currentValue)
        spinner.setSelection(if (pos >= 0) pos else defaultFallbackIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                adapter.getItem(position)?.let { onItemSelected(it) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        
        layout.addView(spinner)
    }
}
