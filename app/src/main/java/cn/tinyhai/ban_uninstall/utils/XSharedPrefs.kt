package cn.tinyhai.ban_uninstall.utils

import android.annotation.SuppressLint
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

@SuppressLint("PrivateApi")
object XSharedPrefs {
    private var prefs: XSharedPreferences = XSharedPreferences(BuildConfig.APPLICATION_ID, App.SP_FILE_NAME)

    private var registered: Boolean = false

    private val listeners = ArrayList<() -> Unit>()

    private val notifyListenerJob by lazy {
        Runnable {
            val listeners = listeners.toList()
            XPLogUtils.log("notifyListener")
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

    private var updater: Updater<BooleanArray> = SimpleUpdater(
        initValue = getPrefsSnapshot(),
        shouldUpdate = { old, new ->
            !new.contentEquals(old)
        },
        onRequestUpdate = { reloadAndUpdate() },
        onUpdateSuccess = { postNotifyReloadListener() }
    )

    fun init() {
        val xpVersion = XposedBridge.getXposedVersion()
        if (xpVersion >= 93) {
            registerPrefChangeListener()
        }
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
        XPLogUtils.log("listenSelfRemoved")
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
                XPLogUtils.log("pkg uninstall uri = $uri, userId = $sendingUserId")
                packageName?.let {
                    TransactService.onPkgUninstall(packageName, sendingUserId)
                }
                if (intentFilter.hasAction(intent.action) && sendingUserId == 0 && packageName == BuildConfig.APPLICATION_ID) {
                    XPLogUtils.log("self package removed")
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
            XPLogUtils.log("register prefs listener")
            prefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
            registered = true
        }
    }

    fun unregisterPrefChangeListener() {
        if (registered) {
            XPLogUtils.log("unregister prefs listener")
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
        return booleanArrayOf(isBanUninstall, isBanClearData)
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
        XPLogUtils.log("start reload prefs")
        prefs.reload()

        XPLogUtils.devMode = isDevMode

        updater.finishUpdate(getPrefsSnapshot())
    }

    private fun postNotifyReloadListener() {
        XPLogUtils.log("postNotifyReloadListener")
        HandlerUtils.postDelay(notifyListenerJob, 0)
    }
}