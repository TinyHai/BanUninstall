package cn.tinyhai.ban_uninstall.hooker

import android.content.Context
import cn.tinyhai.ban_uninstall.XposedInit
import cn.tinyhai.ban_uninstall.receiver.PackageChangeReceiver
import cn.tinyhai.ban_uninstall.receiver.RestartMainReceiver
import cn.tinyhai.ban_uninstall.transact.entities.ActiveMode
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.ban_uninstall.utils.SystemContextHolder
import cn.tinyhai.xp.annotation.*
import cn.tinyhai.xp.hook.logger.XPLogger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

@HookScope(
    targetPackageName = PKG_ANDROID,
    targetProcessName = PKG_ANDROID
)
class HookGetSystemContext(
    private val logger: XPLogger
) {
    companion object {
        private const val ID_SYSTEM_SERVICE_MANAGER = "id_system_service_manager"
    }

    private lateinit var lp: XC_LoadPackage.LoadPackageParam

    @Initiate
    fun initiate(lp: XC_LoadPackage.LoadPackageParam) {
        this.lp = lp
    }

    @HookerGate
    fun hookerGate(hookerId: String): Boolean {
        return when (hookerId) {
            ID_SYSTEM_SERVICE_MANAGER -> shouldHookSystemServiceManager()
            else -> false
        }
    }

    private fun shouldHookSystemServiceManager(): Boolean {
        if (XposedInit.activeMode == ActiveMode.Xposed) {
            return true
        }

        onBootComplete()

        return false
    }

    @Oneshot(unhookable = true)
    @HookerId(ID_SYSTEM_SERVICE_MANAGER)
    @MethodHooker(
        className = "com.android.server.SystemServiceManager",
        methodName = "startBootPhase",
        hookType = HookType.AfterMethod
    )
    fun afterStartBootPhase(param: XC_MethodHook.MethodHookParam, unhook: () -> Unit) {
        val phase = param.args.lastOrNull() as? Int ?: 0
        if (phase == 1000 /* PHASE_BOOT_COMPLETED */) {
            onBootComplete()
            unhook()
        }
    }

    private fun onBootComplete() {
        TransactService.onSystemBootCompleted()
        val activityThread =
            XposedHelpers.findClass("android.app.ActivityThread", lp.classLoader)
                .getDeclaredField("sCurrentActivityThread")
                .also { it.isAccessible = true }.get(null)
        val systemContext =
            activityThread::class.java.getDeclaredField("mSystemContext")
                .also { it.isAccessible = true }.get(activityThread) as? Context
        logger.info("systemContext: $systemContext")
        systemContext?.let {
            SystemContextHolder.onSystemContext(it)
            PackageChangeReceiver().register(it)
            if (XposedInit.activeMode == ActiveMode.Root) {
                RestartMainReceiver.send(it)
            }
        }
    }
}