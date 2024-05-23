package cn.tinyhai.ban_uninstall.hooker

import android.content.pm.*
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Process
import androidx.annotation.RequiresApi
import cn.tinyhai.ban_uninstall.XposedInit
import cn.tinyhai.ban_uninstall.auth.entities.OpResult
import cn.tinyhai.ban_uninstall.auth.server.AuthService
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.ban_uninstall.utils.SPHost
import cn.tinyhai.ban_uninstall.utils.SystemContextHolder
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import cn.tinyhai.ban_uninstall.utils.XSharedPrefs
import cn.tinyhai.xp.annotation.*
import cn.tinyhai.xp.hook.Hooker
import cn.tinyhai.xp.hook.UnhookableHooker
import cn.tinyhai.xp.hook.logger.XPLogger
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

@HookScope(
    targetPackageName = PKG_ANDROID,
    targetProcessName = PKG_ANDROID,
    isUnhookable = true
)
class HookSystem(
    private val logger: XPLogger
) {

    @InjectHooker
    lateinit var hooker: Hooker

    @Initiate
    fun initiate(lp: XC_LoadPackage.LoadPackageParam) {
        logger.info("OS Version: ${Build.VERSION.SDK_INT}")
        logger.info("OS Manufacturer: ${Build.MANUFACTURER}")
        logger.info("OS Model: ${Build.MODEL}")
        logger.info("ActiveMode: ${XposedInit.activeMode.name}")
        logger.info("XP Version: ${XposedBridge.getXposedVersion()}")
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
            SPHost.SP_KEY_BAN_UNINSTALL -> XSharedPrefs.isBanUninstall
            SPHost.SP_KEY_BAN_CLEAR_DATA -> XSharedPrefs.isBanClearData
            else -> false
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

    private fun PackageManager.getApplicationInfoAsUser(
        packageName: String,
        flags: Int,
        userId: Int
    ): ApplicationInfo {
        val method = this::class.java.getDeclaredMethod(
            "getApplicationInfoAsUser",
            packageName::class.java,
            flags::class.javaPrimitiveType,
            userId::class.javaPrimitiveType
        )
        XPLogUtils.log("getApplicationInfoAsUser($packageName:$userId)")
        return method.invoke(
            this,
            packageName,
            flags,
            userId
        ) as ApplicationInfo
    }

    private fun isNotApp(pkgInfo: PkgInfo): Boolean {
        return getApplicationInfoAsUser(pkgInfo) == null
    }

    private fun getApplicationInfoAsUser(pkgInfo: PkgInfo): ApplicationInfo? {
        var appInfo: ApplicationInfo? = null
        SystemContextHolder.withSystemContext {
            appInfo = runCatching {
                packageManager.getApplicationInfoAsUser(pkgInfo.packageName, 0, pkgInfo.userId)
            }.getOrNull()
        }
        return appInfo
    }

    private fun getCallingPackageName(callingPid: Int): String {
        logger.info("getCallingPackageName($callingPid)")
        return runCatching {
            val processName =
                File("/proc/$callingPid/cmdline").readText().substringBefore(Char.MIN_VALUE)
            val colonIdx = processName.indexOf(':')
            if (colonIdx >= 0) {
                processName.substring(0, colonIdx)
            } else {
                processName
            }.also {
                XPLogUtils.log(it)
            }
        }.getOrDefault("")
    }

    @HookerId(SPHost.SP_KEY_BAN_UNINSTALL)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService",
        methodName = "deletePackage",
        hookType = HookType.BeforeMethod,
        maxSdkExclusive = Build.VERSION_CODES.O
    )
    fun beforeDeletePackage(param: MethodHookParam) {
        handleDeletePackage(param)
    }

    private fun handleDeletePackage(param: MethodHookParam) {
        val packageName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (param.args[0] as? VersionedPackage)?.packageName
        } else {
            param.args[0] as? String
        } ?: ""
        val observer2 = param.args[1] as? IPackageDeleteObserver2
        val userId = param.args[2] as Int
        logger.info("${param.method.name}(packageName: $packageName, observer2: ${observer2.hashCode()} userId: $userId)")
        val isUseBannedList = XSharedPrefs.isUseBannedList
        val isShowConfirm = XSharedPrefs.isShowConfirm
        logger.info("(isUseBannedList: $isUseBannedList, isShowConfirm: $isShowConfirm)")

        fun notifyObserver() {
            observer2?.let {
                getPMSHandler(param.thisObject)?.post {
                    logger.info("invoke observer2#onPackageDeleted($packageName, -1, null)")
                    it.onPackageDeleted(
                        packageName,
                        -1,
                        null
                    )
                }
            }
        }

        fun invokeOrigin() {
            runCatching {
                XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
            }.onSuccess {
                logger.info("uninstall success")
            }.onFailure {
                logger.error("uninstall failure", it)
            }
        }

        val pkgInfo = PkgInfo(packageName, userId)
        if (isNotApp(pkgInfo)) {
            logger.info("skip some static sharedlibraries; $pkgInfo")
            return
        }
        val callingUid = Binder.getCallingUid()
        val callingPackageName = getCallingPackageName(Binder.getCallingPid())

        fun onUninstall(result: OpResult) {
            AuthService.onUninstall(
                pkgInfo = pkgInfo,
                callingUid = callingUid,
                callingPackageName = callingPackageName,
                result = result
            )
        }
        when {
            !isUseBannedList -> {
                if (isShowConfirm) {
                    AuthService.showUninstallConfirm(
                        onConfirm = {
                            invokeOrigin()
                        },
                        onCancel = {
                            notifyObserver()
                            logger.info("prevent uninstall")
                        },
                        pkgInfo = pkgInfo,
                        callingUid = callingUid,
                        callingPackageName = callingPackageName
                    )
                } else {
                    notifyObserver()
                    onUninstall(OpResult.Prevented)
                }
                param.result = null
            }

            TransactService.contains(packageName, userId) -> {
                logger.info("${packageName}:$userId is in banned list -> return early")
                if (isShowConfirm) {
                    AuthService.showUninstallConfirm(
                        onConfirm = {
                            invokeOrigin()
                        },
                        onCancel = {
                            notifyObserver()
                            logger.info("prevent uninstall")
                        },
                        pkgInfo = pkgInfo,
                        callingUid = callingUid,
                        callingPackageName = callingPackageName
                    )
                } else {
                    notifyObserver()
                    onUninstall(OpResult.Prevented)
                }
                param.result = null
            }

            else -> {
                onUninstall(OpResult.Allowed)
                logger.info("$packageName:$userId is not in banned list -> pass")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @HookerId(SPHost.SP_KEY_BAN_UNINSTALL)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService",
        methodName = "deleteExistingPackageAsUser",
        minSdkInclusive = Build.VERSION_CODES.R
    )
    fun beforeDeleteExistingPackageAsUser(param: MethodHookParam) {
        handleDeletePackage(param)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @HookerId(SPHost.SP_KEY_BAN_UNINSTALL)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService",
        methodName = "deletePackageVersioned",
        minSdkInclusive = Build.VERSION_CODES.O
    )
    fun beforeDeletePackageVersioned(param: MethodHookParam) {
        handleDeletePackage(param)
    }

    private fun handleClearApplicationUserData(param: MethodHookParam, isFromAm: Boolean = false) {
        // calling from self, skip
        if (Binder.getCallingPid() == Process.myPid()) {
            return
        }
        val args = param.args
        val packageName = args[0] as String
        val observer = param.args[args.lastIndex - 1] as? IPackageDataObserver
        val userId = param.args[args.lastIndex] as Int
        logger.info("${param.method.name}(packageName: $packageName, observer: ${observer.hashCode()}, userId: $userId)")
        val isUseBannedList = XSharedPrefs.isUseBannedList
        val isShowConfirm = XSharedPrefs.isShowConfirm
        logger.info("(isUseBannedList: $isUseBannedList, isShowConfirm: $isShowConfirm, isFromAm: $isFromAm)")

        fun notifyObserver() {
            observer?.let {
                getPMSHandler(param.thisObject)?.post {
                    logger.info("invoke observer#onRemoveCompleted($packageName, false)")
                    it.onRemoveCompleted(packageName, false)
                }
            }
        }

        fun invokeOrigin() {
            runCatching {
                XposedBridge.invokeOriginalMethod(
                    param.method,
                    param.thisObject,
                    param.args
                )
            }.onSuccess {
                logger.info("clear success")
            }.onFailure {
                logger.error("clear failure", it)
            }
        }

        val pkgInfo = PkgInfo(packageName, userId)
        val callingUid = Binder.getCallingUid()
        val callingPackageName = getCallingPackageName(Binder.getCallingPid())

        fun onClearData(result: OpResult) {
            AuthService.onClearData(
                pkgInfo = pkgInfo,
                callingUid = callingUid,
                callingPackageName = callingPackageName,
                result = result
            )
        }
        when {
            !isUseBannedList -> {
                if (isShowConfirm) {
                    AuthService.showClearDataConfirm(
                        onConfirm = {
                            invokeOrigin()
                        },
                        onCancel = {
                            notifyObserver()
                            logger.info("prevent clear")
                        },
                        pkgInfo = pkgInfo,
                        callingUid = callingUid,
                        callingPackageName = callingPackageName
                    )
                } else {
                    notifyObserver()
                    onClearData(OpResult.Prevented)
                }
                param.result = if (isFromAm) true else null
            }

            TransactService.contains(packageName, userId) -> {
                logger.info("${packageName}:$userId is in banned list  -> return early")
                if (isShowConfirm) {
                    AuthService.showClearDataConfirm(
                        onConfirm = {
                            invokeOrigin()
                        },
                        onCancel = {
                            notifyObserver()
                            logger.info("prevent clear")
                        },
                        pkgInfo = pkgInfo,
                        callingUid = callingUid,
                        callingPackageName = callingPackageName
                    )
                } else {
                    notifyObserver()
                    onClearData(OpResult.Prevented)
                }
                param.result = if (isFromAm) true else null
            }

            else -> {
                onClearData(OpResult.Allowed)
                logger.info("${packageName}:$userId is not in banned list -> pass")
            }
        }
    }

    @HookerId(SPHost.SP_KEY_BAN_CLEAR_DATA)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService",
        methodName = "clearApplicationUserData",
        maxSdkExclusive = Build.VERSION_CODES.TIRAMISU
    )
    fun beforeClearApplicationUserData0(param: MethodHookParam) {
        handleClearApplicationUserData(param)
    }

    @HookerId(SPHost.SP_KEY_BAN_CLEAR_DATA)
    @MethodHooker(
        className = "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
        methodName = "clearApplicationUserData",
        minSdkInclusive = Build.VERSION_CODES.TIRAMISU
    )
    fun beforeClearApplicationUserData1(param: MethodHookParam) {
        handleClearApplicationUserData(param)
    }

    @HookerId(SPHost.SP_KEY_BAN_CLEAR_DATA)
    @MethodHooker(
        className = "com.android.server.am.ActivityManagerService",
        methodName = "clearApplicationUserData",
    )
    fun beforeClearApplicationUserData2(param: MethodHookParam) {
        handleClearApplicationUserData(param, true)
    }
}