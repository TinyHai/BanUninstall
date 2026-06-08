package cn.tinyhai.ban_uninstall.ui.screen

import android.content.ComponentName
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.R
import cn.tinyhai.ban_uninstall.ui.component.rememberSetPwdDialog
import cn.tinyhai.ban_uninstall.ui.component.rememberVerifyPwdDialog
import cn.tinyhai.ban_uninstall.ui.compositionlocal.LocalSectionEnable
import cn.tinyhai.ban_uninstall.ui.navigation3.Navigator
import cn.tinyhai.ban_uninstall.ui.navigation3.Route
import cn.tinyhai.ban_uninstall.vm.MainState
import cn.tinyhai.ban_uninstall.vm.MainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun MainScreen(navigator: Navigator) {
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.app_title_main),
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        MainScreenContent(
            state = state,
            onBanUninstall = viewModel::onBanUninstall,
            onBanClearData = viewModel::onBanClearData,
            onDevMode = viewModel::onDevMode,
            onUseBannedList = viewModel::onUseBannedList,
            gotoBannedApp = { navigator.push(Route.BannedApp) },
            gotoOpRecord = { navigator.push(Route.OpRecord) },
            onShowConfirm = viewModel::onShowConfirm,
            onVerifyPwd = viewModel::onVerifyPwd,
            onSetPwd = viewModel::onSetPwd,
            onClearPwd = viewModel::onClearPwd,
            onTick = viewModel::onTick,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(it),
        )
    }
}

@Composable
private fun MainScreenContent(
    state: MainState,
    onBanUninstall: (Boolean) -> Unit,
    onBanClearData: (Boolean) -> Unit,
    onDevMode: (Boolean) -> Unit,
    onUseBannedList: (Boolean) -> Unit,
    gotoBannedApp: () -> Unit,
    gotoOpRecord: () -> Unit,
    onShowConfirm: (Boolean) -> Unit,
    onVerifyPwd: (String) -> Boolean,
    onSetPwd: (String) -> Unit,
    onClearPwd: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
    ) {
        StatusGroup(state, onTick)
        FunctionGroup(state, onVerifyPwd, onBanUninstall, onBanClearData, onDevMode)
        AdvanceGroup(state, onVerifyPwd, onUseBannedList, gotoBannedApp, gotoOpRecord)
        SecurityGroup(state, onShowConfirm, onVerifyPwd, onSetPwd, onClearPwd)
    }
}

@Composable
private fun SettingsSection(
    title: String, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(
        LocalSectionEnable provides enabled
    ) {
        Column {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
            )
            Card {
                content()
            }
        }
    }
}

@Composable
private fun FunctionGroup(
    state: MainState,
    onVerifyPwd: (String) -> Boolean,
    onBanUninstall: (Boolean) -> Unit,
    onBanClearData: (Boolean) -> Unit,
    onDevMode: (Boolean) -> Unit,
) {
    val verifyPwdDialogHandle = rememberVerifyPwdDialog(
        title = stringResource(id = R.string.title_verify_password),
        errorText = stringResource(id = R.string.text_verify_password_error),
        onVerify = onVerifyPwd
    )
    val scope = rememberCoroutineScope()
    SettingsSection(
        title = stringResource(R.string.group_title_function),
        enabled = state.isActive,
    ) {
        SwitchPreference(
            enabled = LocalSectionEnable.current,
            checked = state.banUninstall,
            title = stringResource(R.string.switch_title_ban_uninstall),
            onCheckedChange = {
                scope.launch {
                    if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                        return@launch
                    }
                    onBanUninstall(it)
                }
            }
        )
        SwitchPreference(
            enabled = LocalSectionEnable.current,
            checked = state.banClearData,
            title = stringResource(R.string.switch_title_ban_clear_data),
            onCheckedChange = {
                scope.launch {
                    if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                        return@launch
                    }
                    onBanClearData(it)
                }
            }
        )
        SwitchPreference(
            enabled = LocalSectionEnable.current,
            checked = state.devMode,
            title = stringResource(R.string.switch_title_dev_mode),
            onCheckedChange = onDevMode
        )
    }
}

@Composable
private fun StatusGroup(state: MainState, onTick: () -> Unit) {
    val moduleStatus =
        stringResource(if (state.isActive) R.string.module_status_active else R.string.module_status_disable)
    val context = LocalContext.current

    Row(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = {
                if (state.isActive) {
                    Toast.makeText(context, moduleStatus, Toast.LENGTH_SHORT).show()
                }
                onTick()
            },
            colors = CardDefaults.defaultColors(
                color = (if (state.isActive) Color.Green else Color.Red).copy(alpha = 0.2f)
            ),
            showIndication = true
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .offset(32.dp, 32.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(170.dp),
                        imageVector = if (state.isActive) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                        tint = (if (state.isActive) Color.Green else Color.Red).copy(alpha = 0.4f),
                        contentDescription = moduleStatus
                    )
                }
                Column(modifier = Modifier.padding(start = 32.dp, top = 32.dp)) {
                    Text(
                        stringResource(R.string.group_title_module_status),
                        style = MiuixTheme.textStyles.title2
                    )
                    Text(moduleStatus)
                    if (state.isActive) {
                        Text(
                            text = stringResource(
                                id = R.string.module_active_mode, state.activeMode.description
                            ).format()
                        )
                    }
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
    gotoBannedApp: () -> Unit,
    gotoOpRecord: () -> Unit
) {
    val verifyPwdDialogHandle = rememberVerifyPwdDialog(
        title = stringResource(id = R.string.title_verify_password),
        errorText = stringResource(id = R.string.text_verify_password_error),
        onVerify = onVerifyPwd
    )
    val scope = rememberCoroutineScope()
    SettingsSection(
        enabled = state.isActive, title = stringResource(R.string.group_title_advanced)
    ) {
        val context = LocalContext.current
        var isIconShow by remember {
            mutableStateOf(true)
        }
        val fakeActivity = remember {
            ComponentName(
                context, "${BuildConfig.APPLICATION_ID}.FakeActivity"
            )
        }

        LaunchedEffect(Unit) {
            isIconShow =
                when (context.packageManager.getComponentEnabledSetting(
                    fakeActivity
                )) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> true
                    else -> false
                }
        }

        fun setIconShow(showIcon: Boolean) {
            if (showIcon) {
                context.packageManager.setComponentEnabledSetting(
                    fakeActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                context.packageManager.setComponentEnabledSetting(
                    fakeActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            isIconShow = showIcon
        }

        SwitchPreference(
            enabled = true,
            checked = isIconShow,
            title = stringResource(R.string.switch_title_show_app_icon),
            onCheckedChange = {
                setIconShow(!isIconShow)
            },
        )

        SwitchPreference(
            enabled = LocalSectionEnable.current,
            checked = state.useBannedList,
            title = stringResource(R.string.switch_title_use_banned_list),
            onCheckedChange = {
                scope.launch {
                    if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                        return@launch
                    }
                    onUseBannedList(it)
                }
            }
        )
        AnimatedVisibility(
            state.isActive && state.useBannedList,
        ) {
            ArrowPreference(title = stringResource(R.string.menulink_title_banned_app), onClick = {
                scope.launch {
                    if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                        return@launch
                    }
                    gotoBannedApp()
                }
            })
        }
        ArrowPreference(
            enabled = LocalSectionEnable.current,
            title = stringResource(R.string.menulink_view_operation_records),
            onClick = gotoOpRecord
        )
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
    SettingsSection(
        enabled = state.isActive, title = stringResource(R.string.group_title_security)
    ) {
        SwitchPreference(
            enabled = LocalSectionEnable.current,
            checked = state.hasPwd,
            title = stringResource(R.string.switch_title_use_separate_password),
            summary = stringResource(R.string.switch_subtitle_use_separate_password),
            onCheckedChange = { enable ->
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
        )
        AnimatedVisibility(
            state.isActive && state.hasPwd,
        ) {
            ArrowPreference(title = stringResource(R.string.menulink_change_password), onClick = {
                scope.launch {
                    if (verifyPwdDialogHandle.verify()) {
                        onSetPwd(setPwdDialogHandle.awaitInput())
                    }
                }
            })
        }
        SwitchPreference(
            enabled = LocalSectionEnable.current,
            checked = state.showConfirm,
            title = stringResource(R.string.switch_title_show_confirm),
            summary = stringResource(R.string.switch_subtitle_show_confirm),
            onCheckedChange = onShowConfirm
        )
    }
}