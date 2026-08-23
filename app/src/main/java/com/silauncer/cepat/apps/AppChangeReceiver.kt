package com.silauncer.cepat.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class AppChangeReceiver(
    private val onPackageEvent: (action: String?, packageName: String?, replacing: Boolean) -> Unit
) : BroadcastReceiver() {
    private var isRegistered = false

    fun register(context: Context) {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        isRegistered = true
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        context.unregisterReceiver(this)
        isRegistered = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val packageName = intent.data?.schemeSpecificPart
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        
        onPackageEvent(action, packageName, replacing)
    }
}
