package cn.tinyhai.ban_uninstall.ui.screen

import android.text.format.DateFormat
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.tinyhai.ban_uninstall.R
import cn.tinyhai.ban_uninstall.auth.entities.OpResult
import cn.tinyhai.ban_uninstall.auth.entities.OpType
import cn.tinyhai.ban_uninstall.ui.component.TooltipBoxWrapper
import cn.tinyhai.ban_uninstall.ui.component.rememberVerifyPwdDialog
import cn.tinyhai.ban_uninstall.ui.compositionlocal.LocalDateFormat
import cn.tinyhai.ban_uninstall.vm.OpRecordInfo
import cn.tinyhai.ban_uninstall.vm.OpRecordViewModel
import coil.compose.AsyncImage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun OpRecordScreen(navigator: DestinationsNavigator) {
    val viewModel: OpRecordViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            val scope = rememberCoroutineScope()
            val verifyPwdDialogHandle = rememberVerifyPwdDialog(
                title = stringResource(id = R.string.title_verify_password),
                errorText = stringResource(id = R.string.text_verify_password_error),
                onVerify = viewModel::onVerifyPwd
            )
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "navigationUp"
                        )
                    }
                },
                title = { Text(text = stringResource(R.string.app_title_operation_record)) },
                actions = {
                    TooltipBoxWrapper(tooltipText = stringResource(R.string.icon_description_clear_all_records)) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (state.hasPwd && !verifyPwdDialogHandle.verify()) {
                                        return@launch
                                    }
                                    viewModel.clearAllRecords()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ClearAll,
                                contentDescription = stringResource(R.string.icon_description_clear_all_records)
                            )
                        }
                    }
                }
            )
        }
    ) {
        val dateFormat = DateFormat.getTimeFormat(LocalContext.current)
        CompositionLocalProvider(LocalDateFormat provides dateFormat) {
            OpRecordList(
                isRefreshing = state.isRefreshing,
                onRefreshing = viewModel::refresh,
                records = state.records,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun OpRecordList(
    isRefreshing: Boolean,
    onRefreshing: () -> Unit,
    records: List<OpRecordInfo>,
    modifier: Modifier = Modifier
) {
    val refreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefreshing)
    Box(modifier = modifier.pullRefresh(refreshState)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp)) {
            if (records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.text_empty_list))
                    }
                }
            } else {
                items(records, key = { it.hashCode() }) {
                    OpRecordItem(record = it)
                }
            }
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun OpRecordItem(record: OpRecordInfo) {
    val context = LocalContext.current
    val placeholderIcon = remember {
        AppCompatResources.getDrawable(context, android.R.drawable.sym_def_app_icon)
    }
    val opType = record.opType
    val unknownApp = stringResource(R.string.text_unknown_app)
    val uninstalledApp = stringResource(R.string.text_uninstalled_app)
    val rootLabel = stringResource(R.string.text_label_root)
    val shellLabel = stringResource(R.string.text_label_shell)
    val systemLabel = stringResource(R.string.text_label_system)
    val opLabel = record.opLabel ?: when (record.opUid) {
        0 -> rootLabel
        2000 -> shellLabel
        1000 -> systemLabel
        else -> unknownApp
    }
    val opTypeText = when (opType) {
        OpType.ClearData -> stringResource(R.string.text_clear_app_data)
        OpType.Uninstall -> stringResource(R.string.text_uninstall_app)
    }
    val isOpDual = record.opUid / 100_000 > 0
    val opContentText = buildString {
        append(
            if (isOpDual) stringResource(
                id = R.string.text_app_label_dual,
                opLabel
            ) else opLabel
        )
        append("(${record.opUid}) ")
        append(stringResource(id = R.string.text_try_op, opTypeText.lowercase()))
    }
    val label = (record.label ?: uninstalledApp).let {
        if (record.isDual) {
            stringResource(R.string.text_app_label_dual, it)
        } else {
            it
        }
    }
    val result = when (record.opResult) {
        OpResult.Prevented -> stringResource(R.string.text_prevented)
        OpResult.Allowed -> stringResource(R.string.text_allowed)
        OpResult.Unhandled -> stringResource(R.string.text_unhandled)
    }
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = opContentText)
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            AsyncImage(
                model = record.icon ?: placeholderIcon,
                contentDescription = opContentText,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = label)
                Text(text = record.packageName)
            }
        }
        Row {
            Text(text = LocalDateFormat.current.format(record.opDate))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = result)
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}