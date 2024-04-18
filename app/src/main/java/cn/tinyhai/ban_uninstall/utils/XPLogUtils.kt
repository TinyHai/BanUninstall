package cn.tinyhai.ban_uninstall.utils

import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.xp.hook.logger.XPLogger
import de.robv.android.xposed.XposedBridge

object XPLogUtils : XPLogger {
    private const val PREFIX = "[Ban Uninstall] >>> "

    internal var devMode = BuildConfig.DEBUG || XSharedPrefs.isDevMode

    fun log(log: String) {
        XposedBridge.log("$PREFIX$log")
    }

    fun log(th: Throwable) {
        XposedBridge.log("$PREFIX error >>>>>>>>>>>>>")
        XposedBridge.log(th)
        XposedBridge.log("$PREFIX error <<<<<<<<<<<<<")
    }

    override fun info(s: String) {
        log(s)
    }

    override fun debug(s: String) {
        if (devMode) {
            log(s)
        }
    }

    override fun verbose(s: String) {
        debug(s)
    }

    override fun error(s: String) {
        log(s)
    }

    override fun error(th: Throwable) {
        log(th)
    }

    override fun error(s: String, th: Throwable) {
        log(s)
        log(th)
    }
}