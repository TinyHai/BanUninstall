package cn.tinyhai.ban_uninstall

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.Keep

class App : Application() {
    companion object {
        lateinit var app: App

        private const val TAG = "App"

        lateinit var prefs: SharedPreferences

        const val SP_FILE_NAME = "ban_uninstall"
        const val SP_KEY_BAN_UNINSTALL = "sp_ban_uninstall"
        const val SP_KEY_BAN_CLEAR_DATA = "sp_ban_clear_data"
        const val SP_KEY_DEV_MODE = "sp_dev_mode"
        const val SP_KEY_USE_BANNED_LIST = "sp_key_use_banned_list"
        fun getXpTag() = app.getXpTag()
    }

    @Keep
    fun getXpTag(): String {
        Log.d(TAG, "getXpTag return \"\"")
        return ""
    }

    @SuppressLint("WorldReadableFiles")
    override fun onCreate() {
        super.onCreate()
        app = this
        prefs = getSharedPreferences(
            SP_FILE_NAME,
            if (getXpTag().isNotEmpty()) Context.MODE_WORLD_READABLE else Context.MODE_PRIVATE
        )
    }
}