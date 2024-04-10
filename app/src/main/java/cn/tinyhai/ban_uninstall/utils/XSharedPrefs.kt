package cn.tinyhai.ban_uninstall.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Handler
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.transact.server.TransactService
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

    private val registerReceiverForAllUsers by lazy {
        try {
            Context::class.java.getDeclaredMethod(
                "registerReceiverForAllUsers",
                BroadcastReceiver::class.java,
                IntentFilter::class.java,
                String::class.java,
                Handler::class.java
            )
        } catch (e: NoSuchMethodException) {
            null
        }
    }

    fun listenSelfRemoved(context: Context) {
        LogUtils.log("listenSelfRemoved")
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            private val getSendingUserId by lazy {
                BroadcastReceiver::class.java.getDeclaredMethod("getSendingUserId")
                    .also { it.isAccessible = true }
            }

            override fun onReceive(context: Context, intent: Intent) {
                val sendingUserId = getSendingUserId.invoke(this) as Int
                val uri = intent.data
                val packageName = uri?.encodedSchemeSpecificPart
                LogUtils.log("pkg uninstall uri = $uri, userId = $sendingUserId")
                packageName?.let {
                    TransactService.onPkgUninstall(packageName, sendingUserId)
                }
                if (intentFilter.hasAction(intent.action) && sendingUserId == 0 && packageName == BuildConfig.APPLICATION_ID) {
                    LogUtils.log("self package removed")
                    unregisterPrefChangeListener()
                    TransactService.onSelfRemoved()
                }
            }
        }
        registerReceiverForAllUsers?.let {
            it.invoke(
                context,
                receiver,
                intentFilter,
                null,
                null
            )
            Unit
        } ?: context.registerReceiver(receiver, intentFilter)
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

    val isUseBannedList
        get() = prefs.getBoolean(App.SP_KEY_USE_BANNED_LIST, false)

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