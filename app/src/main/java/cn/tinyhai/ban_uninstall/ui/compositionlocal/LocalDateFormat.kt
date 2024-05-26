package cn.tinyhai.ban_uninstall.ui.compositionlocal

import androidx.compose.runtime.compositionLocalOf
import java.text.DateFormat

val LocalDateFormats = compositionLocalOf<DateFormats> {
    error("DateFormat is not present")
}

class DateFormats(
    val dateFormat: DateFormat,
    val timeFormat: DateFormat
)