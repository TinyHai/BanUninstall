package cn.tinyhai.ban_uninstall.transact.entities

data class PkgInfo(
    val packageName: String,
    val userId: Int
) {
    companion object {
        operator fun invoke(packageNameWithUserId: String): PkgInfo {
            val splitCharIndex = packageNameWithUserId.indexOf(":")
            val (packageName, userId) = if (splitCharIndex > 0) {
                packageNameWithUserId.substring(
                    0,
                    endIndex = splitCharIndex
                ) to packageNameWithUserId.substring(splitCharIndex + 1).toInt()
            } else {
                throw IllegalArgumentException("packageNameWithUserId: $packageNameWithUserId userId not found")
            }
            return PkgInfo(packageName, userId)
        }
    }

    override fun toString(): String {
        return "$packageName:$userId"
    }
}