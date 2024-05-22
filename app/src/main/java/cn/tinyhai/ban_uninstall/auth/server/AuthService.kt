package cn.tinyhai.ban_uninstall.auth.server

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.auth.entities.AuthData
import cn.tinyhai.ban_uninstall.auth.entities.OpRecord
import cn.tinyhai.ban_uninstall.auth.entities.OpResult
import cn.tinyhai.ban_uninstall.auth.entities.OpType
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.utils.SystemContextHolder
import cn.tinyhai.ban_uninstall.utils.XPLogUtils

object AuthService : IAuth.Stub() {

    private val helper = AuthHelper()

    private val pendingOp = PendingOpList()

    private val opRecordList = OpRecordList()

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

    fun showClearDataConfirm(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        pkgInfo: PkgInfo,
        callingUid: Int,
        callingPackageName: String
    ) {
        val opId = pendingOp.add(
            wrapWithPendingOp(
                OpRecord(opType = OpType.ClearData, pkgInfo, callingUid, callingPackageName),
                onConfirm,
                onCancel
            )
        )
        kotlin.runCatching {
            startAuthActivity(opId, pkgInfo, OpType.ClearData, callingUid, callingPackageName)
        }.onSuccess {
            if (!it) {
                prevent(opId)
            }
        }.onFailure {
            XPLogUtils.log(it)
            XPLogUtils.log("!!!! what's wrong?")
            prevent(opId)
        }
    }

    fun showUninstallConfirm(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        pkgInfo: PkgInfo,
        callingUid: Int,
        callingPackageName: String
    ) {
        val opId = pendingOp.add(
            wrapWithPendingOp(
                OpRecord(opType = OpType.Uninstall, pkgInfo, callingUid, callingPackageName),
                onConfirm,
                onCancel
            )
        )
        runCatching {
            startAuthActivity(opId, pkgInfo, OpType.Uninstall, callingUid, callingPackageName)
        }.onSuccess {
            if (!it) {
                prevent(opId)
            }
        }.onFailure {
            XPLogUtils.log(it)
            XPLogUtils.log("maybe there is a sharedlibrary is being uninstalled, just skip it")
            agree(opId)
        }
    }

    private fun startAuthActivity(
        opId: Int,
        pkgInfo: PkgInfo,
        opType: OpType,
        callingUid: Int,
        callingPackageName: String
    ): Boolean {
        var success = false
        val ident = Binder.clearCallingIdentity()
        try {
            SystemContextHolder.withSystemContext {
                val appInfo =
                    packageManager.getApplicationInfoAsUser(pkgInfo.packageName, 0, pkgInfo.userId)

                val callingAppInfo = runCatching {
                    packageManager.getApplicationInfoAsUser(
                        callingPackageName,
                        0,
                        callingUid / 100_000
                    )
                }.getOrNull()
                val authData = AuthData(
                    opId = opId,
                    opTypeOrdinal = opType.ordinal,
                    opUid = callingUid,
                    opAppInfo = callingAppInfo,
                    appInfo = appInfo
                )
                val intent = AuthClient.buildAuthIntent(AuthService, authData)
                if (packageManager.resolveActivity(intent, 0) != null) {
                    startActivity(intent)
                    success = true
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident)
        }
        return success
    }

    private fun PackageManager.getApplicationInfoAsUser(
        packageName: String,
        flags: Int,
        userId: Int
    ): ApplicationInfo {
        val method = this::class.java.getDeclaredMethod(
            "getApplicationInfoAsUser",
            packageName::class.java,
            flags::class.javaPrimitiveType,
            userId::class.javaPrimitiveType
        )
        XPLogUtils.log("getApplicationInfoAsUser($packageName:$userId)")
        return method.invoke(
            this,
            packageName,
            flags,
            userId
        ) as ApplicationInfo
    }

    private fun wrapWithPendingOp(
        opRecord: OpRecord,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ): PendingOpList.PendingOp {
        return object : PendingOpList.PendingOp {
            override fun confirm() {
                onConfirm()
                opRecordList.add(opRecord, OpResult.Allowed)
            }

            override fun cancel() {
                onCancel()
                opRecordList.add(opRecord, OpResult.Prevented)
            }
        }
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

    override fun getAllOpRecord(): List<OpRecord> {
        return opRecordList.toList()
    }
}