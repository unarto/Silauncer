package com.silauncer.cepat.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.silauncer.cepat.R

class AppActionHandler(private val context: Context) {
    private var activeDialog: AlertDialog? = null

    fun launchApp(app: AppInfo) {
        val intent = app.launchIntent()
        startActivitySafely(intent, app.name)
    }

    fun showAppMenu(app: AppInfo) {
        dismissAppMenu()
        val options = arrayOf(
            context.getString(R.string.app_info), 
            context.getString(R.string.uninstall)
        )
        activeDialog = AlertDialog.Builder(context)
            .setTitle(app.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openAppInfo(app)
                    1 -> requestUninstall(app)
                }
            }
            .setOnDismissListener { activeDialog = null }
            .show()
    }

    fun dismissAppMenu() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun openAppInfo(app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        startActivitySafely(intent, app.name)
    }

    private fun requestUninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        startActivitySafely(intent, app.name)
    }

    private fun startActivitySafely(intent: Intent, appName: String) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "App not found: $appName", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Cannot open: $appName", Toast.LENGTH_SHORT).show()
        }
    }
}

