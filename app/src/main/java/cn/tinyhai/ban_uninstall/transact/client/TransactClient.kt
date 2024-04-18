package cn.tinyhai.ban_uninstall.transact.client

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.MainActivity
import cn.tinyhai.ban_uninstall.transact.ITransactor
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactClient(
    private val remote: ITransactor
) {
    private val outputList: MutableList<String> = arrayListOf()
        get() {
            field.clear()
            return field
        }

    suspend fun fetchInstalledPackages() = withContext(Dispatchers.IO) {
        remote.packages.list
    }

    suspend fun fetchAllBannedPackages() = withContext(Dispatchers.IO) {
        remote.allBannedPackages.map { PkgInfo(it) }
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

    suspend fun sayHello(hello: String) = withContext(Dispatchers.IO) {
        remote.sayHello(hello)
    }

    fun onAppLaunched() {
        remote.onAppLaunched()
    }

    fun reloadPrefs() {
        remote.reloadPrefs()
    }

    companion object {
        private const val TAG = "TransactClient"

        private const val KEY_CLIENT = "key_client"

        private var client: TransactClient? = null

        private val clientDeathRecipient = object : IBinder.DeathRecipient {
            override fun binderDied() {
                client?.remote?.asBinder()?.unlinkToDeath(this, 0)
                client = null
            }
        }

        operator fun invoke(): TransactClient? {
            return client
        }

        fun init(intent: Intent) {
            val binder = intent.extras?.getBinder(KEY_CLIENT)
            Log.d(TAG, "$binder")
            binder?.let {
                val remote = ITransactor.Stub.asInterface(it)
                it.linkToDeath(clientDeathRecipient, 0)
                TransactClient(remote).also {
                    it.onAppLaunched()
                    client = it
                }
            }
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
                    putBinder(KEY_CLIENT, remote.asBinder())
                }
                intent.apply {
                    putExtras(bundle)
                }
                XPLogUtils.log("inject self success")
            }
        }
    }
}