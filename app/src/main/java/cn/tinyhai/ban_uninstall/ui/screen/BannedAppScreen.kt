package cn.tinyhai.ban_uninstall.ui.screen

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.tinyhai.ban_uninstall.R
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.ui.component.SearchAppBar
import cn.tinyhai.ban_uninstall.vm.AppInfo
import cn.tinyhai.ban_uninstall.vm.BannedAppViewModel
import cn.tinyhai.compose.dragdrop.*
import cn.tinyhai.compose.dragdrop.modifier.dragTarget
import cn.tinyhai.compose.dragdrop.modifier.dropTarget
import coil.compose.AsyncImage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

private const val TAG = "BannedAppScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun BannedAppScreen(navigator: DestinationsNavigator) {
    val viewModel = viewModel<BannedAppViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.refresh()
    }

    val scaffoldState = rememberBottomSheetScaffoldState()
    val bottomSheetState = scaffoldState.bottomSheetState
    val currentBottomSheetValue = bottomSheetState.currentValue
    LaunchedEffect(currentBottomSheetValue) {
        viewModel.clearSelected()
    }

    val isBottomSheetExpended by remember {
        derivedStateOf {
            bottomSheetState.currentValue == SheetValue.Expanded
        }
    }

    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .fillMaxSize()
            .clipToBounds(),
    ) {
        BottomSheetScaffold(
            sheetContent = {
                BannedAppGrid(
                    isBottomSheetExpended = isBottomSheetExpended,
                    onAppClick = {
                        viewModel.onBannedAppClick(it)
                    },
                    onFreePkg = { viewModel.onFreePkgs(listOf(it)) },
                    onSelectAllBannedApp = { viewModel.onBannedSelectAll() },
                    onFreeSelected = { viewModel.onFreeSelectedBanned() },
                    onClearAllBannedApp = { viewModel.clearSelected() },
                    bannedApps = state.bannedAppInfos,
                    selectedApps = state.selectedInBanned,
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.618f)
                )
            },
            topBar = {
                SearchAppBar(
                    title = { Text(text = stringResource(id = R.string.app_title_banned_app_app_bar)) },
                    searchText = state.query,
                    onSearchTextChange = viewModel::onQueryChange,
                    onSearchStart = { viewModel.clearSelected() },
                    onClearClick = viewModel::onSearchClear,
                    onBackClick = { navigator.navigateUp() }
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
                    title = { Text(text = stringResource(id = R.string.app_title_banned_app_app_bar)) },
                )
            },
            scaffoldState = scaffoldState,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
            ) {
                val functionBarSize = DpSize(300.dp, 56.dp)
                val functionBarBottomPadding = 12.dp
                val showFunctionBar = state.selectedInFreed.isNotEmpty()
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    FreedAppGrid(
                        isRefreshing = state.isRefreshing,
                        isBottomSheetExpended = isBottomSheetExpended,
                        onRefreshing = { viewModel.refresh() },
                        onAppClick = {
                            viewModel.onFreedAppClick(it)
                        },
                        freedApps = state.freedAppInfos,
                        selectedApps = state.selectedInFreed,
                        contentPadding = if (showFunctionBar) PaddingValues(
                            8.dp,
                            8.dp,
                            8.dp,
                            8.dp + functionBarSize.height + functionBarBottomPadding
                        ) else PaddingValues(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }

                if (!isBottomSheetExpended) {
                    DropToBanPkgBox(
                        onBanPkg = { viewModel.onBanPkgs(listOf(it)) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                AnimatedVisibility(
                    showFunctionBar,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MultipleSelectBar(
                        selectAll = { viewModel.onFreedSelectAll() },
                        performIcon = Icons.Default.Add,
                        onPerform = { viewModel.onBanSelectedFreed() },
                        clearAll = { viewModel.clearSelected() },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .shadow(4.dp, shape = RoundedCornerShape(50))
                            .width(300.dp)
                            .height(56.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            }
        }
    }
}

@Composable
private fun DropToBanPkgBox(onBanPkg: (PkgInfo) -> Unit, modifier: Modifier = Modifier) {
    val dragDropState = LocalDragDrop.current
    AnimatedVisibility(
        dragDropState.isDragging,
        modifier = modifier,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        val dropTargetState = rememberDropTargetState<PkgInfo> { pkgInfo ->
            pkgInfo?.let {
                onBanPkg(it)
            }
        }
        val isInBound = dropTargetState.isInBound
        Surface(
            modifier = Modifier
                .padding(PaddingValues(bottom = 24.dp))
                .dropTarget(dropTargetState)
                .size(300.dp, 128.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isInBound) 0.9f else 0.6f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceAround,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "",
                    modifier = Modifier.requiredSize(40.dp)
                )
                Text(
                    text = stringResource(if (isInBound) R.string.text_drag_target_add_in_bound else R.string.text_drag_target_add_default),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MultipleSelectBar(
    selectAll: () -> Unit,
    performIcon: ImageVector,
    onPerform: () -> Unit,
    clearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { selectAll() }) {
            Text(stringResource(R.string.text_select_all))
        }
        IconButton(
            onClick = {
                onPerform()
                Log.d(TAG, "hhh")
            },
        ) {
            Icon(
                performIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        TextButton(onClick = { clearAll() }) {
            Text(stringResource(R.string.text_cancel))
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FreedAppGrid(
    isRefreshing: Boolean,
    isBottomSheetExpended: Boolean,
    onRefreshing: () -> Unit,
    onAppClick: (AppInfo) -> Unit,
    freedApps: List<AppInfo>,
    selectedApps: List<AppInfo>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefreshing
    )

    Box(modifier = modifier.pullRefresh(pullRefreshState)) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(freedApps, key = { it.key }) {
                Surface(
                    modifier = Modifier
                        .aspectRatio(1.2f),
                ) {
                    Column(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !LocalDragDrop.current.isActive && !isBottomSheetExpended && !isRefreshing,
                                onClick = { onAppClick(it) }
                            )
                            .then(
                                if (it in selectedApps) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val dragTargetState =
                            rememberDragTargetState(dataToDrop = it.pkgInfo)
                        AppIcon(
                            it.icon,
                            it.label,
                            it.isDual,
                            modifier = Modifier
                                .size(64.dp)
                                .dragTarget(
                                    dragTargetState,
                                    enable = !isBottomSheetExpended && selectedApps.isEmpty(),
                                    hiddenWhileDragging = true
                                )
                        )
                        val alpha = animateFloatAsState(if (dragTargetState.isDragging) 0f else 1f)
                        Text(
                            text = if (it.isDual) stringResource(
                                R.string.text_app_label_dual,
                                it.label
                            ) else it.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(alpha.value)
                        )
                    }
                }
            }
        }
        if (freedApps.isEmpty()) {
            Text(
                text = stringResource(R.string.text_empty_list),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(
                Alignment.TopCenter
            )
        )
    }
}

@Composable
private fun AppIcon(
    icon: Drawable,
    label: String,
    isDual: Boolean,
    modifier: Modifier = Modifier
) {
    var change by remember {
        mutableStateOf(false)
    }
    Box(
        Modifier
            .drawWithContent {
                change
                drawContent()
            }
            .then(modifier),
    ) {
        AsyncImage(
            model = icon,
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            onState = {
                change = change.not()
            }
        )
        if (isDual) {
            Box(
                modifier = Modifier
                    .padding(PaddingValues(bottom = 4.dp, end = 4.dp))
                    .clip(CircleShape)
                    .size(16.dp)
                    .background(Color.Yellow)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun BannedAppGrid(
    isBottomSheetExpended: Boolean,
    onAppClick: (AppInfo) -> Unit,
    onFreePkg: (PkgInfo) -> Unit,
    onSelectAllBannedApp: () -> Unit,
    onFreeSelected: () -> Unit,
    onClearAllBannedApp: () -> Unit,
    bannedApps: List<AppInfo>,
    selectedApps: List<AppInfo>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val topBarType = when {
            !isBottomSheetExpended -> TopBarType.Normal
            LocalDragDrop.current.isDragging -> TopBarType.Dragging
            selectedApps.isNotEmpty() -> TopBarType.MultipleSelect
            else -> TopBarType.Normal
        }
        BannedAppTopBar(
            topBarType,
            onFreePkg = onFreePkg,
            onSelectAll = onSelectAllBannedApp,
            onFreeSelected = onFreeSelected,
            onClearAll = onClearAllBannedApp,
            modifier = Modifier.height(72.dp)
        )
        Box {
            LazyVerticalGrid(
                GridCells.Fixed(3),
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(bannedApps, key = { it.key }) {
                    Surface(
                        modifier = Modifier
                            .aspectRatio(1.2f),
                    ) {
                        Column(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = isBottomSheetExpended && !LocalDragDrop.current.isActive,
                                    onClick = { onAppClick(it) }
                                )
                                .then(
                                    if (it in selectedApps) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val dragTargetState =
                                rememberDragTargetState(dataToDrop = it.pkgInfo)
                            AppIcon(
                                it.icon,
                                it.label,
                                it.isDual,
                                modifier = Modifier
                                    .size(64.dp)
                                    .dragTarget(
                                        dragTargetState,
                                        enable = isBottomSheetExpended && selectedApps.isEmpty(),
                                        hiddenWhileDragging = true
                                    )
                            )
                            val alpha =
                                animateFloatAsState(if (dragTargetState.isDragging) 0f else 1f)
                            Text(
                                text = if (it.isDual) stringResource(
                                    R.string.text_app_label_dual,
                                    it.label
                                ) else it.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
                            )
                        }
                    }
                }
            }
            if (bannedApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.text_empty_list),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private sealed interface TopBarType {
    object Normal : TopBarType
    object Dragging : TopBarType

    object MultipleSelect : TopBarType
}

@Composable
private fun BannedAppTopBar(
    type: TopBarType,
    onFreePkg: (PkgInfo) -> Unit,
    onSelectAll: () -> Unit,
    onFreeSelected: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Crossfade(type, modifier, label = type.toString()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (it) {
                TopBarType.Dragging -> {
                    val dropTargetState = rememberDropTargetState<PkgInfo> { pkgInfo ->
                        pkgInfo?.let(onFreePkg)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.6f)
                            .dropTarget(dropTargetState)
                            .graphicsLayer {
                                alpha = if (dropTargetState.isInBound) 1f else 0.6f
                            }
                    ) {
                        Column(
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                            )
                            val text = if (dropTargetState.isInBound) {
                                stringResource(R.string.text_drag_target_remove_in_bound)
                            } else {
                                stringResource(R.string.text_drag_target_remove_default)
                            }
                            Text(text, textAlign = TextAlign.Center)
                        }
                    }
                }

                TopBarType.MultipleSelect -> {
                    MultipleSelectBar(
                        selectAll = onSelectAll,
                        performIcon = Icons.Default.Delete,
                        onPerform = onFreeSelected,
                        clearAll = onClearAll,
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.8f)
                    )
                }

                TopBarType.Normal -> {
                    Text(
                        text = stringResource(R.string.app_title_banned_app_bottom_sheet),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}