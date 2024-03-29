package cn.tinyhai.ban_uninstall.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider
import cn.tinyhai.ban_uninstall.R
import cn.tinyhai.ban_uninstall.vm.MainState
import cn.tinyhai.ban_uninstall.vm.MainViewModel
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    TooltipBox(
                        positionProvider = rememberTooltipPositionProvider(),
                        tooltip = {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(text = stringResource(R.string.icon_description_say_hello))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { viewModel.sayHello() }) {
                            Icon(
                                Icons.Outlined.Sms,
                                contentDescription = stringResource(R.string.icon_description_sync)
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = rememberTooltipPositionProvider(),
                        tooltip = {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.icon_description_sync),
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = { viewModel.notifyReloadIfNeeded() }
                        ) {
                            Icon(
                                Icons.Outlined.Sync,
                                contentDescription = stringResource(R.string.icon_description_sync)
                            )
                        }
                    }
                }
            )
        }
    ) {
        val state by viewModel.state.collectAsState()
        MainScreenContent(
            onBanUninstall = viewModel::onBanUninstall,
            onBanClearData = viewModel::onBanClearData,
            onDevMode = viewModel::onDevMode,
            modifier = Modifier
                .padding(it),
            state = state,
        )
    }
}

@Composable
private fun MainScreenContent(
    onBanUninstall: (Boolean) -> Unit,
    onBanClearData: (Boolean) -> Unit,
    onDevMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    state: MainState
) {
    Column(modifier) {
        SettingsGroup(
            title = {
                Text(text = stringResource(R.string.group_title_module_status))
            }
        ) {
            val context = LocalContext.current
            val moduleStatus =
                stringResource(if (state.isActive) R.string.module_status_active else R.string.module_status_disable)
            SettingsMenuLink(
                icon = {
                    Icon(
                        if (state.isActive) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                        contentDescription = moduleStatus
                    )
                },
                title = {
                    Text(text = moduleStatus)
                },
                subtitle = {
                    if (state.isActive) {
                        Text(text = stringResource(id = R.string.module_active_mode).format(state.xpTag))
                    }
                }
            ) {
                Toast.makeText(context, moduleStatus, Toast.LENGTH_SHORT).show()
            }
        }
        SettingsGroup(
            title = {
                Text(text = stringResource(R.string.group_title_function))
            }
        ) {
            SettingsSwitch(
                state = state.banUninstall,
                enabled = state.isActive,
                title = { Text(text = stringResource(R.string.switch_title_ban_uninstall)) },
            ) {
                onBanUninstall(it)
            }
            SettingsSwitch(
                state = state.banClearData,
                enabled = state.isActive,
                title = { Text(text = stringResource(R.string.switch_title_ban_clear_data)) },
            ) {
                onBanClearData(it)
            }
            SettingsSwitch(
                state = state.devMode,
                enabled = state.isActive,
                title = { Text(text = stringResource(R.string.switch_title_dev_mode)) },
            ) {
                onDevMode(it)
            }
        }
    }
}

@Composable
private fun rememberTooltipPositionProvider(): PopupPositionProvider {
    val spacing = with(LocalDensity.current) { 4.dp.toPx().roundToInt() }
    return remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = anchorBounds.left + ((anchorBounds.width - popupContentSize.width) / 2)
                val y = anchorBounds.bottom + spacing
                return IntOffset(x, y)
            }
        }
    }
}