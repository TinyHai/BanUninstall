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

    fun isInterest(lp: LoadPackageParam): Boolean {
        return lp.packageName == targetPackageName && (targetProcessName == null || lp.processName == targetProcessName)
    }

    fun init(lp: LoadPackageParam)
    fun startHook(lp: LoadPackageParam)

    fun runCatchingWithLog(block: () -> Unit) {
        runCatching(block).onFailure {
            LogUtils.log("$name error")
            LogUtils.log(it)
        }.onSuccess {
            LogUtils.log("$name success")
        }
    }
}

interface UnhookableHooker : Hooker {
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
            LogUtils.log("hook $methodName failed !!!!!")
        }
        return hooker
    }

    fun findAndHookFirst(
        className: String, classLoader: ClassLoader, methodName: String, callback: XC_MethodHook
    ): Unhook? {
        val clazz = classLoader.loadClass(className)
        return findAndHookFirst(clazz, methodName, callback)
    }

    fun MutableList<Unhook>.findAndAddHook(
        clazz: Class<*>,
        methodName: String,
        callback: XC_MethodHook
    ) {
        findAndHookFirst(clazz, methodName, callback)?.also { add(it) }
    }
}

abstract class BaseOneshotHooker : OneshotHooker, HookerHelper {

    private var hooked = false

    override fun init(lp: LoadPackageParam) {}

    override fun startHook(lp: LoadPackageParam) {
        startOneshotHook(lp)
    }

    override fun startOneshotHook(lp: LoadPackageParam) {
        if (hooked || !isInterest(lp)) {
            return
        }
        hooked = true
        runCatchingWithLog {
            LogUtils.log("startOneshotHook >>>>")
            createOneshotHook(lp)
            LogUtils.log("startOneshotHook <<<<")
        }
    }

    abstract fun createOneshotHook(lp: LoadPackageParam)
}

abstract class BaseUnhookableHooker : OneshotHooker, UnhookableHooker, HookerHelper {

    private val unhooks: ArrayList<Unhook> = ArrayList()

    override fun init(lp: LoadPackageParam) {}

    override fun startHook(lp: LoadPackageParam) {
        if (!isInterest(lp)) {
            return
        }
        runCatchingWithLog {
            unhooks.addAll(createHooks(lp))
        }
    }

    override fun startOneshotHook(lp: LoadPackageParam) {
        if (!isInterest(lp)) {
            return
        }
        runCatchingWithLog {
            LogUtils.log("startOneshotHook >>>>")
            createOneshotHook(lp)
            LogUtils.log("startOneshotHook <<<<")
        }
    }

    abstract fun createOneshotHook(lp: LoadPackageParam)

    abstract fun createHooks(lp: LoadPackageParam): List<Unhook>

    override fun unhook() {
        unhooks.forEach { it.unhook() }
    }
}