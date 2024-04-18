package cn.tinyhai.ban_uninstall.hooker

import android.system.Os
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.xp.annotation.*
import cn.tinyhai.xp.hook.logger.XPLogger
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File

@HookScope(
    "cn.tinyhai.ban_uninstall",
    "cn.tinyhai.ban_uninstall"
)
class HookSelf(
    private val logger: XPLogger
) {

    companion object {
        private const val ID_GET_XP_TAG = "get_xp_tag"
        private const val ID_CHECK_MODE = "check_mode"
        private const val ID_GET_PREF_DIR = "get_pref_dir"
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
            ID_GET_PREF_DIR -> XposedBridge.getXposedVersion() > 92
            else -> true
        }
    }

    @HookerId(ID_GET_XP_TAG)
    @MethodHooker(
        className = "cn.tinyhai.ban_uninstall.App",
        methodName = "getXpTag",
        hookType = HookType.ReplaceMethod
    )
    fun replaceGetXpTag(param: MethodHookParam): Any? {
        val xpTag = try {
            val tag = XposedBridge::class.java.getDeclaredField("TAG").get(null)?.toString()
            when (tag) {
                "LSPosed-Bridge" -> "LSPosed"
                "Xposed" -> tag
                else -> "Unknown"
            }
        } catch (th: Throwable) {
            logger.error(th)
            "Unknown"
        }
        return xpTag
    }

    @Oneshot(unhookable = true)
    @HookerId(ID_GET_PREF_DIR)
    @MethodHooker(
        className = "android.app.ContextImpl",
        methodName = "getPreferencesDir",
        hookType = HookType.AfterMethod
    )
    fun afterGetPreference(param: MethodHookParam, unhook: () -> Unit) {
        val dir = param.result as? File ?: return
        logger.info("file: ${dir.absoluteFile}")
        if (dir.isDirectory && dir.name == BuildConfig.APPLICATION_ID) {
            if (!dir.exists()) {
                dir.mkdir()
            }
            val prev = Os.umask("777".toInt(8))
            logger.info("prev umask: ${prev.toString(8).padStart(4, '0')}")
            Os.chmod(dir.absolutePath, "715".toInt(8))
            Os.umask(prev)
            logger.info("set prefs dir world readable success")
            unhook()
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

