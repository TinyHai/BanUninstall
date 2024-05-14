package cn.tinyhai.ban_uninstall

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import cn.tinyhai.ban_uninstall.transact.entities.ActiveMode

class App : Application() {
    companion object {
        lateinit var app: App

        private const val TAG = "App"

        const val SP_FILE_NAME = "ban_uninstall"
        const val SP_KEY_BAN_UNINSTALL = "sp_ban_uninstall"
        const val SP_KEY_BAN_CLEAR_DATA = "sp_ban_clear_data"
        const val SP_KEY_DEV_MODE = "sp_dev_mode"
        const val SP_KEY_USE_BANNED_LIST = "sp_key_use_banned_list"
        const val SP_KEY_SHOW_CONFIRM = "sp_key_use_show_confirm"

        var isPrefsWorldReadable = false

        private var prefs: SharedPreferences? = null

        @SuppressLint("WorldReadableFiles")
        fun getPrefs(activeMode: ActiveMode): SharedPreferences {
            return prefs ?: when (activeMode) {
                ActiveMode.Disabled, ActiveMode.Root -> app.getSharedPreferences(
                    SP_FILE_NAME,
                    Context.MODE_PRIVATE
                )

                ActiveMode.Xposed -> runCatching {
                    app.getSharedPreferences(SP_FILE_NAME, Context.MODE_WORLD_READABLE)
                }.onSuccess {
                    isPrefsWorldReadable = true
                }.getOrElse {
                    app.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE)
                }
            }.also { prefs = it }
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }
}