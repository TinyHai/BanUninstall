package cn.tinyhai.ban_uninstall.vm

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainState(
    val xpTag: String,
    val banUninstall: Boolean,
    val banClearData: Boolean,
    val devMode: Boolean,
    val useBannedList: Boolean,
) {
    val isActive get() = xpTag.isNotEmpty()

    companion object {
        val Empty =
            MainState(
                xpTag = "",
                banUninstall = false,
                banClearData = false,
                devMode = false,
                useBannedList = false
            )
    }
}

class MainViewModel : ViewModel() {
    private val prefs get() = App.prefs
    private val xpTag get() = App.getXpTag()

    private val client = TransactClient()

    private val _state = MutableStateFlow(MainState.Empty)

    val state: StateFlow<MainState> = _state.asStateFlow()
    private val isActive get() = state.value.isActive

    private var configChanged = false

    private val prefsListener =
        OnSharedPreferenceChangeListener { _, _ ->
            configChanged = true
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        viewModelScope.launch {
            val xpTag = xpTag
            val isBanUninstall = prefs.getBoolean(App.SP_KEY_BAN_UNINSTALL, true)
            val isBanClearData = prefs.getBoolean(App.SP_KEY_BAN_CLEAR_DATA, true)
            val isDevMode = prefs.getBoolean(App.SP_KEY_DEV_MODE, false)
            val isUseBannedList = prefs.getBoolean(App.SP_KEY_USE_BANNED_LIST, false)
            updateState {
                it.copy(
                    xpTag = xpTag,
                    banUninstall = isBanUninstall,
                    banClearData = isBanClearData,
                    devMode = isDevMode,
                    useBannedList = isUseBannedList
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private inline fun updateState(crossinline updater: (MainState) -> MainState) {
        _state.value = updater(state.value)
    }

    fun onBanUninstall(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onSwitchChange(App.SP_KEY_BAN_UNINSTALL, state.banUninstall, enabled) {
            it.copy(banUninstall = enabled)
        }
    }

    fun onBanClearData(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onSwitchChange(App.SP_KEY_BAN_CLEAR_DATA, state.banClearData, enabled) {
            it.copy(banClearData = enabled)
        }
    }

    fun onDevMode(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onSwitchChange(App.SP_KEY_DEV_MODE, state.devMode, enabled) {
            it.copy(devMode = enabled)
        }
    }

    fun onUseBannedList(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onSwitchChange(App.SP_KEY_USE_BANNED_LIST, state.useBannedList, enabled) {
            it.copy(useBannedList = enabled)
        }
    }

    fun notifyReloadIfNeeded() {
        if (configChanged) {
            configChanged = false
            client?.reloadPrefs()
        }
    }

    fun sayHello() {
        viewModelScope.launch {
            client?.sayHello("test")?.let {
                Toast.makeText(App.app, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inline fun onSwitchChange(
        key: String,
        oldValue: Boolean,
        newValue: Boolean,
        crossinline updater: (MainState) -> MainState
    ) {
        viewModelScope.launch {
            if (oldValue != newValue) {
                withContext(Dispatchers.IO) {
                    prefs.edit(true) {
                        putBoolean(key, newValue)
                    }
                }
                updateState(updater)
            }
        }
    }
}