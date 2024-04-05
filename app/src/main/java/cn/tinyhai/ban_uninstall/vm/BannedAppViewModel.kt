package cn.tinyhai.ban_uninstall.vm

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tinyhai.ban_uninstall.App
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.transact.entities.PkgInfo
import cn.tinyhai.ban_uninstall.utils.HanziToPinyin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val label: String,
    val icon: Drawable,
    val pkgInfo: PkgInfo
) {
    val isDual get() = pkgInfo.userId > 1
    val key get() = pkgInfo.toString()
}

data class BannedAppState(
    val isRefreshing: Boolean,
    val originFreedAppInfos: List<AppInfo>,
    val originBannedAppInfos: List<AppInfo>,
    val selectedInFreed: List<AppInfo>,
    val selectedInBanned: List<AppInfo>,
    val query: String,
) {

    val freedAppInfos: List<AppInfo>
        get() {
            return if (query.isEmpty()) {
                originFreedAppInfos
            } else {
                val queryPinyin = HanziToPinyin.getInstance().toPinyin(query).lowercase()
                val queryLower = query.lowercase()
                originFreedAppInfos.filter {
                    val labelPinyin = HanziToPinyin.getInstance().toPinyin(it.label).lowercase()
                    labelPinyin.contains(queryPinyin)
                        || it.pkgInfo.packageName.lowercase().contains(queryLower)
                }
            }
        }
    val bannedAppInfos: List<AppInfo>
        get() {
            return if (query.isEmpty()) {
                originBannedAppInfos
            } else {
                val queryPinyin = HanziToPinyin.getInstance().toPinyin(query).lowercase()
                val queryLower = query.lowercase()
                originBannedAppInfos.filter {
                    val labelPinyin = HanziToPinyin.getInstance().toPinyin(it.label).lowercase()
                    labelPinyin.contains(queryPinyin)
                        || it.pkgInfo.packageName.lowercase().contains(queryLower)
                }
            }
        }

    companion object {
        val Empty = BannedAppState(
            false,
            originFreedAppInfos = emptyList(),
            originBannedAppInfos = emptyList(),
            selectedInFreed = emptyList(),
            selectedInBanned = emptyList(),
            query = ""
        )
    }
}

class BannedAppViewModel : ViewModel() {

    companion object {
        private const val TAG = "BannedAppViewModel"
    }

    private val client = TransactClient()

    private val _state = MutableStateFlow(BannedAppState.Empty)

    val state = _state.asStateFlow()

    private inline fun updateState(crossinline updater: (BannedAppState) -> BannedAppState) {
        _state.value = updater(state.value)
    }

    private val comparator: Comparator<AppInfo> by lazy {
        compareBy<AppInfo> {
            HanziToPinyin.getInstance().toPinyin(it.label)
        } then compareBy {
            it.pkgInfo.packageName
        } then compareBy {
            it.isDual
        }
    }

    fun refresh() {
        viewModelScope.launch {
            updateState { it.copy(isRefreshing = true) }
            val pm = App.app.packageManager
            withContext(Dispatchers.IO) {
                val allPackages = client?.fetchInstalledPackages() ?: emptyList()
                val appInfoList = allPackages.map {
                    val label = it.applicationInfo.loadLabel(pm).toString()
                    val icon = it.applicationInfo.loadIcon(pm)
                    val packageName = it.packageName ?: it.applicationInfo.packageName
                    val uid = it.applicationInfo.uid
                    AppInfo(label, icon, PkgInfo(packageName, uid / 10_000))
                }
                val bannedPkgInfos = client?.fetchAllBannedPackages() ?: emptyList()
                val bannedAppInfos = ArrayList<AppInfo>()
                val freedPkgInfos = appInfoList.toMutableList().apply {
                    for (bannedPkgInfo in bannedPkgInfos) {
                        val idx = indexOfFirst { it.pkgInfo == bannedPkgInfo }
                        if (idx >= 0) {
                            bannedAppInfos.add(removeAt(idx))
                        }
                    }
                }
                updateState {
                    it.copy(
                        isRefreshing = false,
                        originFreedAppInfos = freedPkgInfos.sortedWith(comparator),
                        originBannedAppInfos = bannedAppInfos.sortedWith(comparator),
                        selectedInFreed = emptyList(),
                        selectedInBanned = emptyList(),
                        query = ""
                    )
                }
            }
        }
    }

    fun onBanPkgs(pkgInfos: List<PkgInfo>) {
        val client = client ?: return
        viewModelScope.launch {
            val banned = client.banPackage(pkgInfos)
            if (banned.isEmpty()) {
                return@launch
            }
            updateState {
                val newFreePkgInfos = it.originFreedAppInfos.toMutableList()
                val newBannedPkgInfos = it.originBannedAppInfos.toMutableList()
                newFreePkgInfos.moveTo(newBannedPkgInfos) {
                    it.pkgInfo in banned
                }
                it.copy(
                    originFreedAppInfos = newFreePkgInfos,
                    originBannedAppInfos = newBannedPkgInfos
                )
            }
        }
    }

    fun onFreePkgs(pkgInfos: List<PkgInfo>) {
        val client = client ?: return
        viewModelScope.launch {
            val freed = client.banPackage(pkgInfos)
            if (freed.isEmpty()) {
                return@launch
            }
            updateState {
                val newFreePkgInfos = it.originFreedAppInfos.toMutableList()
                val newBannedPkgInfos = it.originBannedAppInfos.toMutableList()
                newBannedPkgInfos.moveTo(newFreePkgInfos) {
                    it.pkgInfo in freed
                }
                it.copy(
                    originFreedAppInfos = newFreePkgInfos,
                    originBannedAppInfos = newBannedPkgInfos
                )
            }
        }
    }

    fun onFreedAppClick(appInfo: AppInfo) {
        updateState {
            val newList = it.selectedInFreed.toMutableList().apply {
                val idx = indexOf(appInfo)
                if (idx < 0) {
                    add(appInfo)
                } else {
                    removeAt(idx)
                }
            }
            it.copy(selectedInFreed = newList)
        }
    }

    fun onFreedSelectAll() {
        updateState {
            it.copy(selectedInFreed = it.freedAppInfos)
        }
    }

    fun onBannedSelectAll() {
        updateState {
            it.copy(selectedInBanned = it.bannedAppInfos)
        }
    }

    fun onBannedAppClick(appInfo: AppInfo) {
        updateState {
            val newList = it.selectedInBanned.toMutableList().apply {
                val idx = indexOf(appInfo)
                if (idx < 0) {
                    add(appInfo)
                } else {
                    removeAt(idx)
                }
            }
            it.copy(selectedInBanned = newList)
        }
    }

    fun onQueryChange(newQuery: String) {
        updateState { it.copy(query = newQuery.trim()) }
    }

    fun onSearchClear() {
        updateState { it.copy(query = "") }
    }

    fun clearSelected() {
        updateState {
            it.copy(selectedInFreed = emptyList(), selectedInBanned = emptyList())
        }
    }

    private fun MutableList<AppInfo>.moveTo(
        desList: MutableList<AppInfo>,
        predicate: (AppInfo) -> Boolean
    ) {
        val itor = iterator()
        while (itor.hasNext()) {
            val appInfo = itor.next()
            if (predicate(appInfo)) {
                itor.remove()
                desList.add(appInfo)
            }
        }
    }
}