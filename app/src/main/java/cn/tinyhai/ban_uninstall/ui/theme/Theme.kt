package cn.tinyhai.ban_uninstall.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val themeController = remember { ThemeController() }

    MiuixTheme(
        controller = themeController,
        content = content
    )
}