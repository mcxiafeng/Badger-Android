package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.shared.util.nowMs

class UserProfileTicker {

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    
    fun tick() {
        _tick.value = nowMs()
    }
}
