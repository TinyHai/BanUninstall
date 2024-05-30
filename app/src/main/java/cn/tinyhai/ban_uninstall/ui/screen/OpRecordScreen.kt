package cn.tinyhai.ban_uninstall.ui.screen

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.tinyhai.ban_uninstall.R
import cn.tinyhai.ban_uninstall.auth.entities.OpResult
import cn.tinyhai.ban_uninstall.auth.entities.OpType
import cn.tinyhai.ban_uninstall.ui.component.TooltipBoxWrapper
import cn.tinyhai.ban_uninstall.ui.component.rememberVerifyPwdDialog
import cn.tinyhai.ban_uninstall.ui.compositionlocal.DateFormats
import cn.tinyhai.ban_uninstall.ui.compositionlocal.LocalDateFormats
import cn.tinyhai.ban_uninstall.vm.OpRecordInfo
import cn.tinyhai.ban_uninstall.vm.OpRecordViewModel
import coil.compose.AsyncImage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

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
                    TooltipBoxWrapper(tooltipText = stringResource(R.string.icon_description_filter_list)) {
                        var showMenu by rememberSaveable {
                            mutableStateOf(false)
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.icon_description_filter_list)
                            )
                        }

                        @Composable
                        fun menuItem(text: String, show: Boolean, onChange: (Boolean) -> Unit) {
                            DropdownMenuItem(
                                text = { Text(text) },
                                onClick = { onChange(show.not()) },
                                leadingIcon = {
                                    Checkbox(
                                        checked = show,
                                        onCheckedChange = onChange,
                                    )
                                }
                            )
                        }

                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            menuItem(
                                text = stringResource(id = R.string.text_allowed),
                                show = state.showAllowed,
                                onChange = viewModel::showAllowed
                            )
                            menuItem(
                                text = stringResource(id = R.string.text_prevented),
                                show = state.showPrevented,
                                onChange = viewModel::showPrevented
                            )
                            menuItem(
                                text = stringResource(id = R.string.text_uninstall_app),
                                show = state.showUninstall,
                                onChange = viewModel::showUninstall
                            )
                            menuItem(
                                text = stringResource(id = R.string.text_clear_app_data),
                                show = state.showClearData,
                                onChange = viewModel::showClearData
                            )
                        }
                    }
                }
            )
        }
    ) {
        val locale = LocalConfiguration.current.locale
        val dateFormats = remember {
            DateFormats(
                dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale),
                timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
            )
        }
        val filteredRecords = state.records.asSequence().filter {
            when (it.opResult) {
                OpResult.Allowed -> state.showAllowed
                OpResult.Prevented -> state.showPrevented
                else -> true
            }
        }.filter {
            when (it.opType) {
                OpType.Uninstall -> state.showUninstall
                OpType.ClearData -> state.showClearData
            }
        }.toList()

        CompositionLocalProvider(LocalDateFormats provides dateFormats) {
            OpRecordList(
                isRefreshing = state.isRefreshing,
                onRefreshing = viewModel::refresh,
                records = filteredRecords,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun OpRecordList(
    isRefreshing: Boolean,
    onRefreshing: () -> Unit,
    records: List<OpRecordInfo>,
    modifier: Modifier = Modifier
) {
    val refreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefreshing)
    val groupedRecords = records.groupBy {
        val date = it.opDate
        Date(date.year, date.month, date.date)
    }
    Box(modifier = modifier.pullRefresh(refreshState)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp)) {
            if (groupedRecords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.text_empty_list))
                    }
                }
            } else {
                groupedRecords.forEach { (date, records) ->
                    stickyHeader {
                        Surface(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .height(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.CenterStart) {
                                Text(
                                    text = LocalDateFormats.current.dateFormat.format(date),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                    items(records, key = { it.hashCode() }) {
                        OpRecordItem(
                            record = it, modifier = Modifier
                                .padding(vertical = 8.dp)
                                .fillParentMaxWidth()
                        )
                    }
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
private fun OpRecordItem(record: OpRecordInfo, modifier: Modifier = Modifier) {
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
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                Text(text = LocalDateFormats.current.timeFormat.format(record.opDate))
                Spacer(modifier = Modifier.weight(1f))
                Text(text = result)
            }
        }
    }
}