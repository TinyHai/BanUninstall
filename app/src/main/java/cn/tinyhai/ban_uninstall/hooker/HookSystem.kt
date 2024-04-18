package cn.tinyhai.ban_uninstall.hooker

import android.content.Context
import android.content.pm.IPackageDataObserver
import android.content.pm.IPackageDeleteObserver2
import android.content.pm.VersionedPackage
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.ban_uninstall.utils.XSharedPrefs
import cn.tinyhai.xp.annotation.*
import cn.tinyhai.xp.hook.HookLaunchItemForInjectClientImpl
import cn.tinyhai.xp.hook.Hooker
import cn.tinyhai.xp.hook.UnhookableHooker
import cn.tinyhai.xp.hook.logger.XPLogger
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

@HookScope(
    targetPackageName = "android",
    targetProcessName = "android",
    isUnhookable = true
)
class HookSystem(
    private val logger: XPLogger
) {

    @InjectHooker
    lateinit var hooker: Hooker

    private lateinit var lp: XC_LoadPackage.LoadPackageParam

    @Initiate
    fun initiate(lp: XC_LoadPackage.LoadPackageParam) {
        this.lp = lp
        logger.info("OS Version: ${Build.VERSION.SDK_INT}")
        logger.info("OS Manufacturer: ${Build.MANUFACTURER}")
        logger.info("OS Model: ${Build.MODEL}")
        logger.info("XP Version: ${XposedBridge.getXposedVersion()}")
        XSharedPrefs.init()
        XSharedPrefs.registerPrefsChangeListener {
            logger.info("prefs reloaded")
            (hooker as? UnhookableHooker)?.let {
                it.unhook()
                it.startHook(lp)
            }
        }
    }

    @HookerGate
    fun isHookerEnable(id: String): Boolean {
        return when (id) {
            App.SP_KEY_BAN_UNINSTALL -> XSharedPrefs.isBanUninstall
            App.SP_KEY_BAN_CLEAR_DATA -> XSharedPrefs.isBanClearData
            else -> false
        }
    }

    @Oneshot(unhookable = true)
    @MethodHooker(
        className = "com.android.server.SystemServiceManager",
        methodName = "startBootPhase",
        hookType = HookType.AfterMethod
    )
    fun afterStartBootPhase(param: MethodHookParam, unhook: () -> Unit) {
        val phase = param.args.lastOrNull() as? Int ?: 0
        if (phase == 1000 /* PHASE_BOOT_COMPLETED */) {
            TransactService.onSystemBootCompleted()

            HookLaunchItemForInjectClientImpl(logger).startHook(lp)

            val activityThread =
                XposedHelpers.findClass("android.app.ActivityThread", lp.classLoader)
                    .getDeclaredField("sCurrentActivityThread")
                    .also { it.isAccessible = true }.get(null)
            val systemContext =
                activityThread::class.java.getDeclaredField("mSystemContext")
                    .also { it.isAccessible = true }.get(activityThread) as? Context
            logger.info("systemContext: $systemContext")
            systemContext?.let {
                XSharedPrefs.listenSelfRemoved(it)
            }
            unhook()
        }
    }

    private var pmsHandler: Handler? = null

    private fun getPMSHandler(obj: Any): Handler? {
        return pmsHandler ?: run {
            val outer = runCatching { obj::class.java.getDeclaredField("this\$0") }
                .onSuccess { it.isAccessible = true }
                .getOrNull()?.get(obj)
            val pms = outer ?: obj
            runCatching {
                pms::class.java.getDeclaredField("mHandler")
            }.onSuccess {
                it.isAccessible = true
            }.getOrNull()?.get(pms) as? Handler
        }.also {
            pmsHandler = it
        }
    }

    @HookerId(App.SP_KEY_BAN_UNINSTALL)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService",
        methodName = "deletePackage",
        hookType = HookType.BeforeMethod,
        maxSdkExclusive = Build.VERSION_CODES.O
    )
    fun beforeDeletePackage(param: MethodHookParam) {
        val packageName = param.args[0] as String
        val observer2 = param.args[1] as? IPackageDeleteObserver2
        val userId = param.args[2] as Int
        val isUseBannedList = XSharedPrefs.isUseBannedList
        logger.info("${param.method.name}(packageName: $packageName, observer2: ${observer2.hashCode()} userId: $userId)")
        when {
            isUseBannedList && TransactService.contains(
                packageName,
                userId
            ) -> {
                logger.info("(in bannedList) -> return early")
                observer2?.let {
                    getPMSHandler(param.thisObject)?.post {
                        logger.info("invoke observer2#onPackageDeleted($packageName, -1, null)")
                        it.onPackageDeleted(packageName, -1, null)
                    }
                }
                param.args[0] = null
            }

            !isUseBannedList -> {
                logger.info("(all) -> return early")
                observer2?.let {
                    getPMSHandler(param.thisObject)?.post {
                        logger.info("invoke observer2#onPackageDeleted($packageName, -1, null)")
                        it.onPackageDeleted(packageName, -1, null)
                    }
                }
                param.args[0] = null
            }

            else -> {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleDeletePackage(param: MethodHookParam) {
        val versionedPackage = param.args[0] as VersionedPackage
        val packageName = versionedPackage.packageName
        val observer2 = param.args[1] as? IPackageDeleteObserver2
        val userId = param.args[2] as Int
        val isUseBannedList = XSharedPrefs.isUseBannedList
        logger.info("${param.method.name}(packageName: $packageName, observer2: ${observer2.hashCode()} userId: $userId)")
        when {
            isUseBannedList && TransactService.contains(
                packageName,
                userId
            ) -> {
                logger.info("(in bannedList) -> return early")
                observer2?.let {
                    getPMSHandler(param.thisObject)?.post {
                        logger.info("invoke observer2#onPackageDeleted($packageName, -1, null)")
                        it.onPackageDeleted(packageName, -1, null)
                    }
                }
                param.result = null
            }

            !isUseBannedList -> {
                logger.info("(all) -> return early")
                observer2?.let {
                    getPMSHandler(param.thisObject)?.post {
                        logger.info("invoke observer2#onPackageDeleted($packageName, -1, null)")
                        it.onPackageDeleted(packageName, -1, null)
                    }
                }
                param.result = null
            }

            else -> {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @HookerId(App.SP_KEY_BAN_UNINSTALL)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService",
        methodName = "deleteExistingPackageAsUser",
        minSdkInclusive = Build.VERSION_CODES.R
    )
    fun beforeDeleteExistingPackageAsUser(param: MethodHookParam) {
        handleDeletePackage(param)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @HookerId(App.SP_KEY_BAN_UNINSTALL)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService",
        methodName = "deletePackageVersioned",
        minSdkInclusive = Build.VERSION_CODES.O
    )
    fun beforeDeletePackageVersioned(param: MethodHookParam) {
        handleDeletePackage(param)
    }

    private fun handleClearApplicationUserData(param: MethodHookParam) {
        val packageName = param.args[0] as String
        val observer = param.args[1] as? IPackageDataObserver
        val userId = param.args[2] as Int
        logger.info("${param.method.name}(packageName: $packageName, observer: ${observer.hashCode()}, userId: $userId)")
        val isUseBannedList = XSharedPrefs.isUseBannedList
        when {
            isUseBannedList && TransactService.contains(packageName, userId) -> {
                logger.info("(in bannedList) -> return early")
                observer?.let {
                    getPMSHandler(param.thisObject)?.post {
                        logger.info("invoke observer#onRemoveCompleted($packageName, false)")
                        it.onRemoveCompleted(packageName, false)
                    }
                }
                param.result = null
            }

            !isUseBannedList -> {
                logger.info("(all) -> return early")
                observer?.let {
                    getPMSHandler(param.thisObject)?.post {
                        logger.info("invoke observer#onRemoveCompleted($packageName, false)")
                        it.onRemoveCompleted(packageName, false)
                    }
                }
                param.result = null
            }

            else -> {}
        }
    }

    @HookerId(App.SP_KEY_BAN_CLEAR_DATA)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService",
        methodName = "clearApplicationUserData",
        maxSdkExclusive = Build.VERSION_CODES.TIRAMISU
    )
    fun beforeClearApplicationUserData0(param: MethodHookParam) {
        handleClearApplicationUserData(param)
    }

    @HookerId(App.SP_KEY_BAN_CLEAR_DATA)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
        methodName = "clearApplicationUserData",
        minSdkInclusive = Build.VERSION_CODES.TIRAMISU
    )
    fun beforeClearApplicationUserData1(param: MethodHookParam) {
        handleClearApplicationUserData(param)
    }
}