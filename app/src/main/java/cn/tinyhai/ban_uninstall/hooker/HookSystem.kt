package cn.tinyhai.ban_uninstall.hooker

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.IPackageDataObserver
import android.content.pm.IPackageDeleteObserver2
import android.content.pm.VersionedPackage
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Process
import androidx.annotation.RequiresApi
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.XposedInit
import cn.tinyhai.ban_uninstall.auth.server.AuthService
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.ban_uninstall.utils.XSharedPrefs
import cn.tinyhai.xp.annotation.*
import cn.tinyhai.xp.hook.Hooker
import cn.tinyhai.xp.hook.UnhookableHooker
import cn.tinyhai.xp.hook.logger.XPLogger
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

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

    private lateinit var lp: XC_LoadPackage.LoadPackageParam

    @Initiate
    fun initiate(lp: XC_LoadPackage.LoadPackageParam) {
        this.lp = lp
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
            App.SP_KEY_BAN_UNINSTALL -> XSharedPrefs.isBanUninstall
            App.SP_KEY_BAN_CLEAR_DATA -> XSharedPrefs.isBanClearData
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

    @HookerId(App.SP_KEY_BAN_UNINSTALL)
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

        fun postObserver() {
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

        when {
            !isUseBannedList -> {
                if (isShowConfirm) {
                    AuthService.showUninstallConfirm(
                        onConfirm = {
                            invokeOrigin()
                        },
                        onCancel = {
                            postObserver()
                            logger.info("prevent uninstall")
                        },
                        pkgInfo = PkgInfo(packageName, userId),
                        callingUid = Binder.getCallingUid(),
                        callingPackageName = AuthService.getCallingPackageName(Binder.getCallingPid())
                    )
                } else {
                    postObserver()
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
                            postObserver()
                            logger.info("prevent uninstall")
                        },
                        pkgInfo = PkgInfo(packageName, userId),
                        callingUid = Binder.getCallingUid(),
                        callingPackageName = AuthService.getCallingPackageName(Binder.getCallingPid())
                    )
                } else {
                    postObserver()
                }
                param.result = null
            }

            else -> {
                logger.info("$packageName:$userId is not in banned list -> pass")
            }
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

        fun postObserver() {
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

        when {
            !isUseBannedList -> {
                if (isShowConfirm) {
                    AuthService.showClearDataConfirm(
                        onConfirm = {
                            invokeOrigin()
                        },
                        onCancel = {
                            postObserver()
                            logger.info("prevent clear")
                        },
                        pkgInfo = PkgInfo(packageName, userId),
                        callingUid = Binder.getCallingUid(),
                        callingPackageName = AuthService.getCallingPackageName(Binder.getCallingPid())
                    )
                } else {
                    postObserver()
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
                            postObserver()
                            logger.info("prevent clear")
                        },
                        pkgInfo = PkgInfo(packageName, userId),
                        callingUid = Binder.getCallingUid(),
                        callingPackageName = AuthService.getCallingPackageName(Binder.getCallingPid())
                    )
                } else {
                    postObserver()
                }
                param.result = if (isFromAm) true else null
            }

            else -> {
                logger.info("${packageName}:$userId is not in banned list -> pass")
            }
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

    @HookerId(App.SP_KEY_BAN_CLEAR_DATA)
    @MethodHooker(
        className = "com.android.server.am.ActivityManagerService",
        methodName = "clearApplicationUserData",
    )
    fun beforeClearApplicationUserData2(param: MethodHookParam) {
        handleClearApplicationUserData(param, true)
    }
}