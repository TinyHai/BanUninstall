package cn.tinyhai.ban_uninstall

import android.app.Application

class App : Application() {
    companion object {
        lateinit var app: App

        private const val TAG = "App"
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }
}