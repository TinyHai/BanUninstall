package cn.tinyhai.ban_uninstall.auth.server

import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.configs.Configs
import cn.tinyhai.ban_uninstall.utils.writeWithBak
import java.io.File

class AuthHelper {

    private val authFile: File
        get() = File(Configs.authFilePath).apply {
            parentFile?.mkdirs()
        }

    private var sha256 = if (authFile.exists()) authFile.readText().trim() else ""
        set(value) {
            if (field != value) {
                field = value
                store(value)
            }
        }

    val hasPwd get() = sha256.isNotBlank()

    fun authenticate(sha256: String): Boolean {
        if (!hasPwd) {
            return true
        }
        if (sha256.isBlank()) {
            return false
        }
        return sha256 == this.sha256
    }

    fun setPwd(newSha256: String) {
        this.sha256 = newSha256
    }

    fun clearAuth() {
        sha256 = ""
        deleteAuthFile()
    }

    private fun deleteAuthFile() {
        authFile.delete()
    }

    private fun store(sha256: String) {
        authFile.writeWithBak {
            append(sha256)
            flush()
        }
    }
}