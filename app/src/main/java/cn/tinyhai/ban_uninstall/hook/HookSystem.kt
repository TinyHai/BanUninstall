package cn.tinyhai.ban_uninstall.hook

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import cn.tinyhai.ban_uninstall.hook.callback.afterMethod
import cn.tinyhai.ban_uninstall.hook.callback.beforeMethod
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.transact.server.TransactService
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val launchActivityItemClass = XposedHelpers.findClass(
                "android.app.servertransaction.LaunchActivityItem",
                lp.classLoader
            )
            findAndHookFirst(launchActivityItemClass, "obtain", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args[0] as Intent
                    val aInfo = param.args[2] as ActivityInfo
                    val userId = aInfo.applicationInfo.uid / 100_000
                    TransactClient.injectBinderIfNeeded(TransactService, intent, userId)
                }
            })
        }
        val callback = beforeMethod { param ->
            val intent = param.args[0] as Intent
            val aInfo = param.args[3] as ActivityInfo
            val userId = aInfo.applicationInfo.uid / 100_000
            TransactClient.injectBinderIfNeeded(TransactService, intent, userId)
        }
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.P) {
            val appThreadProxyClass = XposedHelpers.findClass(
                "android.app.IApplicationThread\$Stub\$Proxy",
                lp.classLoader
            )
            findAndHookFirst(appThreadProxyClass, "scheduleLaunchActivity", callback)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val appThreadNative =
                XposedHelpers.findClass("android.app.ApplicationThreadNative", lp.classLoader)
            findAndHookFirst(appThreadNative, "scheduleLaunchActivity", callback)
        }
    }

    override val targetPackageName: String
        get() = "android"
    override val targetProcessName: String
        get() = "android"
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
            afterMethod { param ->
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
        )
    }

    override fun createHooks(lp: XC_LoadPackage.LoadPackageParam): List<XC_MethodHook.Unhook> {
        val unhooks = arrayListOf<Unhook>()
        if (XSharedPrefs.isDevMode) {
            LogUtils.log("!!!!!!!! in dev mode !!!!!!!!!")

        }
        if (XSharedPrefs.isBanUninstall) {
            PMSUninstallHookCompat(this).createHooks(lp).let { unhooks.addAll(it) }
        }

        if (XSharedPrefs.isBanClearData) {
            PMSClearDataHookCompat(this).createHooks(lp).let { unhooks.addAll(it) }
        }
        return unhooks
    }

    override fun init(lp: XC_LoadPackage.LoadPackageParam) {
        if (!isInterest(lp)) {
            return
        }
        LogUtils.log("OS Version: ${Build.VERSION.SDK_INT}")
        LogUtils.log("OS Manufacturer: ${Build.MANUFACTURER}")
        LogUtils.log("OS Model: ${Build.MODEL}")
        LogUtils.log("XP Version: ${XposedBridge.getXposedVersion()}")
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