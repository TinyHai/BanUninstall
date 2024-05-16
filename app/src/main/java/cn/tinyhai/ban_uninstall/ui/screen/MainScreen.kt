package cn.tinyhai.ban_uninstall.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.R
import cn.tinyhai.ban_uninstall.transact.entities.ActiveMode
import cn.tinyhai.ban_uninstall.ui.component.rememberConfirmDialog
import cn.tinyhai.ban_uninstall.ui.component.rememberSetPwdDialog
import cn.tinyhai.ban_uninstall.ui.component.rememberVerifyPwdDialog
import cn.tinyhai.ban_uninstall.utils.getLogcatFile
import cn.tinyhai.ban_uninstall.vm.MainState
import cn.tinyhai.ban_uninstall.vm.MainViewModel
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BannedAppScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(start = true)
@Composable
fun MainScreen(navigator: DestinationsNavigator) {
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_title_main)) },
                actions = {
                    if (state.isActive) {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
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
                                    contentDescription = stringResource(R.string.icon_description_say_hello)
                                )
                            }
                        }
                        if (BuildConfig.ROOT_FEATURE) {
                            TooltipBox(
                                positionProvider = rememberTooltipPositionProvider(),
                                tooltip = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Text(text = stringResource(R.string.icon_description_bug_report))
                                    }
                                },
                                state = rememberTooltipState()
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val logcatFile = getLogcatFile()
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                                logcatFile
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND)
                                            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                                            shareIntent.setDataAndType(uri, "text/*")
                                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    context.getString(R.string.icon_description_bug_report)
                                                )
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.BugReport,
                                        contentDescription = stringResource(R.string.icon_description_bug_report)
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) {
        MainScreenContent(
            state = state,
            hasRoot = viewModel::hasRoot,
            onActiveWithRoot = viewModel::onActiveWithRoot,
            onBanUninstall = viewModel::onBanUninstall,
            onBanClearData = viewModel::onBanClearData,
            onAutoStart = viewModel::onAutoStart,
            onDevMode = viewModel::onDevMode,
            onUseBannedList = viewModel::onUseBannedList,
            gotoBannedApp = { navigator.navigate(BannedAppScreenDestination()) },
            onShowConfirm = viewModel::onShowConfirm,
            onVerifyPwd = viewModel::onVerifyPwd,
            onSetPwd = viewModel::onSetPwd,
            onClearPwd = viewModel::onClearPwd,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(it),
        )
    }
}

@Composable
private fun MainScreenContent(
    state: MainState,
    hasRoot: () -> Boolean,
    onActiveWithRoot: () -> Unit,
    onBanUninstall: (Boolean) -> Unit,
    onBanClearData: (Boolean) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onDevMode: (Boolean) -> Unit,
    onUseBannedList: (Boolean) -> Unit,
    gotoBannedApp: () -> Unit,
    onShowConfirm: (Boolean) -> Unit,
    onVerifyPwd: (String) -> Boolean,
    onSetPwd: (String) -> Unit,
    onClearPwd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        StatusGroup(state, hasRoot, onActiveWithRoot)
        FunctionGroup(state, onVerifyPwd, onBanUninstall, onBanClearData, onAutoStart, onDevMode)
        AdvanceGroup(state, onVerifyPwd, onUseBannedList, gotoBannedApp)
        SecurityGroup(state, onShowConfirm, onVerifyPwd, onSetPwd, onClearPwd)
    }
}

@Composable
private fun FunctionGroup(
    state: MainState,
    onVerifyPwd: (String) -> Boolean,
    onBanUninstall: (Boolean) -> Unit,
    onBanClearData: (Boolean) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onDevMode: (Boolean) -> Unit,
) {
    val verifyPwdDialogHandle = rememberVerifyPwdDialog(
        title = stringResource(id = R.string.title_verify_password),
        errorText = stringResource(id = R.string.text_verify_password_error),
        onVerify = onVerifyPwd
    )
    val scope = rememberCoroutineScope()
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
            scope.launch {
                if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                    return@launch
                }
                onBanUninstall(it)
            }
        }
        SettingsSwitch(
            state = state.banClearData,
            enabled = state.isActive,
            title = { Text(text = stringResource(R.string.switch_title_ban_clear_data)) },
        ) {
            scope.launch {
                if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                    return@launch
                }
                onBanClearData(it)
            }
        }
        if (state.activeMode == ActiveMode.Root) {
            SettingsSwitch(
                state = state.autoStart,
                title = { Text(text = stringResource(R.string.switch_title_auto_start)) },
                subtitle = { Text(text = stringResource(R.string.switch_subtitle_auto_start)) }
            ) {
                onAutoStart(it)
            }
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

@Composable
private fun StatusGroup(state: MainState, hasRoot: () -> Boolean, onActiveWithRoot: () -> Unit) {
    val activeWithRootDialog = rememberConfirmDialog(
        title = stringResource(R.string.title_active_with_root),
        content = stringResource(R.string.text_content_activate_with_root)
    )
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = {
            Text(text = stringResource(R.string.group_title_module_status))
        }
    ) {
        val moduleStatus =
            stringResource(if (state.isActive) R.string.module_status_active else R.string.module_status_disable)
        val context = LocalContext.current
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
                    Text(
                        text = stringResource(
                            id = R.string.module_active_mode,
                            state.activeMode.description
                        ).format()
                    )
                }
            }
        ) {
            if (state.isActive) {
                Toast.makeText(context, moduleStatus, Toast.LENGTH_SHORT).show()
                return@SettingsMenuLink
            }
            scope.launch {
                if (hasRoot() && activeWithRootDialog.showConfirm()) {
                    onActiveWithRoot()
                }
            }
        }
    }
}

@Composable
private fun AdvanceGroup(
    state: MainState,
    onVerifyPwd: (String) -> Boolean,
    onUseBannedList: (Boolean) -> Unit,
    gotoBannedApp: () -> Unit
) {
    val verifyPwdDialogHandle = rememberVerifyPwdDialog(
        title = stringResource(id = R.string.title_verify_password),
        errorText = stringResource(id = R.string.text_verify_password_error),
        onVerify = onVerifyPwd
    )
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = {
            Text(text = stringResource(R.string.group_title_advanced))
        }
    ) {
        SettingsSwitch(
            state = state.useBannedList,
            enabled = state.isActive,
            title = { Text(text = stringResource(R.string.switch_title_use_banned_list)) },
        ) {
            scope.launch {
                if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                    return@launch
                }
                onUseBannedList(it)
            }
        }
        AnimatedVisibility(
            state.isActive && state.useBannedList,
        ) {
            SettingsMenuLink(title = { Text(text = stringResource(R.string.menulink_title_banned_app)) }) {
                scope.launch {
                    if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                        return@launch
                    }
                    gotoBannedApp()
                }
            }
        }
    }
}

@Composable
private fun SecurityGroup(
    state: MainState,
    onShowConfirm: (Boolean) -> Unit,
    onVerifyPwd: (String) -> Boolean,
    onSetPwd: (String) -> Unit,
    onClearPwd: () -> Unit
) {
    val verifyPwdDialogHandle = rememberVerifyPwdDialog(
        title = stringResource(id = R.string.title_verify_password),
        errorText = stringResource(id = R.string.text_verify_password_error),
        onVerify = onVerifyPwd
    )
    val setPwdDialogHandle = rememberSetPwdDialog(
        title = stringResource(id = R.string.title_set_password),
    )
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = {
            Text(text = stringResource(R.string.group_title_security))
        }
    ) {
        SettingsSwitch(
            state = state.hasPwd,
            enabled = state.isActive,
            title = { Text(text = stringResource(R.string.switch_title_use_separate_password)) },
            subtitle = {
                Text(text = stringResource(R.string.switch_subtitle_use_separate_password))
            }
        ) { enable ->
            scope.launch {
                when {
                    enable -> {
                        onSetPwd(setPwdDialogHandle.awaitInput())
                    }

                    !enable && state.hasPwd -> {
                        if (verifyPwdDialogHandle.verify()) {
                            onClearPwd()
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            state.isActive && state.hasPwd,
        ) {
            SettingsMenuLink(title = { Text(text = stringResource(R.string.menulink_change_password)) }) {
                scope.launch {
                    if (verifyPwdDialogHandle.verify()) {
                        onSetPwd(setPwdDialogHandle.awaitInput())
                    }
                }
            }
        }
        SettingsSwitch(
            state = state.showConfirm,
            enabled = state.isActive,
            title = { Text(text = stringResource(R.string.switch_title_show_confirm)) },
            subtitle = {
                Text(text = stringResource(R.string.switch_subtitle_show_confirm))
            }
        ) {
            onShowConfirm(it)
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