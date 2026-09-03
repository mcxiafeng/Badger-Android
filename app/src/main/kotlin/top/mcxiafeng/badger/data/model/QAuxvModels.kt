package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable

sealed class QAuxvConflictAction {
    data object Skip : QAuxvConflictAction()
    data object Replace : QAuxvConflictAction()
    data object InsertAnyway : QAuxvConflictAction()
}

@Immutable
data class QAuxvImportSummary(
    val inserted: Int = 0,
    val replaced: Int = 0,
    val skipped: Int = 0,
)

@Immutable
data class QAuxvImportProgress(
    val phase: Phase,
    val current: Int,
    val total: Int,
) {
    enum class Phase { AvatarDownloading, Writing }

    fun displayLabel(): String = when (phase) {
        Phase.AvatarDownloading -> "下载头像"
        Phase.Writing -> "写入联系人"
    }
}
