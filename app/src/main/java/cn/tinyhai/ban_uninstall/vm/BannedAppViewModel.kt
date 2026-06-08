package cn.tinyhai.ban_uninstall.vm

import android.graphics.drawable.Drawable
import android.util.Log
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
    val label: String, val icon: Drawable, val pkgInfo: PkgInfo, val banned: Boolean,
) {
    val isDual get() = pkgInfo.userId > 1
    val key get() = pkgInfo.toString()
}

data class BannedAppState(
    val isRefreshing: Boolean = false,
    val appInfos: List<AppInfo> = emptyList(),
) {
    val hasLoaded: Boolean get() = this !== Empty

    companion object {
        val Empty = BannedAppState()

        val comparator: Comparator<AppInfo> by lazy {
            compareBy<AppInfo> {
                if (it.banned) -1 else 1
            } then compareBy {
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

    init {
        viewModelScope.launch {
            loadAppList()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            updateState(_state) {
                it.copy(isRefreshing = true)
            }
            loadAppList()
            updateState(_state) {
                it.copy(
                    isRefreshing = false,
                )
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

            val bannedPkgInfos = banned.map { PkgInfo(it) }.toHashSet()
            withContext(Dispatchers.IO) {
                val appInfos = state.value.appInfos.toMutableList().apply {
                    forEachIndexed { index, info ->
                        if (bannedPkgInfos.contains(info.pkgInfo)) {
                            this[index] = info.copy(banned = true)
                        }
                    }
                }.sortedWith(BannedAppState.comparator)
                updateState(_state) {
                    it.copy(
                        appInfos = appInfos
                    )
                }
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

            withContext(Dispatchers.IO) {
                val freedPkgInfos = freed.map { PkgInfo(it) }.toHashSet()
                val appInfos = state.value.appInfos.toMutableList().apply {
                    forEachIndexed { index, info ->
                        if (freedPkgInfos.contains(info.pkgInfo)) {
                            this[index] = info.copy(banned = false)
                        }
                    }
                }.sortedWith(BannedAppState.comparator)
                updateState(_state) {
                    it.copy(
                        appInfos = appInfos
                    )
                }
            }
        }
    }

    private suspend fun loadAppList() {
        val pm = App.app.packageManager
        withContext(Dispatchers.IO) {
            val allPackages = client.packages.list
            var appInfos = allPackages.map {
                val label = it.applicationInfo!!.loadLabel(pm).toString()
                val icon = it.applicationInfo!!.loadIcon(pm)
                val packageName = it.packageName ?: it.applicationInfo!!.packageName
                val uid = it.applicationInfo!!.uid
                AppInfo(label, icon, PkgInfo(packageName, uid / 100_000), false)
            }
            val bannedPkgInfos = client.allBannedPackages.map { PkgInfo(it) }.toHashSet()
            appInfos = appInfos.toMutableList().apply {
                forEachIndexed { index, info ->
                    if (bannedPkgInfos.contains(info.pkgInfo)) {
                        this[index] = info.copy(banned = true)
                    }
                }
            }.sortedWith(BannedAppState.comparator)
            updateState(_state) {
                it.copy(
                    isRefreshing = false,
                    appInfos = appInfos
                )
            }
        }
    }
}