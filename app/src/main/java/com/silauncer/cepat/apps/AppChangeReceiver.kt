package com.silauncer.cepat.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * BroadcastReceiver for monitoring application package changes.
 *
 * Listens for package add, remove, change, and replace events to keep
 * the launcher UI synchronized with system app state.
 *
 * Thread-safe and handles multiple register/unregister calls gracefully.
 */
class AppChangeReceiver(
    private val onPackageEvent: (action: String?, packageName: String?, replacing: Boolean) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppChangeReceiver"
    }

    /**
     * Tracks registration state to prevent duplicate registrations
     * and handle edge cases during activity recreation.
     */
    private var isRegistered = false

    /**
     * Registers this receiver with the given context.
     * 
     * FIX: Tracks registration state to prevent duplicate registrations
     * that can occur during activity recreation.
     * 
     * @param context The context to register the receiver with
     * @throws IllegalStateException if already registered
     */
    fun register(context: Context) {
        if (isRegistered) {
            Log.w(TAG, "Receiver already registered, ignoring duplicate registration attempt")
            return
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            context.registerReceiver(this, filter)
            isRegistered = true
            Log.d(TAG, "BroadcastReceiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver: ${e.message}", e)
            throw e
        }
    }

    /**
     * Unregisters this receiver from the given context.
     * 
     * FIX: Handles IllegalArgumentException gracefully if receiver
     * is already unregistered or was never registered.
     * 
     * @param context The context to unregister the receiver from
     */
    fun unregister(context: Context) {
        if (!isRegistered) {
            Log.d(TAG, "Receiver not registered, nothing to unregister")
            return
        }

        try {
            context.unregisterReceiver(this)
            isRegistered = false
            Log.d(TAG, "BroadcastReceiver unregistered successfully")
        } catch (e: IllegalArgumentException) {
            // This can occur if the receiver was already unregistered
            // or wasn't properly registered in the first place.
            // Safe to ignore as the end result is what we want.
            Log.w(TAG, "Receiver was not registered or already unregistered: ${e.message}")
            isRegistered = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: ${e.message}", e)
            isRegistered = false
        }
    }

    /**
     * Gets the current registration state.
     *
     * @return true if receiver is registered, false otherwise
     */
    fun isCurrentlyRegistered(): Boolean = isRegistered

    /**
     * Called when a broadcast is received.
     *
     * Extracts package information and delegates to the callback.
     * Handles null values gracefully.
     *
     * @param context The context in which the receiver is running
     * @param intent The intent being received
     */
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            val packageName = intent.data?.schemeSpecificPart
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            
            Log.d(TAG, "Package event: action=$action, package=$packageName, replacing=$replacing")
            
            onPackageEvent(action, packageName, replacing)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing broadcast: ${e.message}", e)
        }
    }
}
