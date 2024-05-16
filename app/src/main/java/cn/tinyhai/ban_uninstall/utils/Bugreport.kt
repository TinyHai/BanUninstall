package cn.tinyhai.ban_uninstall.utils

import android.annotation.SuppressLint
import cn.tinyhai.ban_uninstall.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.GregorianCalendar

@SuppressLint("SimpleDateFormat")
suspend fun getLogcatFile() = withContext(Dispatchers.IO) {
    val cacheDir = App.app.cacheDir
    val bugreportDir = File(cacheDir, "bugreport").also { it.mkdirs() }
    val time = GregorianCalendar.getInstance().time
    val formatter = SimpleDateFormat("yyyy-MM-dd_HH_mm")
    val logcatFile = File(bugreportDir, "logcat.txt")
    fastResultWithRootShell("logcat", "-d", ">", logcatFile.absolutePath)
    val bugreportFile = File(cacheDir, "BanUninstall_bugreport_${formatter.format(time)}.tar.gz")
    fastResultWithRootShell(
        "tar",
        "-czf",
        bugreportFile.absolutePath,
        "-C",
        bugreportDir.absolutePath,
        "."
    )
    fastResultWithRootShell(
        "rm",
        "-rf",
        bugreportDir.absolutePath
    )
    fastResultWithRootShell(
        "chmod",
        "0644",
        bugreportFile.absolutePath
    )
    bugreportFile
}