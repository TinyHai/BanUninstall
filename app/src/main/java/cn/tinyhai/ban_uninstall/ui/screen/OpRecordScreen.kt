package cn.tinyhai.ban_uninstall.ui.screen

import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.collection.intListOf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.tinyhai.ban_uninstall.R
import cn.tinyhai.ban_uninstall.auth.entities.OpResult
import cn.tinyhai.ban_uninstall.auth.entities.OpType
import cn.tinyhai.ban_uninstall.ui.component.rememberVerifyPwdDialog
import cn.tinyhai.ban_uninstall.ui.compositionlocal.DateFormats
import cn.tinyhai.ban_uninstall.ui.compositionlocal.LocalDateFormats
import cn.tinyhai.ban_uninstall.ui.navigation3.Navigator
import cn.tinyhai.ban_uninstall.vm.OpRecordInfo
import cn.tinyhai.ban_uninstall.vm.OpRecordViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.DateFormat
import java.util.Date

private const val TAG = "OpRecordScreen"

@Composable
fun OpRecordScreen(navigator: Navigator) {
    val viewModel: OpRecordViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
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
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "navigationUp"
                        )
                    }
                },
                title = stringResource(R.string.app_title_operation_record),
                scrollBehavior = scrollBehavior,
                actions = {
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
                    fun menuItem(
                        text: String,
                        show: Boolean,
                        onChange: (Boolean) -> Unit,
                        index: Int
                    ) {
                        DropdownImpl(
                            text = text,
                            optionSize = 4,
                            isSelected = show,
                            index = index,
                            onSelectedIndexChange = {
                                onChange(show.not())
                            }
                        )
                    }
                    OverlayListPopup(show = showMenu, onDismissRequest = { showMenu = false }) {
                        ListPopupColumn {
                            menuItem(
                                text = stringResource(id = R.string.text_allowed),
                                show = state.showAllowed,
                                onChange = viewModel::showAllowed,
                                index = 0
                            )
                            menuItem(
                                text = stringResource(id = R.string.text_prevented),
                                show = state.showPrevented,
                                onChange = viewModel::showPrevented,
                                index = 1
                            )
                            menuItem(
                                text = stringResource(id = R.string.text_uninstall_app),
                                show = state.showUninstall,
                                onChange = viewModel::showUninstall,
                                index = 2
                            )
                            menuItem(
                                text = stringResource(id = R.string.text_clear_app_data),
                                show = state.showClearData,
                                onChange = viewModel::showClearData,
                                index = 2
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
            if (state.records.isEmpty() && !state.hasLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it),
                    contentAlignment = Alignment.Center
                ) {
                    InfiniteProgressIndicator()
                }
            } else {
                OpRecordList(
                    isRefreshing = state.isRefreshing,
                    onRefreshing = viewModel::refresh,
                    records = filteredRecords,
                    scrollBehavior = scrollBehavior,
                    contentPadding = it,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(it)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OpRecordList(
    isRefreshing: Boolean,
    onRefreshing: () -> Unit,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    records: List<OpRecordInfo>,
    modifier: Modifier = Modifier
) {
    val groupedRecords = records.groupBy {
        val date = it.opDate
        Date(date.year, date.month, date.date)
    }
    PullToRefresh(
        isRefreshing,
        onRefresh = onRefreshing,
        topAppBarScrollBehavior = scrollBehavior,
        contentPadding = contentPadding
    ) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp, 8.dp)
        ) {
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
                                    style = MiuixTheme.textStyles.title2
                                )
                            }
                        }
                    }
                    items(records, key = { it.hashCode() }) {
                        OpRecordItem(
                            record = it, modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillParentMaxWidth()
                        )
                    }
                }
            }
        }
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
    Card(modifier = modifier) {
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