package cn.tinyhai.ban_uninstall.hook

import android.content.pm.IPackageDataObserver
import android.content.pm.IPackageDeleteObserver2
import android.content.pm.VersionedPackage
import android.os.Build
import androidx.annotation.RequiresApi
import cn.tinyhai.ban_uninstall.hook.callback.beforeMethodWithPost
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.ban_uninstall.utils.LogUtils
import cn.tinyhai.ban_uninstall.utils.XSharedPrefs
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

abstract class HookCompat(private val helper: HookerHelper) : HookerHelper by helper {
    abstract fun createHooks(lp: XC_LoadPackage.LoadPackageParam): List<Unhook>
}

private const val PMS_CLASS_NAME = "com.android.server.pm.PackageManagerService"
private const val PMI_CLASS_NAME = "$PMS_CLASS_NAME\$IPackageManagerImpl"

class PMSUninstallHookCompat(helper: HookerHelper) : HookCompat(helper) {

    override fun createHooks(lp: XC_LoadPackage.LoadPackageParam): List<Unhook> {
        val unhooks = mutableListOf<Unhook>()
        val pmsClass = XposedHelpers.findClass(PMS_CLASS_NAME, lp.classLoader)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            createHooksBelowO(unhooks, pmsClass)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createHooksAboveOrEqualO(unhooks, pmsClass)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createHooksAboveOrEqualR(unhooks, pmsClass)
        }

        return unhooks
    }

    private fun createHooksBelowO(unhooks: MutableList<Unhook>, pmsClass: Class<*>) {
        unhooks.findAndAddHook(pmsClass, "deletePackage", beforeMethodWithPost { param, post ->
            val packageName = param.args[0] as String
            val observer2 = param.args[1] as? IPackageDeleteObserver2
            val userId = param.args[2] as Int
            val isUseBannedList = XSharedPrefs.isUseBannedList
            LogUtils.log("${param.method.name}(packageName: $packageName, observer2: ${observer2.hashCode()} userId: $userId)")
            when {
                isUseBannedList && TransactService.contains(
                    packageName,
                    userId
                ) -> {
                    LogUtils.log("(in bannedList) -> return early")
                    observer2?.let {
                        post {
                            LogUtils.log("invoke observer2#onPackageDeleted($packageName, -1, null)")
                            it.onPackageDeleted(packageName, -1, null)
                        }
                    }
                    param.args[0] = null
                }

                !isUseBannedList -> {
                    LogUtils.log("(all) -> return early")
                    observer2?.let {
                        post {
                            LogUtils.log("invoke observer2#onPackageDeleted($packageName, -1, null)")
                            it.onPackageDeleted(packageName, -1, null)
                        }
                    }
                    param.args[0] = null
                }

                else -> {}
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val pmsCallback = beforeMethodWithPost { param, post ->
        val versionedPackage = param.args[0] as VersionedPackage
        val packageName = versionedPackage.packageName
        val observer2 = param.args[1] as? IPackageDeleteObserver2
        val userId = param.args[2] as Int
        val isUseBannedList = XSharedPrefs.isUseBannedList
        LogUtils.log("${param.method.name}(packageName: $packageName, observer2: ${observer2.hashCode()} userId: $userId)")
        when {
            isUseBannedList && TransactService.contains(
                packageName,
                userId
            ) -> {
                LogUtils.log("(in bannedList) -> return early")
                observer2?.let {
                    post {
                        LogUtils.log("invoke observer2#onPackageDeleted($packageName, -1, null)")
                        it.onPackageDeleted(packageName, -1, null)
                    }
                }
                param.result = null
            }

            !isUseBannedList -> {
                LogUtils.log("(all) -> return early")
                observer2?.let {
                    post {
                        LogUtils.log("invoke observer2#onPackageDeleted($packageName, -1, null)")
                        it.onPackageDeleted(packageName, -1, null)
                    }
                }
                param.result = null
            }

            else -> {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createHooksAboveOrEqualO(unhooks: MutableList<Unhook>, pmsClass: Class<*>) {
        unhooks.findAndAddHook(pmsClass, "deletePackageVersioned", pmsCallback)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createHooksAboveOrEqualR(unhooks: MutableList<Unhook>, pmsClass: Class<*>) {
        unhooks.findAndAddHook(pmsClass, "deleteExistingPackageAsUser", pmsCallback)
    }
}

class PMSClearDataHookCompat(helper: HookerHelper) : HookCompat(helper) {
    override fun createHooks(lp: XC_LoadPackage.LoadPackageParam): List<Unhook> {
        val unhooks = mutableListOf<Unhook>()
        val pmsClass = XposedHelpers.findClass(PMS_CLASS_NAME, lp.classLoader)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            createHooksBelowTIRAMISU(unhooks, pmsClass)
        }
        val pmiClass =
            XposedHelpers.findClass(PMI_CLASS_NAME, lp.classLoader)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createHooksAboveOrEqualTIRAMISU(unhooks, pmiClass)
        }
        return unhooks
    }

    private val callback = beforeMethodWithPost { param, post ->
        val packageName = param.args[0] as String
        val observer = param.args[1] as? IPackageDataObserver
        val userId = param.args[2] as Int
        LogUtils.log("${param.method.name}(packageName: $packageName, observer: ${observer.hashCode()}, userId: $userId)")
        val isUseBannedList = XSharedPrefs.isUseBannedList
        when {
            isUseBannedList && TransactService.contains(packageName, userId) -> {
                LogUtils.log("(in bannedList) -> return early")
                observer?.let {
                    post {
                        LogUtils.log("invoke observer#onRemoveCompleted($packageName, false)")
                        it.onRemoveCompleted(packageName, false)
                    }
                }
                param.result = null
            }

            !isUseBannedList -> {
                LogUtils.log("(all) -> return early")
                observer?.let {
                    post {
                        LogUtils.log("invoke observer#onRemoveCompleted($packageName, false)")
                        it.onRemoveCompleted(packageName, false)
                    }
                }
                param.result = null
            }

            else -> {}
        }
    }

    private fun createHooksBelowTIRAMISU(unhooks: MutableList<Unhook>, pmsClass: Class<*>) {
        unhooks.findAndAddHook(pmsClass, "clearApplicationUserData", callback)
    }

    private fun createHooksAboveOrEqualTIRAMISU(unhooks: MutableList<Unhook>, pmiClass: Class<*>) {
        unhooks.findAndAddHook(pmiClass, "clearApplicationUserData", callback)
    }
}