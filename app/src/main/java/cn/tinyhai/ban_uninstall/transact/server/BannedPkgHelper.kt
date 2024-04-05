package cn.tinyhai.ban_uninstall.transact.server

import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.utils.LogUtils
import java.io.File


class BannedPkgHelper {
    companion object {
        private const val CONFIG_PATH = "/data/misc/adb/${BuildConfig.APPLICATION_ID}"
        private const val BANNED_PKG_LIST = "$CONFIG_PATH/banned_pkg_list"
    }

    private val bannedPkgListFile by lazy {
        File(BANNED_PKG_LIST).apply {
            parentFile?.mkdirs()
            if (!exists()) {
                createNewFile()
            }
        }
    }

    private val bakFile by lazy {
        File(bannedPkgListFile.absolutePath + ".bak")
    }

    private val bannedPkgSet = HashSet<PkgInfo>()

    val allBannedPackages
        get() = synchronized(bannedPkgSet) {
            bannedPkgSet.map { it.toString() }.toList()
        }

    val allBannedPkgInfo
        get() = synchronized(bannedPkgSet) {
            bannedPkgSet.toSet()
        }

    fun loadBannedPkgList() {
        val hashSet = HashSet<PkgInfo>()
        synchronized(bannedPkgListFile) {
            runCatching {
                bannedPkgListFile.bufferedReader().use {
                    it.forEachLine { pkgWithUserId ->
                        if (pkgWithUserId.isNotBlank()) {
                            hashSet.add(PkgInfo(pkgWithUserId))
                        }
                    }
                }
            }.onFailure {
                LogUtils.log(it)
            }.onSuccess {
                LogUtils.log("bannedPkgList loaded")
            }
        }
        synchronized(bannedPkgSet) {
            bannedPkgSet.clear()
            bannedPkgSet.addAll(hashSet)
        }
    }

    fun storeBannedPkgList() {
        synchronized(bannedPkgListFile) {
            val list = allBannedPackages
            runCatching {
                bannedPkgListFile.renameTo(bakFile)
                bannedPkgListFile.bufferedWriter().use {
                    list.forEach { pkg ->
                        it.write(pkg)
                        it.newLine()
                    }
                }
            }.onFailure {
                LogUtils.log("bannedPkgList save failed")
                LogUtils.log(it)
                if (bakFile.exists()) {
                    bakFile.renameTo(bannedPkgListFile)
                }
            }.onSuccess {
                if (bakFile.exists()) {
                    bakFile.delete()
                }
                LogUtils.log("bannedPkgList saved")
            }
        }
    }

    fun removePkgs(packageNames: List<String>, removed: MutableList<String>) {
        synchronized(bannedPkgSet) {
            packageNames.forEach { pkgWithUserId ->
                if (bannedPkgSet.remove(PkgInfo(pkgWithUserId))) {
                    removed.add(pkgWithUserId)
                }
            }
        }
    }

    fun addPkgs(packageNames: List<String>, added: MutableList<String>) {
        synchronized(bannedPkgSet) {
            packageNames.forEach { pkgWithUserId ->
                if (bannedPkgSet.add(PkgInfo(pkgWithUserId))) {
                    added.add(pkgWithUserId)
                }
            }
        }
    }

    fun destroy() {
        File(CONFIG_PATH).deleteRecursively()
    }
}