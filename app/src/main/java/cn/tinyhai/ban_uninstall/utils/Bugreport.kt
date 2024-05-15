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
    val time = GregorianCalendar.getInstance().time
    val formatter = SimpleDateFormat("yyyy-MM-dd_HH_mm")
    val logcatFile = File(App.app.cacheDir, "Logcat_${formatter.format(time)}.txt")
    fastResultWithRootShell("logcat", "-d", ">", logcatFile.absolutePath)
    if (!logcatFile.exists()) {
        logcatFile.createNewFile()
    }
    logcatFile
}