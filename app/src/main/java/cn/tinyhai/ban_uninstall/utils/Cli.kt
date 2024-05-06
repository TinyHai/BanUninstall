package cn.tinyhai.ban_uninstall.utils

import android.os.Build
import android.os.Environment
import android.util.Log
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.BuildConfig
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.Shell.FLAG_NON_ROOT_SHELL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "Cli"

private const val TMP_PATH = "/data/local/tmp"

private const val LSPATCH_PATH = "$TMP_PATH/lspatch"

private val META_LOADER_LIB_PATH = "$LSPATCH_PATH/so/${Build.SUPPORTED_ABIS[0]}/libmeta_loader.so"

private val nativeLibraryDir = App.app.applicationInfo.nativeLibraryDir

private val injectToolPath = nativeLibraryDir + File.separator + "libinject_tool.so"

private val normalShell by lazy { Shell.Builder.create().setFlags(FLAG_NON_ROOT_SHELL).build() }

private val rootShell by lazy { Shell.getShell() }

val hasRoot get() = rootShell.isAlive && rootShell.isRoot

suspend fun fastResultWithRootShell(vararg cmd: String) = withContext(Dispatchers.IO) {
    val out = ArrayList<String>()
    val err = ArrayList<String>()
    val joinedCmd = cmd.joinToString(" ")
    rootShell.newJob().add(joinedCmd).to(out, err).exec().isSuccess.also {
        Log.i(TAG, out.toString())
        Log.i(TAG, err.toString())
        if (!it) {
            Log.d(TAG, "exec $joinedCmd failed")
        }
    }
}

suspend fun fastResultWithShell(vararg cmd: String) = withContext(Dispatchers.IO) {
    val out = ArrayList<String>()
    val err = ArrayList<String>()
    val joinedCmd = cmd.joinToString(" ")
    normalShell.newJob().add(joinedCmd).to(out, err).exec().isSuccess.also {
        Log.i(TAG, out.toString())
        Log.i(TAG, err.toString())
        if (!it) {
            Log.d(TAG, "exec $joinedCmd failed")
        }
    }
}

suspend fun copyPatchToTmp() {
    withContext(Dispatchers.IO) {
        val cacheDir = App.app.cacheDir.absolutePath
        App.app.assets.copyTo("lspatch", App.app.cacheDir.absolutePath)
        val from = cacheDir + File.separator + "lspatch"
        val to = "/data/local/tmp"
        Shell.enableVerboseLogging = true
        fastResultWithRootShell("cp", "-R", from, to)
        fastResultWithRootShell("chown", "-R", "system:system", LSPATCH_PATH)
        fastResultWithRootShell("chcon", "-R", "u:object_r:system_file:s0", LSPATCH_PATH)
    }
}

suspend fun makePrefsWorldReadable(filename: String) {
    withContext(Dispatchers.IO) {
        val prefName = filename.let { if (it.endsWith(".xml")) it else "$it.xml" }
        val appDataDir =
            Environment.getDataDirectory().absolutePath + File.separator + "data" + File.separator + BuildConfig.APPLICATION_ID
        fastResultWithShell("chmod", "o+x", appDataDir)
        val prefDir = appDataDir + File.separator + "shared_prefs"
        fastResultWithShell("chmod", "o+x", prefDir)
        val prefFile = prefDir + File.separator + prefName
        fastResultWithShell("chmod", "o+r", prefFile)
    }
}

suspend fun injectSystemServer() {
    withContext(Dispatchers.IO) {
        fastResultWithRootShell(
            injectToolPath,
            "inject",
            "-c",
            "system_server",
            "-s",
            META_LOADER_LIB_PATH
        ).let {
            if (!it) {
                Log.d(TAG, "inject failed")
            }
        }
    }
}