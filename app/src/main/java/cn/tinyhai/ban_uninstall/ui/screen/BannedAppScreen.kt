package cn.tinyhai.ban_uninstall.ui.screen

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.tinyhai.ban_uninstall.R
import cn.tinyhai.ban_uninstall.ui.component.SearchBarFake
import cn.tinyhai.ban_uninstall.ui.component.SearchPager
import cn.tinyhai.ban_uninstall.ui.component.SearchStatus
import cn.tinyhai.ban_uninstall.ui.navigation3.Navigator
import cn.tinyhai.ban_uninstall.utils.toLowercasePinyin
import cn.tinyhai.ban_uninstall.vm.AppInfo
import cn.tinyhai.ban_uninstall.vm.BannedAppViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val TAG = "BannedAppScreen"

@Composable
fun BannedAppScreen(navigator: Navigator) {
    val viewModel = viewModel<BannedAppViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scrollBehavior = MiuixScrollBehavior()
    val density = LocalDensity.current
    // Quantize the collapse-driven padding to whole pixels to avoid sub-pixel jitter while collapsing.
    val dynamicTopPadding by remember(scrollBehavior, density) {
        derivedStateOf {
            with(density) {
                (12.dp * (1f - scrollBehavior.state.collapsedFraction)).roundToPx().toDp()
            }
        }
    }
    val searchBarLabel = stringResource(R.string.hint_search_app)
    var searchStatus by remember {
        mutableStateOf(SearchStatus(searchBarLabel))
    }
    Scaffold(
        topBar = {
            searchStatus.TopAppBarAnim {
                TopAppBar(
                    title = stringResource(R.string.title_app_manage),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "navigationUp"
                            )
                        }
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                            .onGloballyPositioned { coordinates ->
                                with(density) {
                                    val searchOffsetY = coordinates.positionInWindow().y.toDp()
                                    if (searchOffsetY != searchStatus.offsetY) {
                                        searchStatus = searchStatus.copy(offsetY = searchOffsetY)
                                    }
                                }
                            }
                            .then(
                                if (searchStatus.isCollapsed()) {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures {
                                            searchStatus =
                                                searchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        SearchBarFake(searchStatus.label, dynamicTopPadding)
                    }
                }
            }
        },
        popupHost = {
            val queryPinyin by remember {
                derivedStateOf { searchStatus.searchText.toLowercasePinyin() }
            }
            val queryLower by remember {
                derivedStateOf { searchStatus.searchText.lowercase() }
            }
            var filteredAppInfos by remember {
                mutableStateOf(emptyList<AppInfo>())
            }
            LaunchedEffect(queryLower, queryPinyin) {
                when {
                    queryPinyin.isBlank() && queryLower.isBlank() -> {
                        searchStatus =
                            searchStatus.copy(resultStatus = SearchStatus.ResultStatus.DEFAULT)
                    }

                    else -> {
                        searchStatus =
                            searchStatus.copy(resultStatus = SearchStatus.ResultStatus.LOAD)
                        filteredAppInfos = withContext(Dispatchers.IO) {
                            state.appInfos.filter {
                                it.label.toLowercasePinyin()
                                    .contains(queryPinyin) || it.pkgInfo.packageName.contains(
                                    queryLower
                                )
                            }
                        }
                        if (filteredAppInfos.isEmpty()) {
                            searchStatus =
                                searchStatus.copy(resultStatus = SearchStatus.ResultStatus.EMPTY)
                        } else {
                            searchStatus =
                                searchStatus.copy(resultStatus = SearchStatus.ResultStatus.SHOW)
                        }
                    }
                }
            }
            searchStatus.SearchPager(
                onSearchStatusChange = {
                    searchStatus = it
                },
                searchBarTopPadding = dynamicTopPadding,
                defaultResult = {},
            ) {
                itemsIndexed(filteredAppInfos, key = { _, item -> item.key }) { idx, item ->
                    AppItem(
                        idx = idx,
                        total = filteredAppInfos.size,
                        checked = item.banned,
                        onClick = {
                            if (item.banned) {
                                viewModel.onFreePkgs(listOf(item.pkgInfo))
                            } else {
                                viewModel.onBanPkgs(listOf(item.pkgInfo))
                            }
                        },
                        icon = item.icon,
                        label = item.label,
                        isDual = item.isDual,
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .requiredHeight(60.dp)
                    )
                }
            }
        }
    ) {
        if (state.appInfos.isEmpty() && !state.hasLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        it
                    ),
                contentAlignment = Alignment.Center
            ) {
                InfiniteProgressIndicator()
            }
        } else {
            PullToRefresh(
                state.isRefreshing,
                onRefresh = viewModel::refresh,
                topAppBarScrollBehavior = scrollBehavior,
                contentPadding = it
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(it)
                        .padding(horizontal = 16.dp),
                ) {
                    val index = state.appInfos.indexOfFirst { !it.banned }
                    val bannedAppInfos = state.appInfos.subList(0, index)
                    val freedAppInfos = state.appInfos.subList(index, state.appInfos.size)
                    if (bannedAppInfos.isNotEmpty()) {
                        item(key = Int.MIN_VALUE) {
                            SmallTitle(
                                stringResource(
                                    R.string.small_title_banned_app,
                                    bannedAppInfos.size
                                ),
                            )
                        }
                        itemsIndexed(
                            bannedAppInfos, key = { _, item -> item.key },
                        ) { idx, item ->
                            AppItem(
                                idx = idx,
                                total = bannedAppInfos.size,
                                checked = true,
                                onClick = {
                                    viewModel.onFreePkgs(listOf(item.pkgInfo))
                                },
                                icon = item.icon,
                                label = item.label,
                                isDual = item.isDual,
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .requiredHeight(60.dp),
                            )
                        }
                    }


                    if (freedAppInfos.isNotEmpty()) {
                        item {
                            SmallTitle(
                                stringResource(R.string.small_title_freed_app, freedAppInfos.size),
                            )
                        }

                        itemsIndexed(freedAppInfos, key = { _, item -> item.key }) { idx, item ->
                            AppItem(
                                idx = idx,
                                total = freedAppInfos.size,
                                checked = false,
                                onClick = {
                                    viewModel.onBanPkgs(listOf(item.pkgInfo))
                                },
                                icon = item.icon,
                                label = item.label,
                                isDual = item.isDual,
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .requiredHeight(60.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItem(
    idx: Int,
    total: Int,
    checked: Boolean,
    icon: Drawable,
    label: String,
    isDual: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember(idx, total) {
        when (idx) {
            0 -> RoundedCornerShape(
                topStart = CardDefaults.CornerRadius,
                topEnd = CardDefaults.CornerRadius
            )

            total - 1 -> RoundedCornerShape(
                bottomStart = CardDefaults.CornerRadius,
                bottomEnd = CardDefaults.CornerRadius
            )

            else -> RectangleShape
        }
    }
    val colors = CardDefaults.defaultColors()
    CompositionLocalProvider(
        LocalContentColor provides colors.contentColor,
    ) {
        Box(
            modifier = modifier
                .semantics(mergeDescendants = false) {
                    isTraversalGroup = true
                }
                .clip(shape)
                .background(color = colors.color),
            propagateMinConstraints = true,
        ) {
            SwitchPreference(
                checked,
                onCheckedChange = { onClick() },
                title = label,
                startAction = {
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .requiredSize(40.dp),
                    ) {
                        AsyncImage(
                            model = icon,
                            contentDescription = label,
                            modifier = Modifier.fillMaxSize(),
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
            )
        }
    }
}