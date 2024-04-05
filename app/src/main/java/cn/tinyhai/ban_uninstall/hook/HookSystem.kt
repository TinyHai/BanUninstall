package cn.tinyhai.ban_uninstall.hook

import android.content.Context
import android.content.Intent
import android.content.pm.IPackageDataObserver
import android.os.Build
import android.os.Handler
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.ban_uninstall.utils.LogUtils
import cn.tinyhai.ban_uninstall.utils.XSharedPrefs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

private class HookLaunchItemForInjectClient : BaseOneshotHooker() {
    override val name: String
        get() = "HookLaunchItemForInjectClient"

    override fun createOneshotHook(lp: XC_LoadPackage.LoadPackageParam) {
        val launchActivityItemClass =
            lp.classLoader.loadClass("android.app.servertransaction.LaunchActivityItem")
        findAndHookFirst(launchActivityItemClass, "obtain", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                TransactClient.injectBinderIfNeeded(TransactService, intent)
            }
        })
    }

    override val targetPackageName: String
        get() = BuildConfig.APPLICATION_ID
    override val targetProcessName: String
        get() = BuildConfig.APPLICATION_ID
}

class HookSystem : BaseUnhookableHooker() {
    override val name: String
        get() = "HookSystem"

    override fun createOneshotHook(lp: XC_LoadPackage.LoadPackageParam) {
        val unhook: Array<XC_MethodHook.Unhook?> = arrayOf(null)
        unhook[0] = findAndHookFirst(
            "com.android.server.SystemServiceManager",
            lp.classLoader,
            "startBootPhase",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val phase = param.args.lastOrNull() as? Int ?: 0
                    if (phase == 1000 /* PHASE_BOOT_COMPLETED */) {
                        TransactService.onSystemBootCompleted()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            HookLaunchItemForInjectClient().startOneshotHook(lp)
                        }
                        val activityThread =
                            XposedHelpers.findClass("android.app.ActivityThread", lp.classLoader)
                                .getDeclaredField("sCurrentActivityThread")
                                .also { it.isAccessible = true }.get(null)
                        val systemContext =
                            activityThread::class.java.getDeclaredField("mSystemContext")
                                .also { it.isAccessible = true }.get(activityThread) as? Context
                        LogUtils.log("systemContext: $systemContext")
                        systemContext?.let {
                            XSharedPrefs.listenSelfRemoved(it)
                        }
                        unhook[0]?.unhook()
                    }
                }
            }
        )
    }

    override fun createHooks(lp: XC_LoadPackage.LoadPackageParam): List<XC_MethodHook.Unhook> {
        val unhooks = arrayListOf<Unhook>()
        val pmsClass = XposedHelpers.findClass(
            "com.android.server.pm.PackageManagerService", lp.classLoader
        )
        if (XSharedPrefs.isDevMode) {
            LogUtils.log("!!!!!!!! in dev mode !!!!!!!!!")

        }
        val hookCallback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                LogUtils.log("${param.method.name} versionedPackage = ${param.args[0]}")
                (param.args[2] as? Int)?.let { userId ->
                    LogUtils.log("userId = $userId")
                }

                XposedHelpers.setObjectField(
                    param.args[0] /* VersionedPackage */, "mPackageName", null
                )
            }
        }
        if (XSharedPrefs.isBanUninstall) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                findAndHookFirst(pmsClass, "deletePackage", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val packageName = param.args[0] as String
                        val userId = param.args[2] as Int
                        val isUseBannedList = XSharedPrefs.isUseBannedList
                        when {
                            isUseBannedList && TransactService.contains(
                                packageName,
                                userId
                            ) -> {
                                LogUtils.log("${param.method.name}: pkg: $packageName:$userId (in bannedList) -> set packageName to null")
                                param.args[0] = null
                            }

                            !isUseBannedList -> {
                                LogUtils.log("${param.method.name}: pkg: $packageName:$userId -> set packageName to null")
                                param.args[0] = null
                            }

                            else -> {}
                        }
                    }
                })
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                findAndHookFirst(
                    pmsClass, "deletePackageVersioned", hookCallback
                )?.let { unhooks.add(it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                findAndHookFirst(pmsClass, "deleteExistingPackageAsUser", hookCallback)?.let {
                    unhooks.add(it)
                }
            }
        }

        if (XSharedPrefs.isBanClearData) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                findAndHookFirst(
                    pmsClass,
                    "clearExternalStorageDataSync",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val packageName = param.args[0] as String
                            val userId = param.args[1] as Int
                            val isUseBannedList = XSharedPrefs.isUseBannedList
                            when {
                                isUseBannedList && TransactService.contains(
                                    packageName,
                                    userId
                                ) -> {
                                    LogUtils.log("${param.method.name}: pkg: $packageName:$userId (in bannedList) -> false")
                                    param.result = null
                                }

                                !isUseBannedList -> {
                                    LogUtils.log("${param.method.name}: pkg: $packageName:$userId -> false")
                                    param.result = null
                                }

                                else -> {}
                            }
                        }
                    }
                )?.let { unhooks.add(it) }
            }
            findAndHookFirst(pmsClass, "clearApplicationUserData", object : XC_MethodHook() {
                private val Any.handler by lazy {
                    this::class.java.getDeclaredField("mHandler")
                        .also { it.isAccessible = true }
                        .get(this) as? Handler
                }

                override fun beforeHookedMethod(param: MethodHookParam) {
                    val packageName = param.args[0] as String
                    val observer = param.args[1] as? IPackageDataObserver
                    val userId = param.args[2] as Int
                    val isUseBannedList = XSharedPrefs.isUseBannedList
                    when {
                        isUseBannedList && TransactService.contains(packageName, userId) -> {
                            LogUtils.log("${param.method.name}: pkg: $packageName:$userId (in bannedList) -> return early")
                            param.thisObject.handler?.let {
                                it.post {
                                    LogUtils.log("invoke observer#onRemoveCompleted($packageName, false)")
                                    observer?.onRemoveCompleted(packageName, false)
                                }
                            }
                            param.result = null
                        }

                        !isUseBannedList -> {
                            LogUtils.log("${param.method.name}: pkg: $packageName:$userId -> return early")
                            param.thisObject.handler?.let {
                                it.post {
                                    LogUtils.log("invoke observer#onRemoveCompleted($packageName, false)")
                                    observer?.onRemoveCompleted(packageName, false)
                                }
                            }
                            param.result = null
                        }

                        else -> {}
                    }
                }
            })?.let { unhooks.add(it) }
        }
        return unhooks
    }

    override fun init(lp: XC_LoadPackage.LoadPackageParam) {
        XSharedPrefs.init()
        XSharedPrefs.registerPrefsChangeListener {
            LogUtils.log("prefs reloaded")
            unhook()
            startHook(lp)
        }
    }

    override val targetPackageName: String
        get() = "android"
    override val targetProcessName: String
        get() = "android"
}