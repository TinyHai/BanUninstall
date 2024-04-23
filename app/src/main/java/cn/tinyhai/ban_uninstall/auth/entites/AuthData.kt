package cn.tinyhai.ban_uninstall.auth.entites

import android.content.pm.ApplicationInfo
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AuthData(
    val opId: Int,
    val opType: OpType,
    val opUid: Int,
    val opAppInfo: ApplicationInfo?,
    val appInfo: ApplicationInfo
) : Parcelable {

    val isEmpty get() = this === Empty

    companion object {
        val Empty = AuthData(
            -1,
            OpType.ClearData,
            -1,
            null,
            ApplicationInfo()
        )
    }
}

enum class OpType {
    ClearData,
    Uninstall
}