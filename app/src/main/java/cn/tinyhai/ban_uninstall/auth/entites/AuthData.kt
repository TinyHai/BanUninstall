package cn.tinyhai.ban_uninstall.auth.entites

import android.content.pm.ApplicationInfo
import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class AuthData(
    val opId: Int,
    val opTypeOrdinal: Int,
    val opUid: Int,
    val opAppInfo: ApplicationInfo?,
    val appInfo: ApplicationInfo
) : Parcelable {

    val opType get() = OpType.entries[opTypeOrdinal]

    val isEmpty get() = this === Empty

    companion object {
        val Empty = AuthData(
            -1,
            -1,
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