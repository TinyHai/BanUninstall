package cn.tinyhai.ban_uninstall.auth.entities

import android.os.Parcelable
import androidx.annotation.Keep
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class OpRecord(
    val opTypeOrdinal: Int,
    val pkgInfoString: String,
    val callingUid: Int,
    val callingPackageName: String,
    val resultOrdinal: Int,
    val timeMillis: Long,
) : Parcelable {

    @IgnoredOnParcel
    val opType = OpType.entries[opTypeOrdinal]

    @IgnoredOnParcel
    val result = OpResult.entries[resultOrdinal]

    companion object {
        operator fun invoke(
            opType: OpType,
            pkgInfo: PkgInfo,
            callingUid: Int,
            callingPackageName: String,
            result: OpResult = OpResult.Unhandled
        ): OpRecord {
            return OpRecord(
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