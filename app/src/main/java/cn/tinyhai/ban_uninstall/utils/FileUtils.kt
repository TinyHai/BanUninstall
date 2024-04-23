package cn.tinyhai.ban_uninstall.utils

import java.io.BufferedWriter
import java.io.File

inline fun File.writeWithBak(block: BufferedWriter.() -> Unit): Result<Unit> {
    val bak = File("${this.path}.bak")
    return runCatching {
        if (this.exists()) {
            this.renameTo(bak)
        }
        bufferedWriter().use(block)
    }.onFailure {
        if (this.exists()) {
            this.delete()
        }
        if (bak.exists()) {
            bak.renameTo(this)
        }
    }.onSuccess {
        if (bak.exists()) {
            bak.delete()
        }
    }
}