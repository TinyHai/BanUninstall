package cn.tinyhai.ban_uninstall.utils

import android.content.Intent
import android.content.pm.PackageInfo
import cn.tinyhai.ban_uninstall.ITransactor
import cn.tinyhai.ban_uninstall.transactor.Transactor

object TransactorHelper {
    private val client: ITransactor? get() = Transactor.client

    private val outList: MutableList<String> = ArrayList()

    fun init(intent: Intent) {
        Transactor.parseFromIntent(intent)
        client?.onAppLaunched()
    }

    fun sayHello(hello: String): String {
        return client?.sayHello(hello) ?: ""
    }

    fun getAllPackages(): List<PackageInfo> {
        return client?.packages?.list ?: emptyList()
    }

    fun banPackages(packages: List<String>): Result<List<String>> {
        return runCatching {
            val success = client?.banPackage(packages, outList) ?: false
            if (success) {
                outList.toList()
            } else {
                throw RuntimeException("banPackages failed with packages: $packages")
            }
        }.also { outList.clear() }
    }

    fun freePackages(packages: List<String>): Result<List<String>> {
        return runCatching {
            val success = client?.banPackage(packages, outList) ?: false
            if (success) {
                outList.toList()
            } else {
                throw RuntimeException("freePackages failed with packages: $packages")
            }
        }.also { outList.clear() }
    }

    fun reload() {
        client?.reloadPrefs()
    }
}