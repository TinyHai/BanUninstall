package cn.tinyhai.ban_uninstall.hooker

import cn.tinyhai.xp.annotation.*
import cn.tinyhai.xp.hook.logger.XPLogger
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@HookScope(
    PKG_SELF,
    PKG_SELF
)
class HookSelf(
    private val logger: XPLogger
) {

    companion object {
        private const val ID_CHECK_MODE = "check_mode"
    }

    private var skip = false

    @Initiate
    fun init(lp: LoadPackageParam) {
        skip = lp.appInfo.uid / 100_000 > 0
        if (skip) {
            logger.info("skip dual app")
        }
    }

    @HookerGate
    fun isHookerEnable(id: String): Boolean {
        return !skip && when (id) {
            ID_CHECK_MODE -> XposedBridge.getXposedVersion() <= 92
            else -> false
        }
    }

    @HookerId(ID_CHECK_MODE)
    @MethodHooker(
        className = "android.app.ContextImpl",
        methodName = "checkMode",
        hookType = HookType.AfterMethod
    )
    fun afterCheckMode(param: MethodHookParam) {
        if (param.args[0] as Int and 0x0001 /* Context.MODE_WORLD_READABLE */ != 0) {
            param.throwable = null
        }
    }
}

