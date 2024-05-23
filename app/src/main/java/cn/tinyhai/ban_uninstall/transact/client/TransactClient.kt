package cn.tinyhai.ban_uninstall.transact.client

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.MainActivity
import cn.tinyhai.ban_uninstall.transact.ITransactor
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import rikka.parcelablelist.ParcelableListSlice

class TransactClient(
    private var service: ITransactor
) : ITransactor, IBinder.DeathRecipient {

    init {
        service.asBinder()?.linkToDeath(this, 0)
        service.onAppLaunched()
    }

    override fun getPackages(): ParcelableListSlice<PackageInfo> {
        return service.packages ?: ParcelableListSlice(emptyList())
    }

    override fun banPackage(
        packageNames: List<String>,
        bannedPackages: MutableList<String>
    ) {
        service.banPackage(packageNames, bannedPackages)
    }

    override fun freePackage(
        packageNames: List<String>,
        freedPackages: MutableList<String>
    ) {
        service.freePackage(packageNames, freedPackages)
    }

    override fun asBinder(): IBinder? {
        return service.asBinder()
    }

    override fun getAllBannedPackages(): List<String> {
        return service.allBannedPackages ?: emptyList()
    }

    override fun getAuth(): IBinder? {
        return service.auth
    }

    override fun getActiveMode(): Int {
        return service.activeMode
    }

    override fun syncPrefs(prefs: Map<Any?, Any?>?): Boolean {
        return service.syncPrefs(prefs)
    }

    override fun getApplicationInfoAsUser(packageName: String, userId: Int): ApplicationInfo? {
        return service.getApplicationInfoAsUser(packageName, userId)
    }

    override fun sayHello(hello: String): String {
        return service.sayHello(hello) ?: ""
    }

    override fun onAppLaunched() {
        service.onAppLaunched()
    }

    override fun reloadPrefs() {
        service.reloadPrefs()
    }

    override fun binderDied() {
        service.asBinder()?.unlinkToDeath(this, 0)
        service = ITransactor.Default()
    }

    companion object {
        private const val TAG = "TransactClient"

        private const val KEY_TRANSACT = "key_transact"

        private var client: TransactClient? = null

        private val Dummy = TransactClient(ITransactor.Default())

        operator fun invoke(): TransactClient {
            return client ?: Dummy
        }

        fun inject(intent: Intent) {
            val binder = intent.extras?.getBinder(KEY_TRANSACT)
            Log.d(TAG, "$binder")
            if (binder != null) {
                client?.binderDied()
                client = TransactClient(ITransactor.Stub.asInterface(binder))
            }
        }

        private fun ComponentName.isSelf(): Boolean {
            return packageName == BuildConfig.APPLICATION_ID && className == MainActivity::class.qualifiedName
        }

        fun injectBinderIfNeeded(service: ITransactor.Stub, intent: Intent, userId: Int) {
            val component = intent.component ?: return
            if (component.isSelf()) {
                if (userId > 0) {
                    XPLogUtils.log("inject skip dual app")
                    return
                }
                val bundle = Bundle().apply {
                    putBinder(KEY_TRANSACT, service.asBinder())
                }
                intent.apply {
                    putExtras(bundle)
                }
                XPLogUtils.log("inject transact success")
            }
        }
    }
}