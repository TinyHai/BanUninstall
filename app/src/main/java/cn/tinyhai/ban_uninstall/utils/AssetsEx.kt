package cn.tinyhai.ban_uninstall.utils

import android.content.res.AssetManager
import java.io.File

fun AssetManager.copyTo(path: String, dest: String) {
    if (list(path).isNullOrEmpty()) {
        copyFileTo(path, dest)
    } else {
        copyDirTo(path, dest)
    }
}

private fun AssetManager.copyDirTo(path: String, dest: String) {
    File(dest, path).mkdir()
    list(path)?.forEach {
        copyTo(path + File.separator + it, dest)
    }
}

private fun AssetManager.copyFileTo(filename: String, dest: String) {
    val input = open(filename)
    input.use { from ->
        File(dest, filename).outputStream().use { to ->
            from.copyTo(to)
        }
    }
}