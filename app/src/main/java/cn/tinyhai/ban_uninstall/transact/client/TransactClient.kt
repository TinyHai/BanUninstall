package cn.tinyhai.ban_uninstall.transact.client

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.MainActivity
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.transact.ITransactor
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactClient(
    private var remote: ITransactor
) : IBinder.DeathRecipient {

    init {
        remote.asBinder()?.linkToDeath(this, 0)
        remote.onAppLaunched()
    }

    private val outputList: MutableList<String> = arrayListOf()
        get() {
            field.clear()
            return field
        }

    suspend fun fetchInstalledPackages(): List<PackageInfo> = withContext(Dispatchers.IO) {
        remote.packages.list ?: emptyList()
    }

    suspend fun fetchAllBannedPackages() = withContext(Dispatchers.IO) {
        remote.allBannedPackages?.map { PkgInfo(it) } ?: emptyList()
    }

    suspend fun banPackage(pkgInfos: List<PkgInfo>): List<PkgInfo> {
        val output = outputList
        withContext(Dispatchers.IO) {
            remote.banPackage(pkgInfos.map { it.toString() }, output)
        }
        return output.map { PkgInfo(it) }
    }

    suspend fun freePackage(pkgInfos: List<PkgInfo>): List<PkgInfo> {
        val output = outputList
        withContext(Dispatchers.IO) {
            remote.freePackage(pkgInfos.map { it.toString() }, output)
        }
        return output.map { PkgInfo(it) }
    }

    suspend fun sayHello(hello: String): String = withContext(Dispatchers.IO) {
        remote.sayHello(hello) ?: ""
    }

    fun onAppLaunched() {
        remote.onAppLaunched()
    }

    fun reloadPrefs() {
        remote.reloadPrefs()
    }

    fun getAuthClient(): AuthClient {
        val remoteAuth = remote.auth?.let {
            IAuth.Stub.asInterface(it)
        } ?: IAuth.Default()
        return AuthClient(remoteAuth)
    }

    override fun binderDied() {
        remote.asBinder()?.unlinkToDeath(this, 0)
        remote = ITransactor.Default()
    }

    companion object {
        private const val TAG = "TransactClient"

        private const val KEY_TRANSACT = "key_transact"

        private lateinit var client: TransactClient

        operator fun invoke(): TransactClient {
            return client
        }

        fun inject(intent: Intent) {
            val binder = intent.extras?.getBinder(KEY_TRANSACT)
            Log.d(TAG, "$binder")
            val remote = if (binder != null) {
                ITransactor.Stub.asInterface(binder)
            } else {
                ITransactor.Default()
            }
            client = TransactClient(remote).also { it.onAppLaunched() }
        }

        private fun ComponentName.isSelf(): Boolean {
            return packageName == BuildConfig.APPLICATION_ID && className == MainActivity::class.qualifiedName
        }

        fun injectBinderIfNeeded(remote: ITransactor.Stub, intent: Intent, userId: Int) {
            val component = intent.component ?: return
            if (component.isSelf()) {
                if (userId > 0) {
                    XPLogUtils.log("inject skip dual app")
                    return
                }
                val bundle = Bundle().apply {
                    putBinder(KEY_TRANSACT, remote.asBinder())
                }
                intent.apply {
                    putExtras(bundle)
                }
                XPLogUtils.log("inject transact success")
            }
        }
    }
}