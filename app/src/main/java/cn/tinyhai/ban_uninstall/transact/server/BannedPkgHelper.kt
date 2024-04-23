package cn.tinyhai.ban_uninstall.transact.server

import cn.tinyhai.ban_uninstall.configs.Configs
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import cn.tinyhai.ban_uninstall.utils.writeWithBak
import java.io.File

class BannedPkgHelper {

    private val ioLock = Any()

    private val bannedPkgListFile: File
        get() = File(Configs.bannedPkgListFilePath).apply {
            parentFile?.mkdirs()
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
        synchronized(ioLock) {
            runCatching {
                bannedPkgListFile.bufferedReader().use {
                    it.forEachLine { pkgWithUserId ->
                        if (pkgWithUserId.isNotBlank()) {
                            hashSet.add(PkgInfo(pkgWithUserId))
                        }
                    }
                }
            }.onFailure {
                XPLogUtils.log(it)
            }.onSuccess {
                XPLogUtils.log("bannedPkgList loaded")
            }
        }
        synchronized(bannedPkgSet) {
            bannedPkgSet.clear()
            bannedPkgSet.addAll(hashSet)
        }
    }

    fun storeBannedPkgList() {
        synchronized(ioLock) {
            val list = allBannedPackages
            bannedPkgListFile.writeWithBak {
                list.forEach { pkg ->
                    write(pkg)
                    newLine()
                }
            }.onFailure {
                XPLogUtils.log("bannedPkgList save failed")
            }.onSuccess {
                XPLogUtils.log("bannedPkgList saved")
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
}