package cn.tinyhai.ban_uninstall.auth.server

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.auth.entites.AuthData
import cn.tinyhai.ban_uninstall.auth.entites.OpType
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.utils.SystemContextHolder
import cn.tinyhai.ban_uninstall.utils.XPLogUtils
import java.io.File

object AuthService : IAuth.Stub() {

    private val helper = AuthHelper()

    private val pendingOp = PendingOpList()

    override fun hasPwd(): Boolean {
        return helper.hasPwd
    }

    override fun setPwd(newSha256: String) {
        helper.setPwd(newSha256)
    }

    override fun clearPwd() {
        helper.clearAuth()
    }

    override fun authenticate(sha256: String): Boolean {
        return helper.authenticate(sha256)
    }

    fun onSelfRemoved() {
        helper.clearAuth()
    }

    fun showClearDataConfirm(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        pkgInfo: PkgInfo,
        callingUid: Int,
        callingPackageName: String
    ) {
        val opId = pendingOp.add(wrapWithPendingOp(onConfirm, onCancel))
        startAuthActivity(opId, pkgInfo, OpType.ClearData, callingUid, callingPackageName)
    }

    fun showUninstallConfirm(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        pkgInfo: PkgInfo,
        callingUid: Int,
        callingPackageName: String
    ) {
        val opId = pendingOp.add(wrapWithPendingOp(onConfirm, onCancel))
        startAuthActivity(opId, pkgInfo, OpType.Uninstall, callingUid, callingPackageName)
    }

    private fun startAuthActivity(
        opId: Int,
        pkgInfo: PkgInfo,
        opType: OpType,
        callingUid: Int,
        callingPackageName: String
    ) {
        val ident = Binder.clearCallingIdentity()
        try {
            SystemContextHolder.withSystemContext {
                val appInfo =
                    packageManager.getApplicationInfoAsUser(pkgInfo.packageName, 0, pkgInfo.userId)
                if (appInfo == null) {
                    XPLogUtils.log("!!!!! getApplicationInfo failed  $pkgInfo")
                    return@withSystemContext
                }
                val callingAppInfo =
                    packageManager.getApplicationInfoAsUser(
                        callingPackageName,
                        0,
                        callingUid / 100_000
                    )
                val authData = AuthData(
                    opId = opId,
                    opType = opType,
                    opUid = callingUid,
                    opAppInfo = callingAppInfo,
                    appInfo = appInfo
                )
                val intent = AuthClient.buildAuthIntent(AuthService, authData)
                startActivity(intent)
            }
        } finally {
            Binder.restoreCallingIdentity(ident)
        }
    }

    private fun PackageManager.getApplicationInfoAsUser(
        packageName: String,
        flags: Int,
        userId: Int
    ): ApplicationInfo? {
        val method = this::class.java.getDeclaredMethod(
            "getApplicationInfoAsUser",
            packageName::class.java,
            flags::class.javaPrimitiveType,
            userId::class.javaPrimitiveType
        )
        return runCatching {
            XPLogUtils.log("getApplicationInfoAsUser($packageName:$userId)")
            method.invoke(
                this,
                packageName,
                flags,
                userId
            )
        }.onFailure {
            XPLogUtils.log(it)
        }.getOrNull() as? ApplicationInfo
    }

    private fun wrapWithPendingOp(
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ): PendingOpList.PendingOp {
        return object : PendingOpList.PendingOp {
            override fun confirm() {
                onConfirm()
            }

            override fun cancel() {
                onCancel()
            }
        }
    }

    fun getCallingPackageName(callingPid: Int): String {
        XPLogUtils.log("getCallingPackageName($callingPid)")
        return runCatching {
            val processName =
                File("/proc/$callingPid/cmdline").readText().substringBefore(Char.MIN_VALUE)
            val colonIdx = processName.indexOf(':')
            if (colonIdx >= 0) {
                processName.substring(0, colonIdx)
            } else {
                processName
            }.also {
                XPLogUtils.log(it)
            }
        }.getOrDefault("")
    }

    override fun agree(opId: Int) {
        val calling = Binder.clearCallingIdentity()
        try {
            pendingOp.remove(opId)?.confirm()
        } finally {
            Binder.restoreCallingIdentity(calling)
        }
    }

    override fun prevent(opId: Int) {
        val calling = Binder.clearCallingIdentity()
        try {
            pendingOp.remove(opId)?.cancel()
        } finally {
            Binder.restoreCallingIdentity(calling)
        }
    }
}