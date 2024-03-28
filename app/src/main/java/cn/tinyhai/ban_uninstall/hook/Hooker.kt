package cn.tinyhai.ban_uninstall.hook

import cn.tinyhai.ban_uninstall.utils.LogUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

interface Hooker {
    val name: String
    val targetPackageName: String
    val targetProcessName: String?
}

interface UnhookableHooker : Hooker {
    fun init(lp: LoadPackageParam)
    fun startHook(lp: LoadPackageParam)
    fun unhook()
}

interface OneshotHooker : Hooker {
    fun startOneshotHook(lp: LoadPackageParam)
}

interface HookerHelper {
    fun findAndHookFirst(
        clazz: Class<*>, methodName: String, callback: XC_MethodHook
    ): Unhook? {
        val hooker = clazz.declaredMethods.firstOrNull { it.name == methodName }?.let {
            XposedBridge.hookMethod(it, callback).also {
                LogUtils.log("hook $methodName success")
            }
        }
        if (hooker == null) {
            LogUtils.log("hook $methodName failed")
        }
        return hooker
    }

    fun findAndHookFirst(
        className: String, classLoader: ClassLoader, methodName: String, callback: XC_MethodHook
    ): Unhook? {
        val clazz = classLoader.loadClass(className)
        return findAndHookFirst(clazz, methodName, callback)
    }
}

abstract class BaseOneshotHooker : OneshotHooker, HookerHelper {

    override fun startOneshotHook(lp: LoadPackageParam) {
        runCatchingWithLog {
            createOneshotHook(lp)
        }
    }

    abstract fun createOneshotHook(lp: LoadPackageParam)

    private inline fun runCatchingWithLog(block: () -> Unit) {
        runCatching(block).onFailure {
            LogUtils.log("$name error")
            LogUtils.log(it)
        }.onSuccess {
            LogUtils.log("$name success")
        }
    }
}

abstract class BaseUnhookableHooker : UnhookableHooker, OneshotHooker, HookerHelper {

    private val unhooks: ArrayList<Unhook> = ArrayList()

    override fun startHook(lp: LoadPackageParam) {
        runCatchingWithLog {
            unhooks.addAll(createHooks(lp))
        }
    }

    private inline fun runCatchingWithLog(block: () -> Unit) {
        runCatching(block).onFailure {
            LogUtils.log("$name error")
            LogUtils.log(it)
        }.onSuccess {
            LogUtils.log("$name success")
        }
    }

    override fun startOneshotHook(lp: LoadPackageParam) {
        runCatchingWithLog {
            createOneshotHook(lp)
        }
    }

    abstract fun createOneshotHook(lp: LoadPackageParam)

    abstract fun createHooks(lp: LoadPackageParam): List<Unhook>

    override fun unhook() {
        unhooks.forEach { it.unhook() }
    }
}