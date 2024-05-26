package cn.tinyhai.ban_uninstall.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import androidx.core.content.ContextCompat
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.auth.server.AuthService
import cn.tinyhai.ban_uninstall.configs.Configs
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.ban_uninstall.utils.XPLogUtils

@SuppressLint("PrivateApi")
class PackageChangeReceiver : BroadcastReceiver() {

    private val intentFilter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addDataScheme("package")
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

    private val getSendingUserId by lazy {
        BroadcastReceiver::class.java.getDeclaredMethod("getSendingUserId")
            .also { it.isAccessible = true }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!intentFilter.hasAction(intent.action)) {
            return
        }

        val sendingUserId = getSendingUserId.invoke(this) as Int
        val uri = intent.data
        val packageName = uri?.encodedSchemeSpecificPart
        when (intent.action) {
            Intent.ACTION_PACKAGE_REPLACED -> {
                XPLogUtils.log("pkg replace uri = $uri, userId = $sendingUserId")
                if (packageName == BuildConfig.APPLICATION_ID && sendingUserId == 0) {
                    AuthService.preventAll()
                }
            }

            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                XPLogUtils.log("pkg uninstall uri = $uri, userId = $sendingUserId")
                packageName?.let {
                    TransactService.onPkgUninstall(packageName, sendingUserId)
                }
                if (packageName == BuildConfig.APPLICATION_ID && sendingUserId == 0) {
                    XPLogUtils.log("self package removed")
                    Configs.onSelfRemoved()
                }
            }
        }
    }

    fun register(context: Context) {
        XPLogUtils.log("register PackageChangeReceiver")
        registerReceiverForAllUsers?.let {
            it.invoke(
                context,
                this,
                intentFilter,
                null,
                null
            )
            Unit
        } ?: ContextCompat.registerReceiver(
            context, this, intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}