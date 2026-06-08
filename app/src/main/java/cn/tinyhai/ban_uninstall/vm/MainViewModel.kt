package cn.tinyhai.ban_uninstall.vm

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.transact.entities.ActiveMode
import cn.tinyhai.ban_uninstall.utils.SharedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class MainState(
    val activeMode: ActiveMode = ActiveMode.Disabled,
    val banUninstall: Boolean = false,
    val banClearData: Boolean = false,
    val devMode: Boolean = false,
    val useBannedList: Boolean = false,
    val showConfirm: Boolean = false,
    val hasPwd: Boolean = false,
) {
    val isActive get() = activeMode != ActiveMode.Disabled

    companion object {
        val Empty = MainState()
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
        resetTickIfTimeout()

        if (tick == 0) {
            firstTick()
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
    private val client = TransactClient()

    private val authClient =
        client.auth?.let { AuthClient(IAuth.Stub.asInterface(it)) } ?: AuthClient()

    private val _state = MutableStateFlow(MainState.Empty)

    val state: StateFlow<MainState> = _state.asStateFlow()
    private val isActive get() = state.value.isActive

    private val clearPwdTicker = Ticker(5, 3.toDuration(DurationUnit.SECONDS)) {
        onClearPwd()
    }

    private val prefsListener: OnSharedPreferenceChangeListener

    init {
        var activeMode = ActiveMode.entries[client.activeMode]

        prefsListener = OnSharedPreferenceChangeListener { sp, _ ->
            if (sp == SharedPrefs.prefs) {
                @Suppress("UNCHECKED_CAST")
                client.syncPrefs(sp.all as Map<Any?, Any?>)
            }
        }
        SharedPrefs.prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        if (activeMode == ActiveMode.Xposed && !SharedPrefs.isWorldReadable) {
            activeMode = ActiveMode.Disabled
        }

        viewModelScope.launch {
            val hasPwd = authClient.hasPwd()
            SharedPrefs.apply {
                updateState(_state) {
                    it.copy(
                        activeMode = activeMode,
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
    }

    override fun onCleared() {
        super.onCleared()
        SharedPrefs.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    fun onBanUninstall(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onValueChanged(state.banUninstall, enabled) {
            SharedPrefs.isBanUninstall = enabled
            it.copy(banUninstall = enabled)
        }
    }

    fun onBanClearData(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onValueChanged(state.banClearData, enabled) {
            SharedPrefs.isBanClearData = enabled
            it.copy(banClearData = enabled)
        }
    }

    fun onDevMode(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onValueChanged(state.devMode, enabled) {
            SharedPrefs.isDevMode = enabled
            it.copy(devMode = enabled)
        }
    }

    fun onUseBannedList(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onValueChanged(state.useBannedList, enabled) {
            SharedPrefs.isUseBannedList = enabled
            it.copy(useBannedList = enabled)
        }
    }

    fun onShowConfirm(enabled: Boolean) {
        if (isActive.not()) {
            return
        }
        val state = state.value
        onValueChanged(state.showConfirm, enabled) {
            SharedPrefs.isShowConfirm = enabled
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
        updateState(_state) {
            it.copy(hasPwd = authClient.hasPwd())
        }
    }

    fun onClearPwd() {
        if (isActive.not()) {
            return
        }
        authClient.clearPwd()
        updateState(_state) {
            it.copy(hasPwd = authClient.hasPwd())
        }
    }

    fun onTick() {
        if (isActive.not()) {
            return
        }
        clearPwdTicker.increaseTick()
    }

    private inline fun onValueChanged(
        oldValue: Boolean,
        newValue: Boolean,
        crossinline updater: (MainState) -> MainState
    ) {
        if (oldValue != newValue) {
            updateState(_state, updater)
        }
    }
}