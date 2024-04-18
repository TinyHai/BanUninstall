package cn.tinyhai.ban_uninstall

import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import cn.tinyhai.xp.hook.HookerManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage


class XposedInit : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookerManager(XPLogUtils).startHook(lpparam)
    }
}