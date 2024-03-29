package cn.tinyhai.ban_uninstall.hook

import android.content.Context
import android.content.Intent
import android.os.Build
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.transactor.Transactor
import cn.tinyhai.ban_uninstall.utils.LogUtils
import cn.tinyhai.ban_uninstall.utils.XSharedPrefs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedBridge
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
                Transactor.prepareIntent(intent)
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
                        Transactor.onSystemBootCompleted()
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
                XposedBridge.log("${param.method.name} versionedPackage = ${param.args[0]}")
                XposedHelpers.setObjectField(
                    param.args[0] /* VersionedPackage */, "mPackageName", null
                )
            }
        }
        if (XSharedPrefs.isBanUninstall) {
            findAndHookFirst(
                pmsClass, "deletePackageVersioned", hookCallback
            )?.let { unhooks.add(it) }
            findAndHookFirst(pmsClass, "deleteExistingPackageAsUser", hookCallback)?.let {
                unhooks.add(it)
            }
        }

        if (XSharedPrefs.isBanClearData) {
            findAndHookFirst(pmsClass, "clearApplicationUserDataLIF", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    LogUtils.log("${param.method.name} -> false")
                    param.result = false
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