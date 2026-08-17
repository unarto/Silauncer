package com.silauncer.cepat.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class AppChangeReceiver(
    private val onPackageEvent: (action: String?, packageName: String?, replacing: Boolean) -> Unit
) : BroadcastReceiver() {

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(this, filter)
    }

    fun unregister(context: Context) {
        context.unregisterReceiver(this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val packageName = intent.data?.schemeSpecificPart
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        
        onPackageEvent(action, packageName, replacing)
    }
}
