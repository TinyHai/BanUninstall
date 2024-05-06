package cn.tinyhai.ban_uninstall

import cn.tinyhai.ban_uninstall.transact.entities.ActiveMode
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import cn.tinyhai.xp.hook.HookerManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage


class XposedInit : IXposedHookLoadPackage {

    companion object {
        val activeMode by lazy {
            val cl = XposedInit::class.java.classLoader
            try {
                cl!!.loadClass("cn.tinyhai.xposed.meta_loader.LoaderEntry")
                ActiveMode.Root
            } catch (e: Exception) {
                XPLogUtils.log(e)
                ActiveMode.Xposed
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XPLogUtils.log("handleLoadPackage: ${lpparam.packageName}, ${lpparam.processName}")
        HookerManager(XPLogUtils).startHook(lpparam)
    }
}