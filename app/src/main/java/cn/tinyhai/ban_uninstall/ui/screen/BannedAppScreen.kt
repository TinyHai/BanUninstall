package cn.tinyhai.ban_uninstall.ui.screen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import cn.tinyhai.compose.dragdrop.AnimatedDragDropBox
import cn.tinyhai.compose.dragdrop.DropTarget
import cn.tinyhai.compose.dragdrop.LocalDragDrop
import cn.tinyhai.compose.dragdrop.modifier.dragTarget
import cn.tinyhai.compose.dragdrop.rememberDragTargetState
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

    BottomSheetScaffold(
        sheetContent = {
            BoxWithConstraints(contentAlignment = Alignment.TopCenter) {
                BannedAppGrid(
                    isBottomSheetExpended = scaffoldState.bottomSheetState.currentValue != SheetValue.PartiallyExpanded,
                    onAppClick = {
                        viewModel.onBannedAppClick(it)
                    },
                    bannedApps = state.bannedAppInfos,
                    selectedApps = state.selectedInBanned,
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier
                        .height(maxHeight * 0.7f)
                )
            }
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
        scaffoldState = scaffoldState
    ) {
        AnimatedDragDropBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            scale = 1.5f,
            alpha = 0.5f
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
                    isBottomSheetExpended = scaffoldState.bottomSheetState.currentValue != SheetValue.PartiallyExpanded,
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

            val dragDropState = LocalDragDrop.current
            if (dragDropState.isDragging) {
                DropTarget<PkgInfo>(
                    onDrop = { pkgInfo ->
                        pkgInfo?.let {
                            viewModel.onBanPkgs(listOf(it))
                        }
                    },
                    modifier = Modifier
                        .padding(PaddingValues(bottom = 24.dp))
                        .size(300.dp, 128.dp)
                        .align(Alignment.BottomCenter)
                ) { isInBound, _ ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
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
                                text = stringResource(if (isInBound) R.string.text_drag_target_in_bound else R.string.text_drag_target_default),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                showFunctionBar,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(
                    Alignment.BottomCenter
                )
            ) {
                Row(
                    Modifier
                        .padding(bottom = 12.dp)
                        .shadow(4.dp, shape = RoundedCornerShape(50))
                        .width(300.dp)
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.onFreedSelectAll() }) {
                        Text(stringResource(R.string.text_select_all))
                    }
                    IconButton(onClick = { viewModel.onBanPkgs(state.selectedInFreed.map { it.pkgInfo }) }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { viewModel.clearSelected() }) {
                        Text(stringResource(R.string.text_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionBar(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Green))
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

    SideEffect {
        Log.d(TAG, selectedApps.map { it.label }.toString())
    }

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
                    val appIcon = @Composable {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = it.icon,
                                contentDescription = it.label,
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                            if (it.isDual) {
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
                    val dragTargetState =
                        rememberDragTargetState(dataToDrop = it.pkgInfo, draggableContent = appIcon)
                    Column(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !LocalDragDrop.current.isDragging && !isBottomSheetExpended,
                                onClick = { onAppClick(it) }
                            )
                            .then(
                                if (it in selectedApps) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .dragTarget(dragTargetState, enable = !isBottomSheetExpended)
                                .alpha(if (dragTargetState.isDragging) 0f else 1f),
                        ) {
                            appIcon()
                        }
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
private fun BannedAppGrid(
    isBottomSheetExpended: Boolean,
    onAppClick: (AppInfo) -> Unit,
    bannedApps: List<AppInfo>,
    selectedApps: List<AppInfo>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.padding(vertical = 24.dp)) {
            Text(
                text = stringResource(R.string.app_title_banned_app_bottom_sheet),
                style = MaterialTheme.typography.titleLarge
            )
        }
        Box(modifier = modifier) {
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
                                    enabled = isBottomSheetExpended,
                                    onClick = { onAppClick(it) }
                                )
                                .then(
                                    if (it in selectedApps) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AsyncImage(
                                model = it.icon,
                                contentDescription = it.label,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = if (it.isDual) stringResource(
                                    R.string.text_app_label_dual,
                                    it.label
                                ) else it.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
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
