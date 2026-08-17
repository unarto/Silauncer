package com.silauncer.cepat.launcher

import android.app.Application
import com.tencent.mmkv.MMKV

class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
    }
}
