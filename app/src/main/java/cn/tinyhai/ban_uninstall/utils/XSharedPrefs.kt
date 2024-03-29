package cn.tinyhai.ban_uninstall.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.BuildConfig
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge

object XSharedPrefs {
    private lateinit var prefs: XSharedPreferences

    private var registered: Boolean = false

    private val listeners = ArrayList<() -> Unit>()

    private val notifyListenerJob by lazy {
        Runnable {
            val listeners = listeners.toList()
            LogUtils.log("notifyListener")
            listeners.forEach {
                it()
            }
        }
    }

    private const val UPDATE_DELAY = 2000L
    private val updateJob by lazy {
        Runnable {
            updater.requestUpdate()
        }
    }

    private val prefsChangeListener by lazy {
        OnSharedPreferenceChangeListener { _, _ ->
            postUpdateJob()
        }
    }

    private lateinit var updater: Updater<BooleanArray>

    fun init() {
        val xpVersion = XposedBridge.getXposedVersion()
        LogUtils.log("xp version = $xpVersion")
        prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, App.SP_FILE_NAME)
        if (xpVersion >= 93) {
            registerPrefChangeListener()
        }
        prefs.reload()
        updater =
            SimpleUpdater(
                initValue = getPrefsSnapshot(),
                shouldUpdate = { old, new ->
                    !new.contentEquals(old)
                },
                onRequestUpdate = { reloadAndUpdate() },
                onUpdateSuccess = { postNotifyReloadListener() }
            )
    }

    fun listenSelfRemoved(context: Context) {
        LogUtils.log("listenSelfRemoved")
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val packageName = intent.data?.encodedSchemeSpecificPart
                if (intentFilter.hasAction(intent.action) && packageName == BuildConfig.APPLICATION_ID) {
                    LogUtils.log("self package removed")
                    unregisterPrefChangeListener()
                }
            }
        }, intentFilter)
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

    fun unregisterPrefChangeListener() {
        if (registered) {
            LogUtils.log("unregister prefs listener")
            prefs.unregisterOnSharedPreferenceChangeListener(prefsChangeListener)
            registered = false
        }
    }

    val isBanUninstall
        get() = prefs.getBoolean(App.SP_KEY_BAN_UNINSTALL, true)

    val isBanClearData
        get() = prefs.getBoolean(App.SP_KEY_BAN_CLEAR_DATA, true)

    val isDevMode
        get() = prefs.getBoolean(App.SP_KEY_DEV_MODE, false)

    fun registerPrefsChangeListener(onPrefsChange: () -> Unit) {
        listeners.add(onPrefsChange)
    }

    fun reload() {
        if (XposedBridge.getXposedVersion() > 92) {
            return
        }
        updater.requestUpdate()
    }

    private fun getPrefsSnapshot(): BooleanArray {
        return booleanArrayOf(isBanUninstall, isBanClearData, isDevMode)
    }

    private fun postUpdateJob() {
        HandlerUtils.removeWorkerRunnable(updateJob)
        HandlerUtils.postWorkerDelay(updateJob, UPDATE_DELAY)
    }

    private fun reloadAndUpdate() {
        if (!HandlerUtils.checkWorkerThread()) {
            HandlerUtils.postWorker {
                reloadAndUpdate()
            }
            return
        }
        LogUtils.log("start reload prefs")
        prefs.reload()

        updater.finishUpdate(getPrefsSnapshot())
    }

    private fun postNotifyReloadListener() {
        LogUtils.log("postNotifyReloadListener")
        HandlerUtils.postDelay(notifyListenerJob, 0)
    }
}