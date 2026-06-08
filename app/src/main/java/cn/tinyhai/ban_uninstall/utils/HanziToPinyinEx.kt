package cn.tinyhai.ban_uninstall.utils

fun String.toLowercasePinyin() = HanziToPinyin.getInstance().toPinyin(this).lowercase()
