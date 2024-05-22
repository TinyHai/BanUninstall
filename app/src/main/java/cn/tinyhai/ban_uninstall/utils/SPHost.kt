package cn.tinyhai.ban_uninstall.utils

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import androidx.core.content.edit
import kotlin.reflect.KProperty

abstract class Preference<T : Any>(
    val key: String,
    val default: T,
    val commit: Boolean
) {
    abstract fun SharedPreferences.get(): T
    abstract fun Editor.put(value: T)
}

class StringPreference(key: String, default: String = "", commit: Boolean = true) :
    Preference<String>(key, default, commit) {
    override fun SharedPreferences.get(): String {
        return getString(key, default)!!
    }

    override fun Editor.put(value: String) {
        putString(key, value)
    }
}

class IntPreference(key: String, default: Int = 0, commit: Boolean = true) :
    Preference<Int>(key, default, commit) {
    override fun SharedPreferences.get(): Int {
        return getInt(key, default)
    }

    override fun Editor.put(value: Int) {
        putInt(key, value)
    }
}

class LongPreference(key: String, default: Long = 0L, commit: Boolean = true) :
    Preference<Long>(key, default, commit) {
    override fun SharedPreferences.get(): Long {
        return getLong(key, default)
    }

    override fun Editor.put(value: Long) {
        putLong(key, value)
    }
}

class FloatPreference(key: String, default: Float = 0f, commit: Boolean = true) :
    Preference<Float>(key, default, commit) {
    override fun SharedPreferences.get(): Float {
        return getFloat(key, default)
    }

    override fun Editor.put(value: Float) {
        putFloat(key, value)
    }
}

class BooleanPreference(key: String, default: Boolean = false, commit: Boolean = true) :
    Preference<Boolean>(key, default, commit) {
    override fun SharedPreferences.get(): Boolean {
        return getBoolean(key, default)
    }

    override fun Editor.put(value: Boolean) {
        putBoolean(key, value)
    }
}

class StringSetPreference(key: String, default: Set<String> = emptySet(), commit: Boolean = true) :
    Preference<Set<String>>(key, default, commit) {
    override fun SharedPreferences.get(): Set<String> {
        return getStringSet(key, default)!!
    }

    override fun Editor.put(value: Set<String>) {
        putStringSet(key, value)
    }
}

interface SPHost {
    companion object {
        const val SP_FILE_NAME = "ban_uninstall"
        const val SP_KEY_BAN_UNINSTALL = "sp_ban_uninstall"
        const val SP_KEY_BAN_CLEAR_DATA = "sp_ban_clear_data"
        const val SP_KEY_DEV_MODE = "sp_dev_mode"
        const val SP_KEY_USE_BANNED_LIST = "sp_key_use_banned_list"
        const val SP_KEY_SHOW_CONFIRM = "sp_key_use_show_confirm"
    }

    val prefs: SharedPreferences

    operator fun <T : Any> Preference<T>.getValue(thisObj: Any?, property: KProperty<*>): T {
        return with(prefs) { get() }
    }

    operator fun <T : Any> Preference<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) {
        prefs.edit(commit) {
            put(value)
        }
    }

    val isBanUninstall: Boolean

    val isBanClearData: Boolean

    val isDevMode: Boolean

    val isUseBannedList: Boolean

    val isShowConfirm: Boolean
}