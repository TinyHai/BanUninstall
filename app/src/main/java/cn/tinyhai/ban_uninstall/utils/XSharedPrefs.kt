package cn.tinyhai.ban_uninstall.utils

import android.annotation.SuppressLint
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Handler
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.configs.Configs
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_FILE_NAME
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_BAN_CLEAR_DATA
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_BAN_UNINSTALL
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_DEV_MODE
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_SHOW_CONFIRM
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_KEY_USE_BANNED_LIST
import de.robv.android.xposed.XSharedPreferences

private class MapPreferences(
    initMap: Map<String, *> = emptyMap<String, Any>()
) : SharedPreferences {

    @Volatile
    private var map: Map<String, *> = initMap

    fun update(map: Map<String, *>) {
        this.map = map
    }

    override fun getAll(): Map<String, *> {
        return HashMap(map)
    }

    override fun getString(key: String, defValue: String?): String? {
        return map[key] as? String ?: defValue
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return map[key] as? Set<String> ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int {
        return map[key] as? Int ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        return map[key] as? Long ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return map[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return map[key] as? Boolean ?: defValue
    }

    override fun contains(key: String): Boolean {
        return map.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor {
        throw UnsupportedOperationException()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        throw UnsupportedOperationException()
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        throw UnsupportedOperationException()
    }

    override fun toString(): String {
        return map.toString()
    }
}

@SuppressLint("PrivateApi")
object XSharedPrefs : SPHost {
    private val _prefs: MapPreferences
    override val prefs: SharedPreferences get() = _prefs

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

    init {
        val xprefs = XSharedPreferences(BuildConfig.APPLICATION_ID, SP_FILE_NAME)
        _prefs = MapPreferences(xprefs.all)

        XPLogUtils.log(xprefs.file.absolutePath)
        XPLogUtils.log(prefs.toString())

        SystemContextHolder.registerCallback {
            onSystemContext(it)
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

    private fun onSystemContext(context: Context) {
        XPLogUtils.log("start listen self remove broadcast")
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
                    Configs.onSelfRemoved()
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

    override val isBanUninstall by BooleanPreference(SP_KEY_BAN_UNINSTALL, true)

    override val isBanClearData by BooleanPreference(SP_KEY_BAN_CLEAR_DATA, true)

    override val isDevMode by BooleanPreference(SP_KEY_DEV_MODE, false)

    override val isUseBannedList by BooleanPreference(SP_KEY_USE_BANNED_LIST, false)

    override val isShowConfirm by BooleanPreference(SP_KEY_SHOW_CONFIRM, false)

    fun registerPrefsChangeListener(onPrefsChange: () -> Unit) {
        listeners.add(onPrefsChange)
    }

    fun update(map: Map<String, *>) {
        val oldSnapshot = getPrefsSnapshot()
        _prefs.update(map)
        val newSnapshot = getPrefsSnapshot()
        if (!oldSnapshot.contentEquals(newSnapshot)) {
            postNotifyReloadListener()
        }
    }

    private fun getPrefsSnapshot(): BooleanArray {
        return booleanArrayOf(isBanUninstall, isBanClearData)
    }

    private fun postNotifyReloadListener() {
        XPLogUtils.log("postNotifyReloadListener")
        HandlerUtils.postDelay(notifyListenerJob, 0)
    }
}