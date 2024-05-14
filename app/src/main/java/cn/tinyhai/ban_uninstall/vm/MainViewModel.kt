package cn.tinyhai.ban_uninstall.vm

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.receiver.BootCompletedReceiver
import cn.tinyhai.ban_uninstall.receiver.RestartMainReceiver
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.transact.entities.ActiveMode
import cn.tinyhai.ban_uninstall.utils.*
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
    val activeMode: ActiveMode,
    val banUninstall: Boolean,
    val banClearData: Boolean,
    val devMode: Boolean,
    val useBannedList: Boolean,
    val showConfirm: Boolean,
    val hasPwd: Boolean,
    val autoStart: Boolean,
) {
    val isActive get() = activeMode != ActiveMode.Disabled

    companion object {
        val Empty =
            MainState(
                ActiveMode.Disabled,
                banUninstall = false,
                banClearData = false,
                devMode = false,
                useBannedList = false,
                showConfirm = false,
                hasPwd = false,
                autoStart = false,
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
    private val prefs: SharedPreferences

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

    private val unregisterRestartMainReceiver: (() -> Unit)?

    init {
        var activeMode = ActiveMode.entries[client.activeMode]
        if (BuildConfig.ROOT_FEATURE && activeMode == ActiveMode.Disabled) {
            unregisterRestartMainReceiver = RestartMainReceiver.register(App.app)
        } else {
            unregisterRestartMainReceiver = null
        }

        prefs = App.getPrefs(activeMode)
        prefsListener = OnSharedPreferenceChangeListener { sp, _ ->
            if (sp == prefs) {
                client.syncPrefs(sp.all as Map<Any?, Any?>)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        if (activeMode == ActiveMode.Xposed && !App.isPrefsWorldReadable) {
            activeMode = ActiveMode.Disabled
        }

        val autoStart = isBootCompletedReceiverEnabled()

        viewModelScope.launch {
            val isBanUninstall = prefs.getBoolean(App.SP_KEY_BAN_UNINSTALL, true)
            val isBanClearData = prefs.getBoolean(App.SP_KEY_BAN_CLEAR_DATA, true)
            val isDevMode = prefs.getBoolean(App.SP_KEY_DEV_MODE, false)
            val isUseBannedList = prefs.getBoolean(App.SP_KEY_USE_BANNED_LIST, false)
            val isShowConfirm = prefs.getBoolean(App.SP_KEY_SHOW_CONFIRM, false)
            val hasPwd = authClient.hasPwd()
            updateState {
                it.copy(
                    activeMode = activeMode,
                    banUninstall = isBanUninstall,
                    banClearData = isBanClearData,
                    devMode = isDevMode,
                    useBannedList = isUseBannedList,
                    showConfirm = isShowConfirm,
                    hasPwd = hasPwd,
                    autoStart = autoStart
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        unregisterRestartMainReceiver?.invoke()
        client.binderDied()
        authClient.binderDied()
    }

    private inline fun updateState(crossinline updater: (MainState) -> MainState) {
        _state.value = updater(state.value)
    }

    fun hasRoot(): Boolean {
        return BuildConfig.ROOT_FEATURE && hasRoot
    }

    fun onActiveWithRoot() {
        if (!hasRoot || state.value.activeMode != ActiveMode.Disabled) {
            return
        }
        viewModelScope.launch {
            tryToInjectIntoSystemServer()
        }
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

    fun onAutoStart(enabled: Boolean) {
        if (hasRoot().not() || isActive.not() || state.value.activeMode != ActiveMode.Root) {
            return
        }
        val autoStart = state.value.autoStart
        if (autoStart != enabled) {
            setBootCompletedReceiverEnabled(enabled)
            updateState {
                it.copy(autoStart = enabled)
            }
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
            it.copy(hasPwd = authClient.hasPwd())
        }
    }

    fun onClearPwd() {
        if (isActive.not()) {
            return
        }
        authClient.clearPwd()
        updateState {
            it.copy(hasPwd = authClient.hasPwd())
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

    private fun isBootCompletedReceiverEnabled(): Boolean {
        val context = App.app
        val pm = context.packageManager
        val receiver = ComponentName(context, BootCompletedReceiver::class.java)
        return pm.getComponentEnabledSetting(receiver) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    private fun setBootCompletedReceiverEnabled(enabled: Boolean) {
        val context = App.app
        val pm = context.packageManager
        val receiver = ComponentName(context, BootCompletedReceiver::class.java)
        pm.setComponentEnabledSetting(
            receiver,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}