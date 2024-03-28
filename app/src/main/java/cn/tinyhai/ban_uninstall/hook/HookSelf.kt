package cn.tinyhai.ban_uninstall.hook

import android.system.Os
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.utils.LogUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

class HookSelf : BaseOneshotHooker() {
    override val name: String
        get() = "HookSelf"

    override fun createOneshotHook(lp: XC_LoadPackage.LoadPackageParam) {
        val xpTag = try {
            val tag = XposedBridge::class.java.getDeclaredField("TAG").get(null)?.toString()
            when (tag) {
                "LSPosed-Bridge" -> "LSPosed"
                "Xposed" -> tag
                else -> null
            }
        } catch (th: Throwable) {
            LogUtils.log(th)
            null
        }
        val appClass = lp.classLoader.loadClass("${BuildConfig.APPLICATION_ID}.App")
        findAndHookFirst(
            appClass,
            "getXpTag",
            XC_MethodReplacement.returnConstant(xpTag ?: "Unknown")
        )
        if (XposedBridge.getXposedVersion() > 92) {
            val unhook = arrayOf<Unhook?>(null)
            unhook[0] = findAndHookFirst(
                "android.app.ContextImpl",
                lp.classLoader,
                "getPreferencesDir",
                object : XC_MethodHook(PRIORITY_HIGHEST) {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dir = param.result as? File ?: return
                        LogUtils.log("file: ${dir.absoluteFile}")
                        if (dir.isDirectory && dir.name == BuildConfig.APPLICATION_ID) {
                            if (!dir.exists()) {
                                dir.mkdir()
                            }
                            val prev = Os.umask("777".toInt(8))
                            LogUtils.log(prev.toString(8))
                            Os.chmod(dir.absolutePath, "715".toInt(8))
                            Os.umask(prev)
                            LogUtils.log("set prefs dir world readable success")
                            unhook[0]?.unhook()
                        }
                    }
                }
            )
        } else {
            val unhook = arrayOf<Unhook?>(null)
            unhook[0] = findAndHookFirst(
                "android.app.ContextImpl",
                lp.classLoader,
                "checkMode",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args[0] as Int and 0x0001 /* Context.MODE_WORLD_READABLE */ != 0) {
                            param.throwable = null
                            unhook[0]?.unhook()
                        }
                    }
                }
            )
        }
    }

    override val targetPackageName: String
        get() = BuildConfig.APPLICATION_ID
    override val targetProcessName: String
        get() = BuildConfig.APPLICATION_ID
}