package cn.tinyhai.ban_uninstall.ui.compositionlocal

import androidx.compose.runtime.compositionLocalOf
import java.text.DateFormat

val LocalDateFormat = compositionLocalOf<DateFormat> {
    error("DateFormat is not present")
}