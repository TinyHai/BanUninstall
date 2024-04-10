package cn.tinyhai.ban_uninstall

import cn.tinyhai.ban_uninstall.hook.HookSelf
import cn.tinyhai.ban_uninstall.hook.HookSystem
import cn.tinyhai.ban_uninstall.hook.Hooker
import cn.tinyhai.ban_uninstall.hook.OneshotHooker
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage


class XposedInit : IXposedHookLoadPackage {

    private val hookers = ArrayList<Hooker>().apply {
        registerAllHooker()
    }

    private fun ArrayList<Hooker>.registerAllHooker() {
        add(HookSelf())
        add(HookSystem())
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (hooker in hookers) {
            if (!hooker.isInterest(lpparam)) {
                continue
            }
            hooker.init(lpparam)
            if (hooker is OneshotHooker) {
                hooker.startOneshotHook(lpparam)
            }
            hooker.startHook(lpparam)
        }
    }
}