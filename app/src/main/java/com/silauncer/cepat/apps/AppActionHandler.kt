package com.silauncer.cepat.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import com.silauncer.cepat.R

class AppActionHandler(private val context: Context) {

    fun launchApp(app: AppInfo) {
        val intent = app.launchIntent()
        context.startActivity(intent)
    }

    fun showAppMenu(app: AppInfo) {
        val options = arrayOf(
            context.getString(R.string.app_info), 
            context.getString(R.string.uninstall)
        )
        AlertDialog.Builder(context)
            .setTitle(app.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openAppInfo(app)
                    1 -> requestUninstall(app)
                }
            }
            .show()
    }

    private fun openAppInfo(app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        context.startActivity(intent)
    }

    private fun requestUninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        context.startActivity(intent)
    }
}
