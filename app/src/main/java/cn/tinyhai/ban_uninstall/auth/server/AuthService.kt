package cn.tinyhai.ban_uninstall.auth.server

import android.content.pm.ApplicationInfo
import android.os.Binder
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.auth.entities.AuthData
import cn.tinyhai.ban_uninstall.auth.entities.OpRecord
import cn.tinyhai.ban_uninstall.auth.entities.OpResult
import cn.tinyhai.ban_uninstall.auth.entities.OpType
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.transact.server.TransactService
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
        val appInfo =
            TransactService.getApplicationInfoAsUser(pkgInfo.packageName, pkgInfo.userId)
        val opId = SystemContextHolder.withSystemContext {
            pendingOp.add(
                wrapWithPendingOp(
                    OpRecord(
                        label = appInfo?.loadLabel(packageManager)?.toString(),
                        opType = OpType.ClearData,
                        pkgInfo,
                        callingUid,
                        callingPackageName
                    ),
                    onConfirm,
                    onCancel
                )
            )
        }

        if (appInfo == null) {
            XPLogUtils.log("maybe there is a sharedlibrary is being uninstalled, just skip it")
            agree(opId)
            return
        }

        runCatching {
            val callingAppInfo =
                TransactService.getApplicationInfoAsUser(callingPackageName, callingUid / 100_000)
            startAuthActivity(opId, OpType.ClearData, appInfo, callingUid, callingAppInfo)
        }.onSuccess {
            if (!it) {
                prevent(opId)
            }
        }.onFailure {
            XPLogUtils.log(it)
            XPLogUtils.log("!!!! what's wrong?")
            agree(opId)
        }
    }

    fun showUninstallConfirm(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        pkgInfo: PkgInfo,
        callingUid: Int,
        callingPackageName: String
    ) {
        val appInfo =
            TransactService.getApplicationInfoAsUser(pkgInfo.packageName, pkgInfo.userId)
        val opId = SystemContextHolder.withSystemContext {
            pendingOp.add(
                wrapWithPendingOp(
                    OpRecord(
                        label = appInfo?.loadLabel(packageManager)?.toString(),
                        opType = OpType.Uninstall,
                        pkgInfo,
                        callingUid,
                        callingPackageName
                    ),
                    onConfirm,
                    onCancel
                )
            )
        }

        if (appInfo == null) {
            XPLogUtils.log("!!!! $pkgInfo not found")
            agree(opId)
            return
        }

        runCatching {
            val callingAppInfo =
                TransactService.getApplicationInfoAsUser(callingPackageName, callingUid / 100_000)
            startAuthActivity(opId, OpType.Uninstall, appInfo, callingUid, callingAppInfo)
        }.onSuccess {
            if (!it) {
                prevent(opId)
            }
        }.onFailure {
            XPLogUtils.log(it)
            XPLogUtils.log("!!!! what's wrong?")
            agree(opId)
        }
    }

    fun onUninstall(
        pkgInfo: PkgInfo,
        callingUid: Int,
        callingPackageName: String,
        result: OpResult
    ) {
        SystemContextHolder.withSystemContext {
            val appInfo =
                TransactService.getApplicationInfoAsUser(pkgInfo.packageName, pkgInfo.userId)
            opRecordList.add(
                opRecord = OpRecord(
                    label = appInfo?.loadLabel(packageManager)?.toString(),
                    opType = OpType.Uninstall,
                    pkgInfo,
                    callingUid,
                    callingPackageName
                ),
                result = result
            )
        }
    }

    fun onClearData(
        pkgInfo: PkgInfo,
        callingUid: Int,
        callingPackageName: String,
        result: OpResult
    ) {
        SystemContextHolder.withSystemContext {
            val appInfo =
                TransactService.getApplicationInfoAsUser(pkgInfo.packageName, pkgInfo.userId)
            opRecordList.add(
                opRecord = OpRecord(
                    label = appInfo?.loadLabel(packageManager)?.toString(),
                    opType = OpType.ClearData,
                    pkgInfo,
                    callingUid,
                    callingPackageName
                ),
                result = result
            )
        }
    }

    private fun startAuthActivity(
        opId: Int,
        opType: OpType,
        appInfo: ApplicationInfo,
        callingUid: Int,
        callingAppInfo: ApplicationInfo?,
    ): Boolean {
        var success = false
        val ident = Binder.clearCallingIdentity()
        try {
            SystemContextHolder.withSystemContext {
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

    private fun wrapWithPendingOp(
        opRecord: OpRecord,
        onAgree: () -> Unit,
        onPrevent: () -> Unit
    ): PendingOpList.PendingOp {
        return object : PendingOpList.PendingOp {
            override fun agree() {
                onAgree()
                opRecordList.add(opRecord, OpResult.Allowed)
            }

            override fun prevent() {
                onPrevent()
                opRecordList.add(opRecord, OpResult.Prevented)
            }
        }
    }

    override fun agree(opId: Int) {
        val ident = Binder.clearCallingIdentity()
        try {
            pendingOp.remove(opId)?.agree()
        } finally {
            Binder.restoreCallingIdentity(ident)
        }
    }

    override fun prevent(opId: Int) {
        val ident = Binder.clearCallingIdentity()
        try {
            pendingOp.remove(opId)?.prevent()
        } finally {
            Binder.restoreCallingIdentity(ident)
        }
    }

    fun preventAll() {
        val ident = Binder.clearCallingIdentity()
        try {
            pendingOp.removeAll().forEach {
                it.prevent()
            }
        } finally {
            Binder.restoreCallingIdentity(ident)
        }
    }

    override fun isValid(opId: Int): Boolean {
        return pendingOp.contains(opId)
    }

    override fun getAllOpRecord(): List<OpRecord> {
        return opRecordList.toList()
    }

    override fun clearAllOpRecord() {
        opRecordList.clear()
    }
}