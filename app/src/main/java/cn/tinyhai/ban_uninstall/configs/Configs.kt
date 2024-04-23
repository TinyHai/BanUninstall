package cn.tinyhai.ban_uninstall.configs

import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import java.io.File

object Configs {
    private const val CONFIG_PATH = "/data/misc/adb/${BuildConfig.APPLICATION_ID}"
    private const val AUTH_FILE_PATH = "$CONFIG_PATH/auth"
    private const val BANNED_PKG_LIST_FILE_PATH = "${CONFIG_PATH}/banned_pkg_list"

    val authFilePath = AUTH_FILE_PATH
    val bannedPkgListFilePath = BANNED_PKG_LIST_FILE_PATH

    fun onSelfRemoved() {
        destroy()
    }

    private fun destroy() {
        XPLogUtils.log("delete $CONFIG_PATH")
        File(CONFIG_PATH).let {
            if (it.exists()) {
                it.deleteRecursively()
            }
        }
    }
}