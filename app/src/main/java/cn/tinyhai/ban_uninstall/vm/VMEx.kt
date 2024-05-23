package cn.tinyhai.ban_uninstall.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

inline fun <T, S : MutableStateFlow<T>> ViewModel.updateState(
    state: S,
    crossinline update: (T) -> T
) {
    viewModelScope.launch {
        state.value = update(state.value)
    }
}