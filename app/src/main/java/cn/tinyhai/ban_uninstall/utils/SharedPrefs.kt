package cn.tinyhai.ban_uninstall.utils

import android.content.Context
import android.content.SharedPreferences
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_FILE_NAME
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_BAN_CLEAR_DATA
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_BAN_UNINSTALL
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_DEV_MODE
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_SHOW_CONFIRM
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_USE_BANNED_LIST

object SharedPrefs : SPHost {
    var isWorldReadable: Boolean = false
        private set

    override val prefs: SharedPreferences = try {
        App.app.getSharedPreferences(SP_FILE_NAME, Context.MODE_WORLD_READABLE).also {
            isWorldReadable = true
        }
    } catch (e: Exception) {
        App.app.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE)
    }

    override var isBanUninstall by BooleanPreference(SP_KEY_BAN_UNINSTALL, true)

    override var isBanClearData by BooleanPreference(SP_KEY_BAN_CLEAR_DATA, true)

    override var isDevMode by BooleanPreference(SP_KEY_DEV_MODE, false)

    override var isUseBannedList by BooleanPreference(SP_KEY_USE_BANNED_LIST, false)

    override var isShowConfirm by BooleanPreference(SP_KEY_SHOW_CONFIRM, false)
}