package cn.tinyhai.ban_uninstall.utils

import de.robv.android.xposed.XposedBridge

object LogUtils {
    private const val PREFIX = "[Ban Uninstall] >>> "

    fun log(log: String) {
        XposedBridge.log("$PREFIX$log")
    }

    fun log(th: Throwable) {
        XposedBridge.log("$PREFIX error >>>>>>>>>>>>>")
        XposedBridge.log(th)
        XposedBridge.log("$PREFIX error <<<<<<<<<<<<<")
    }
}