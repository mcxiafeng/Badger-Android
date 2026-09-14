package top.mcxiafeng.badger.pages.settings.history

import top.mcxiafeng.badger.data.repository.HistoryFilter

sealed interface OperationHistoryEvent {
    
    data class ChangeFilter(val filter: HistoryFilter) : OperationHistoryEvent
}
