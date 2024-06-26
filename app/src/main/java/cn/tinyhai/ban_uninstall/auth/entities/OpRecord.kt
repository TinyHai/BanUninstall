package cn.tinyhai.ban_uninstall.auth.entities

import android.os.Parcelable
import androidx.annotation.Keep
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class OpRecord(
    val label: String?,
    val opTypeOrdinal: Int,
    val pkgInfoString: String,
    val callingUid: Int,
    val callingPackageName: String,
    val resultOrdinal: Int,
    val timeMillis: Long,
) : Parcelable {

    val opType get() = OpType.entries[opTypeOrdinal]

    val result get() = OpResult.entries[resultOrdinal]

    val pkgInfo get() = PkgInfo(pkgInfoString)

    companion object {
        operator fun invoke(
            label: String?,
            opType: OpType,
            pkgInfo: PkgInfo,
            callingUid: Int,
            callingPackageName: String,
            result: OpResult = OpResult.Unhandled
        ): OpRecord {
            return OpRecord(
                label,
                opType.ordinal,
                pkgInfo.toString(),
                callingUid,
                callingPackageName,
                result.ordinal,
                System.currentTimeMillis()
            )
        }
    }
}