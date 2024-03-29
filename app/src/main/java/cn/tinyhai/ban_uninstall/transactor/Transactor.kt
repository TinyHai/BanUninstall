package cn.tinyhai.ban_uninstall.transactor

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import android.os.Bundle
import android.os.IBinder.DeathRecipient
import android.os.ServiceManager
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.ITransactor
import cn.tinyhai.ban_uninstall.MainActivity
import cn.tinyhai.ban_uninstall.utils.HandlerUtils
import cn.tinyhai.ban_uninstall.utils.LogUtils
import cn.tinyhai.ban_uninstall.utils.XSharedPrefs
import rikka.parcelablelist.ParcelableListSlice
import java.io.File
import kotlin.concurrent.thread

object Transactor : ITransactor.Stub() {
    private const val BANNED_PKG_LIST =
        "/data/misc/adb/${BuildConfig.APPLICATION_ID}/banned_pkg_list"

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

    private const val KEY_CLIENT = "key_client"

    private lateinit var pm: IPackageManager

    private val bannedPkgSet = HashSet<String>()

    private const val STORE_BANNED_PKG_LIST_DELAY = 30_000L
    private val storeBannedPkgListJob = Runnable {
        storeBannedPkgList()
    }

    private val clientDeathRecipient = object : DeathRecipient {
        override fun binderDied() {
            client?.asBinder()?.unlinkToDeath(this, 0)
            client = null
        }
    }

    private fun ComponentName.isSelf(): Boolean {
        return packageName == BuildConfig.APPLICATION_ID && className == MainActivity::class.qualifiedName
    }

    var client: ITransactor? = null
        private set

    fun prepareIntent(intent: Intent) {
        val component = intent.component ?: return
        if (component.isSelf()) {
            val bundle = Bundle().apply {
                putBinder(KEY_CLIENT, this@Transactor.asBinder())
            }
            intent.apply {
                putExtras(bundle)
            }
            LogUtils.log("inject self success")
        }
    }

    fun parseFromIntent(intent: Intent) {
        client?.let {
            it.asBinder().unlinkToDeath(clientDeathRecipient, 0)
            client = null
        }
        val binder = intent.extras?.getBinder(KEY_CLIENT)
        binder?.let {
            client = asInterface(it)
            it.linkToDeath(clientDeathRecipient, 0)
        }
    }

    fun onSystemBootCompleted() {
        pm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"))
        loadBannedPkgList()
    }

    private fun loadBannedPkgList() {
        HandlerUtils.postWorker {
            val hashSet = HashSet<String>()
            synchronized(bannedPkgListFile) {
                runCatching {
                    bannedPkgListFile.bufferedReader().use {
                        it.forEachLine { pkg ->
                            if (pkg.isNotBlank()) {
                                hashSet.add(pkg)
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
    }

    private fun postStoreJob() {
        HandlerUtils.removeWorkerRunnable(storeBannedPkgListJob)
        HandlerUtils.postWorkerDelay(storeBannedPkgListJob, STORE_BANNED_PKG_LIST_DELAY)
    }

    private fun storeBannedPkgList() {
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

    override fun getPackages(): ParcelableListSlice<PackageInfo> {
        val list = pm.getInstalledPackages(0, 0).list.filter {
            it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
        }
        return ParcelableListSlice(list)
    }

    override fun banPackage(
        packageNames: List<String>,
        bannedPackages: MutableList<String>
    ): Boolean {
        var added = false
        synchronized(bannedPkgSet) {
            packageNames.forEach {
                if (bannedPkgSet.add(it)) {
                    added = true
                    bannedPackages.add(it)
                }
            }
        }
        if (added) {
            postStoreJob()
        }
        return added
    }

    override fun freePackage(
        packageNames: List<String>,
        freedPackages: MutableList<String>
    ): Boolean {
        var removed = false
        synchronized(bannedPkgSet) {
            packageNames.forEach {
                if (bannedPkgSet.remove(it)) {
                    removed = true
                    freedPackages.add(it)
                }
            }
        }
        if (removed) {
            postStoreJob()
        }
        return removed
    }

    override fun getAllBannedPackages(): List<String> {
        return synchronized(bannedPkgSet) {
            bannedPkgSet.toList()
        }
    }

    override fun sayHello(hello: String): String {
        return "$hello from server"
    }

    override fun onAppLaunched() {
        XSharedPrefs.registerPrefChangeListener()
    }

    override fun reloadPrefs() {
        XSharedPrefs.reload()
    }
}