package cn.tinyhai.ban_uninstall.vm

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.auth.entities.OpResult
import cn.tinyhai.ban_uninstall.auth.entities.OpType
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

data class OpRecordInfo(
    val label: String?,
    val isDual: Boolean,
    val packageName: String,
    val icon: Drawable?,
    val opType: OpType,
    val opResult: OpResult,
    val opUid: Int,
    val opLabel: String?,
    val opDate: Date,
)


data class OpRecordState(
    val isRefreshing: Boolean = false,
    val hasPwd: Boolean = false,
    val showAllowed: Boolean = true,
    val showPrevented: Boolean = true,
    val showUninstall: Boolean = true,
    val showClearData: Boolean = true,
    val records: List<OpRecordInfo> = emptyList()
) {
    companion object {
        val Empty = OpRecordState()
    }
}

class OpRecordViewModel : ViewModel() {
    private val transactClient: TransactClient = TransactClient()

    private val authClient =
        transactClient.auth?.let { AuthClient(IAuth.Stub.asInterface(it)) } ?: AuthClient()

    private val _state = MutableStateFlow(OpRecordState.Empty)

    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val hasPwd = authClient.hasPwd()
            updateState(_state) {
                it.copy(hasPwd = hasPwd)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            updateState(_state) { it.copy(isRefreshing = true) }
            val pm = App.app.packageManager
            val records = withContext(Dispatchers.IO) {
                authClient.allOpRecord.asReversed().map {
                    val pkgInfo = it.pkgInfo
                    val appInfo =
                        transactClient.getApplicationInfoAsUser(pkgInfo.packageName, pkgInfo.userId)
                    val opAppInfo = transactClient.getApplicationInfoAsUser(
                        it.callingPackageName,
                        it.callingUid / 100_000
                    )
                    OpRecordInfo(
                        label = it.label,
                        isDual = pkgInfo.userId / 100_000 > 0,
                        packageName = appInfo?.packageName ?: pkgInfo.packageName,
                        icon = appInfo?.loadIcon(pm),
                        opType = it.opType,
                        opResult = it.result,
                        opUid = it.callingUid,
                        opLabel = opAppInfo?.loadLabel(pm)?.toString(),
                        opDate = Date(it.timeMillis)
                    )
                }
            }
            updateState(_state) {
                it.copy(records = records, isRefreshing = false)
            }
        }
    }

    fun showAllowed(show: Boolean) {
        updateState(_state) {
            it.copy(showAllowed = show)
        }
    }

    fun showPrevented(show: Boolean) {
        updateState(_state) {
            it.copy(showPrevented = show)
        }
    }

    fun showUninstall(show: Boolean) {
        updateState(_state) {
            it.copy(showUninstall = show)
        }
    }

    fun showClearData(show: Boolean) {
        updateState(_state) {
            it.copy(showClearData = show)
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                authClient.clearAllOpRecord()
            }
            refresh()
        }
    }

    fun onVerifyPwd(pwd: String): Boolean {
        return authClient.authenticate(pwd)
    }
}