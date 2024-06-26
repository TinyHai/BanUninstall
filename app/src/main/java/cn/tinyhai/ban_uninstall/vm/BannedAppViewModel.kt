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
    val isRefreshing: Boolean = false,
    val originFreedAppInfos: List<AppInfo> = emptyList(),
    val originBannedAppInfos: List<AppInfo> = emptyList(),
    val selectedInFreed: List<AppInfo> = emptyList(),
    val selectedInBanned: List<AppInfo> = emptyList(),
    val query: String = "",
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
            }.sortedWith(comparator)
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
            }.sortedWith(comparator)
        }

    companion object {
        val Empty = BannedAppState()

        private val comparator: Comparator<AppInfo> by lazy {
            compareBy<AppInfo> {
                HanziToPinyin.getInstance().toPinyin(it.label)
            } then compareBy {
                it.pkgInfo.packageName
            } then compareBy {
                it.isDual
            }
        }
    }
}

class BannedAppViewModel : ViewModel() {

    companion object {
        private const val TAG = "BannedAppViewModel"
    }

    private val tempOutput = ArrayList<String>()
        get() {
            field.clear()
            return field
        }

    private val client = TransactClient()

    private val _state = MutableStateFlow(BannedAppState.Empty)

    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            updateState(_state) { it.copy(isRefreshing = true) }
            val pm = App.app.packageManager
            withContext(Dispatchers.IO) {
                val allPackages = client.packages.list
                val appInfoList = allPackages.map {
                    val label = it.applicationInfo.loadLabel(pm).toString()
                    val icon = it.applicationInfo.loadIcon(pm)
                    val packageName = it.packageName ?: it.applicationInfo.packageName
                    val uid = it.applicationInfo.uid
                    AppInfo(label, icon, PkgInfo(packageName, uid / 100_000))
                }
                val bannedPkgInfos = client.allBannedPackages.map { PkgInfo(it) }
                val bannedAppInfos = ArrayList<AppInfo>()
                val freedPkgInfos = appInfoList.toMutableList().apply {
                    for (bannedPkgInfo in bannedPkgInfos) {
                        val idx = indexOfFirst { it.pkgInfo == bannedPkgInfo }
                        if (idx >= 0) {
                            bannedAppInfos.add(removeAt(idx))
                        }
                    }
                }
                updateState(_state) {
                    it.copy(
                        isRefreshing = false,
                        originFreedAppInfos = freedPkgInfos,
                        originBannedAppInfos = bannedAppInfos,
                        selectedInFreed = emptyList(),
                        selectedInBanned = emptyList(),
                        query = ""
                    )
                }
            }
        }
    }

    fun onBanPkgs(pkgInfos: List<PkgInfo>) {
        viewModelScope.launch {
            val banned = tempOutput
            withContext(Dispatchers.IO) {
                client.banPackage(pkgInfos.map { it.toString() }, banned)
            }

            if (banned.isEmpty()) {
                return@launch
            }

            val bannedPkgInfos = banned.map { PkgInfo(it) }
            updateState(_state) {
                val newFreePkgInfos = it.originFreedAppInfos.toMutableList()
                val newBannedPkgInfos = it.originBannedAppInfos.toMutableList()
                newFreePkgInfos.moveTo(newBannedPkgInfos) {
                    it.pkgInfo in bannedPkgInfos
                }
                it.copy(
                    originFreedAppInfos = newFreePkgInfos,
                    originBannedAppInfos = newBannedPkgInfos
                )
            }
        }
    }

    fun onFreePkgs(pkgInfos: List<PkgInfo>) {
        viewModelScope.launch {
            val freed = tempOutput
            withContext(Dispatchers.IO) {
                client.freePackage(pkgInfos.map { it.toString() }, freed)
            }
            if (freed.isEmpty()) {
                return@launch
            }

            val freedPkgInfos = freed.map { PkgInfo(it) }
            updateState(_state) {
                val newFreePkgInfos = it.originFreedAppInfos.toMutableList()
                val newBannedPkgInfos = it.originBannedAppInfos.toMutableList()
                newBannedPkgInfos.moveTo(newFreePkgInfos) {
                    it.pkgInfo in freedPkgInfos
                }
                it.copy(
                    originFreedAppInfos = newFreePkgInfos,
                    originBannedAppInfos = newBannedPkgInfos
                )
            }
        }
    }

    fun onFreedAppClick(appInfo: AppInfo) {
        updateState(_state) {
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
        updateState(_state) {
            it.copy(selectedInFreed = it.freedAppInfos)
        }
    }

    fun onFreeSelectedBanned() {
        val selectedInBanned = state.value.selectedInBanned.map { it.pkgInfo }
        onFreePkgs(selectedInBanned)
        clearSelected()
    }

    fun onBanSelectedFreed() {
        val selectedInFreed = state.value.selectedInFreed.map { it.pkgInfo }
        onBanPkgs(selectedInFreed)
        clearSelected()
    }

    fun onBannedSelectAll() {
        updateState(_state) {
            it.copy(selectedInBanned = it.bannedAppInfos)
        }
    }

    fun clearSelected() {
        updateState(_state) {
            it.copy(selectedInFreed = emptyList(), selectedInBanned = emptyList())
        }
    }

    fun onBannedAppClick(appInfo: AppInfo) {
        updateState(_state) {
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
        updateState(_state) { it.copy(query = newQuery.trim()) }
    }

    fun onSearchClear() {
        updateState(_state) { it.copy(query = "") }
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