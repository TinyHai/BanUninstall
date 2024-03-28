package cn.tinyhai.ban_uninstall

import cn.tinyhai.ban_uninstall.hook.*
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
        val packageName = lpparam.packageName
        val processName = lpparam.processName
        hookers.filterIsInstance<OneshotHooker>().filter {
            it.targetPackageName == packageName
        }.filter {
            it.targetProcessName == null || it.targetProcessName == processName
        }.forEach {
            it.startOneshotHook(lpparam)
        }
        hookers.filterIsInstance<UnhookableHooker>().filter {
            it.targetPackageName == packageName
        }.filter {
            it.targetProcessName == null || it.targetProcessName == processName
        }.forEach {
            it.init(lpparam)
            it.startHook(lpparam)
        }
    }
}