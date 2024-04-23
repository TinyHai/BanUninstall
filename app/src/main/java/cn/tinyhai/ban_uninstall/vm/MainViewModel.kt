package cn.tinyhai.ban_uninstall.vm

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.SystemClock
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
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class MainState(
    val xpTag: String,
    val banUninstall: Boolean,
    val banClearData: Boolean,
    val devMode: Boolean,
    val useBannedList: Boolean,
    val showConfirm: Boolean,
    val hasPwd: Boolean,
) {
    val isActive get() = xpTag.isNotEmpty()

    companion object {
        val Empty =
            MainState(
                xpTag = "",
                banUninstall = false,
                banClearData = false,
                devMode = false,
                useBannedList = false,
                showConfirm = false,
                hasPwd = false
            )
    }
}

private class Ticker(
    private val maxTick: Int,
    private val duration: Duration,
    private val onMaxTick: () -> Unit,
) {
    init {
        assert(maxTick > 0)
    }

    private var tick = 0

    private var firstTickMs = 0L

    fun increaseTick() {
        if (tick == 0) {
            firstTick()
        } else {
            resetTickIfTimeout()
        }
        tick += 1
        afterIncreaseTick()
    }

    private fun firstTick() {
        firstTickMs = SystemClock.uptimeMillis()
    }

    private fun afterIncreaseTick() {
        if (tick >= maxTick && SystemClock.uptimeMillis() - firstTickMs <= duration.inWholeMilliseconds) {
            onMaxTick()
            reset()
        }
    }

    private fun resetTickIfTimeout() {
        if (SystemClock.uptimeMillis() - firstTickMs > duration.inWholeMilliseconds) {
            reset()
        }
    }

    private fun reset() {
        tick = 0
        firstTickMs = 0
    }
}

class MainViewModel : ViewModel() {
    private val prefs get() = App.prefs
    private val xpTag get() = App.getXpTag()

    private val client = TransactClient()

    private val authClient = client.getAuthClient()

    private val _state = MutableStateFlow(MainState.Empty)

    val state: StateFlow<MainState> = _state.asStateFlow()
    private val isActive get() = state.value.isActive

    private var configChanged = false

    private val clearPwdTicker = Ticker(5, 3.toDuration(DurationUnit.SECONDS)) {
        onClearPwd()
    }

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
            val isShowConfirm = prefs.getBoolean(App.SP_KEY_SHOW_CONFIRM, false)
            val hasPwd = authClient.hasPwd
            updateState {
                it.copy(
                    xpTag = xpTag,
                    banUninstall = isBanUninstall,
                    banClearData = isBanClearData,
                    devMode = isDevMode,
                    useBannedList = isUseBannedList,
                    showConfirm = isShowConfirm,
                    hasPwd = hasPwd,
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

    fun onShowConfirm(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onSwitchChange(App.SP_KEY_SHOW_CONFIRM, state.showConfirm, enabled) {
            it.copy(showConfirm = enabled)
        }
    }

    fun onVerifyPwd(pwd: String): Boolean {
        return authClient.authenticate(pwd)
    }

    fun onSetPwd(pwd: String) {
        if (isActive.not() || pwd.isEmpty()) {
            return
        }
        authClient.setPwd(pwd)
        updateState {
            it.copy(hasPwd = authClient.hasPwd)
        }
    }

    fun onClearPwd() {
        if (isActive.not()) {
            return
        }
        authClient.clearPwd()
        updateState {
            it.copy(hasPwd = authClient.hasPwd)
        }
    }

    fun notifyReloadIfNeeded() {
        if (configChanged) {
            configChanged = false
            client.reloadPrefs()
        }
    }

    fun sayHello() {
        if (isActive.not()) {
            return
        }
        clearPwdTicker.increaseTick()

        viewModelScope.launch {
            client.sayHello("test").let {
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