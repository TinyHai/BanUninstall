package cn.tinyhai.ban_uninstall.transact.server

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.os.*
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.XposedInit
import cn.tinyhai.ban_uninstall.auth.server.AuthService
import cn.tinyhai.ban_uninstall.transact.ITransactor
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.utils.HandlerUtils
import cn.tinyhai.ban_uninstall.utils.SPHost.Companion.SP_FILE_NAME
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import cn.tinyhai.ban_uninstall.utils.XSharedPrefs
import de.robv.android.xposed.XSharedPreferences
import rikka.parcelablelist.ParcelableListSlice

interface PkgInfoContainer {
    fun contains(packageName: String, userId: Int): Boolean
}

object TransactService : ITransactor.Stub(), PkgInfoContainer {

    private val helper = BannedPkgHelper()

    private lateinit var pm: IPackageManager
    private lateinit var um: IUserManager

    private const val STORE_BANNED_PKG_LIST_DELAY = 30_000L
    private val storeBannedPkgListJob = Runnable {
        helper.storeBannedPkgList()
    }

    fun onSystemBootCompleted() {
        pm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"))
        um = IUserManager.Stub.asInterface(ServiceManager.getService(Context.USER_SERVICE))
        HandlerUtils.postWorker {
            helper.loadBannedPkgList()
            trimBannedPkgInfo {
                try {
                    pm.getPackageInfo(it.packageName, 0, it.userId)
                    false
                } catch (e: NameNotFoundException) {
                    true
                } catch (th: Throwable) {
                    XPLogUtils.log(th)
                    true
                }
            }
        }
    }

    private fun postStoreJob() {
        HandlerUtils.removeWorkerRunnable(storeBannedPkgListJob)
        HandlerUtils.postWorkerDelay(storeBannedPkgListJob, STORE_BANNED_PKG_LIST_DELAY)
    }

    override fun getPackages(): ParcelableListSlice<PackageInfo> {
        val ident = Binder.clearCallingIdentity()
        try {
            val list = arrayListOf<PackageInfo>()
            val userIds = um.getProfileIds(Process.myUid() / 100_000, true)
            for (userId in userIds) {
                pm.getInstalledPackages(0, userId).list.filter {
                    it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
                }.let {
                    list.addAll(it)
                }
            }
            return ParcelableListSlice(list)
        } finally {
            Binder.restoreCallingIdentity(ident)
        }
    }

    override fun banPackage(
        packageNames: List<String>,
        bannedPackages: MutableList<String>
    ) {
        helper.addPkgs(packageNames, bannedPackages)
        if (bannedPackages.isNotEmpty()) {
            bannedPackages.forEach {
                XPLogUtils.log("ban pkg $it")
            }
            postStoreJob()
        }
    }

    override fun freePackage(
        packageNames: List<String>,
        freedPackages: MutableList<String>
    ) {
        helper.removePkgs(packageNames, freedPackages)
        if (freedPackages.isNotEmpty()) {
            freedPackages.forEach {
                XPLogUtils.log("free pkg $it")
            }
            postStoreJob()
        }
    }

    override fun getAllBannedPackages(): List<String> {
        return helper.allBannedPackages
    }

    override fun sayHello(hello: String): String {
        return "$hello from server"
    }

    override fun onAppLaunched() {
        XPLogUtils.log("App Launched")
    }

    override fun reloadPrefs() {
        XSharedPrefs.update(XSharedPreferences(BuildConfig.APPLICATION_ID, SP_FILE_NAME).all)
    }

    override fun getAuth(): IBinder {
        return AuthService.asBinder()
    }

    override fun getActiveMode(): Int {
        return XposedInit.activeMode.ordinal
    }

    override fun syncPrefs(prefs: Map<Any?, Any?>): Boolean {
        return try {
            XSharedPrefs.update(prefs as Map<String, *>)
            true
        } catch (e: Exception) {
            XPLogUtils.log(e)
            false
        }
    }

    override fun contains(packageName: String, userId: Int): Boolean {
        return helper.allBannedPkgInfo.contains(PkgInfo(packageName, userId))
    }

    fun onPkgUninstall(packageName: String, userId: Int) {
        val removed = mutableListOf<String>()
        helper.removePkgs(listOf(PkgInfo(packageName, userId).toString()), removed)
        if (removed.isNotEmpty()) {
            XPLogUtils.log("onPkgUninstall")
            removed.forEach {
                XPLogUtils.log(it)
            }
            postStoreJob()
        }
    }

    private fun trimBannedPkgInfo(predicate: (PkgInfo) -> Boolean) {
        val allPkgInfo = helper.allBannedPkgInfo
        val trimmed = allPkgInfo.filter(predicate)
        val removed = mutableListOf<String>()
        helper.removePkgs(trimmed.map { it.toString() }, removed)
        if (removed.isNotEmpty()) {
            XPLogUtils.log("trim bannedPkgInfo")
            removed.forEach {
                XPLogUtils.log(it)
            }
            postStoreJob()
        }
    }
}