package cn.tinyhai.xp.hook

import cn.tinyhai.xp.hook.logger.XPLogger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

interface Hooker {
    val name: String
    val targetPackageName: String
    val targetProcessName: String

    fun isInterest(lp: LoadPackageParam): Boolean {
        return lp.packageName == targetPackageName && (targetProcessName.isBlank() || lp.processName == targetProcessName)
    }

    fun init(lp: LoadPackageParam)
    fun startHook(lp: LoadPackageParam)
}

interface UnhookableHooker : Hooker {
    fun unhook()
}

interface OneshotHooker : Hooker {
    fun startOneshotHook(lp: LoadPackageParam)
}

interface HookerHelper {
    val logger: XPLogger

    fun findAndHookAll(clazz: Class<*>, methodName: String, callback: XC_MethodHook): List<Unhook> {
        return XposedBridge.hookAllMethods(clazz, methodName, callback).toList()
    }

    fun findAndHookAll(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        callback: XC_MethodHook
    ): List<Unhook> {
        return runCatching {
            findAndHookAll(classLoader.loadClass(className), methodName, callback)
        }.onFailure {
            logger.error(it)
        }.getOrElse { emptyList() }
    }

    fun findAndHookExact(
        clazz: Class<*>,
        methodName: String,
        vararg parametersAndCallback: Any
    ): Unhook? {
        return runCatching {
            XposedHelpers.findAndHookMethod(clazz, methodName, parametersAndCallback)
        }.onFailure {
            logger.error(it)
        }.getOrNull()
    }

    fun findAndHookExact(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        parametersAndCallback: Any
    ): Unhook? {
        return runCatching {
            findAndHookExact(classLoader.loadClass(className), methodName, parametersAndCallback)
        }.onFailure {
            logger.error(it)
        }.getOrNull()
    }

    fun findAndHookFirst(
        clazz: Class<*>, methodName: String, callback: XC_MethodHook
    ): Unhook? {
        val hooker = if (methodName == "") {
            XposedBridge.hookAllConstructors(clazz, callback).also {
                logger.verbose("hook ${clazz.canonicalName}#(all constructors) success")
            } as Unhook?
        } else {
            clazz.declaredMethods.firstOrNull { it.name == methodName }?.let {
                XposedBridge.hookMethod(it, callback).also {
                    logger.verbose("hook ${clazz.canonicalName}#$methodName success")
                }
            }

        }

        if (hooker == null) {
            logger.error("hook ${clazz.canonicalName}#$methodName failed !!!!!")
        }
        return hooker
    }

    fun findAndHookFirst(
        className: String, classLoader: ClassLoader, methodName: String, callback: XC_MethodHook
    ): Unhook? {
        return runCatching {
            findAndHookFirst(classLoader.loadClass(className), methodName, callback)
        }.onFailure {
            logger.error(it)
        }.getOrNull()
    }

    fun MutableList<Unhook>.hookAndAddFirst(
        clazz: Class<*>,
        methodName: String,
        callback: XC_MethodHook
    ) {
        findAndHookFirst(clazz, methodName, callback)?.also { add(it) }
    }

    fun MutableList<Unhook>.hookAndAddFirst(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        callback: XC_MethodHook
    ) {
        runCatching {
            findAndHookFirst(classLoader.loadClass(className), methodName, callback)?.also { add(it) }
        }.onFailure {
            logger.error(it)
        }
    }

    fun Hooker.runCatchingWithLog(block: () -> Unit) {
        logger.verbose("$name >>>>>>>>>>>>>>>>")
        runCatching(block).onFailure {
            logger.error("$name error", it)
        }
        logger.verbose("$name <<<<<<<<<<<<<<<<")
    }
}

abstract class BaseOneshotHooker(
    override val logger: XPLogger = XPLogger
) : OneshotHooker, HookerHelper {

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
            logger.verbose("startOneshotHook >>>>")
            createOneshotHook(lp)
            logger.verbose("startOneshotHook <<<<")
        }
    }

    abstract fun createOneshotHook(lp: LoadPackageParam)
}

abstract class BaseUnhookableHooker(
    override val logger: XPLogger = XPLogger
) : OneshotHooker, UnhookableHooker, HookerHelper {

    private val unhooks: ArrayList<Unhook> = ArrayList()

    override fun init(lp: LoadPackageParam) {}

    override fun startHook(lp: LoadPackageParam) {
        if (!isInterest(lp)) {
            return
        }
        runCatchingWithLog {
            logger.verbose("startHook >>>>")
            unhooks.addAll(createHooks(lp))
            logger.verbose("startHook <<<<")
        }
    }

    override fun startOneshotHook(lp: LoadPackageParam) {
        if (!isInterest(lp)) {
            return
        }
        runCatchingWithLog {
            logger.verbose("startOneshotHook >>>>")
            createOneshotHook(lp)
            logger.verbose("startOneshotHook <<<<")
        }
    }

    abstract fun createOneshotHook(lp: LoadPackageParam)

    abstract fun createHooks(lp: LoadPackageParam): List<Unhook>

    override fun unhook() {
        unhooks.forEach { it.unhook() }
    }
}