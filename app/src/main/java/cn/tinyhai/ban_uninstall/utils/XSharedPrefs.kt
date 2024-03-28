package cn.tinyhai.ban_uninstall.utils

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.BuildConfig
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge

object XSharedPrefs {
    private lateinit var prefs: XSharedPreferences

    @Volatile
    private var loading = false

    @Volatile
    private var registered: Boolean = false

    private val listeners = ArrayList<() -> Unit>()

    private const val NOTIFY_DELAY_MS = 5_000L
    private val notifyListenerJob by lazy {
        Runnable {
            val listeners = listeners.toList()
            LogUtils.log("notifyListener")
            listeners.forEach {
                it()
            }
        }
    }

    private val reloadOnWorker by lazy {
        Runnable {
            reloadAndNotify()
        }
    }

    private val prefsChangeListener by lazy {
        OnSharedPreferenceChangeListener { _, _ ->
            HandlerUtils.postWorker(reloadOnWorker)
        }
    }

    fun init() {
        val xpVersion = XposedBridge.getXposedVersion()
        LogUtils.log("xp version = $xpVersion")
        prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, App.SP_FILE_NAME)
        if (xpVersion >= 93) {
            registerPrefChangeListener()
        }
    }

    fun registerPrefChangeListener() {
        if (registered) {
            return
        }
        if (prefs.file.parentFile?.exists() == true) {
            LogUtils.log("register prefs listener")
            prefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
            registered = true
        }
    }

    val isBanUninstall
        get() = prefs.getBoolean(App.SP_KEY_BAN_UNINSTALL, true)

    val isBanClearData
        get() = prefs.getBoolean(App.SP_KEY_BAN_CLEAR_DATA, true)

    val isDevMode
        get() = prefs.getBoolean(App.SP_KEY_DEV_MODE, false)

    fun runAfterReload(afterReload: () -> Unit) {
        listeners.add(afterReload)
    }

    fun reload() {
        if (XposedBridge.getXposedVersion() > 92) {
            return
        }
        reloadAndNotify()
    }

    private fun reloadAndNotify() {
        if (!HandlerUtils.checkWorkerThread()) {
            HandlerUtils.postWorker {
                reloadAndNotify()
            }
            return
        }
        if (loading) {
            return
        }
        LogUtils.log("start reload prefs")
        loading = true
        HandlerUtils.removeRunnable(notifyListenerJob)
        prefs.reload()
        prefs.getBoolean(App.SP_KEY_DEV_MODE, false)
        postNotifyReloadListener()
    }

    private fun postNotifyReloadListener() {
        LogUtils.log("postNotifyReloadListener")
        HandlerUtils.postDelay(notifyListenerJob, NOTIFY_DELAY_MS)
        loading = false
    }
}