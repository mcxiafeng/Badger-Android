package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.ocr.PlatformFieldDef

internal enum class AddPlatformStep { AUTOMATIC, MANUAL, FORM }

@Immutable
internal data class PlatformCandidate(
    val lineNumber: Int,
    val fieldKey: String?,
    val entry: PlatformEntry?,
    val message: String? = null
)

@Immutable
internal data class PlatformListItem(
    val definition: PlatformFieldDef,
    val initial: String,
    val isAdded: Boolean
)

@Immutable
internal data class AddPlatformUiState(
    val step: AddPlatformStep = AddPlatformStep.AUTOMATIC,
    val input: String = "",
    val candidates: List<PlatformCandidate> = emptyList(),
    val query: String = "",
    val platforms: List<PlatformListItem> = emptyList(),
    val selectedKey: String = "",
    val customName: String = "",
    val account: String = "",
    val auxiliaryAccount: String = "",
    val displayName: String = "",
    val pending: Map<String, PlatformEntry> = emptyMap(),
    val error: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
) {
    val additions: Map<String, PlatformEntry>
        get() = pending + candidates.filter { it.entry != null && it.message == null }
            .associate { requireNotNull(it.fieldKey) to requireNotNull(it.entry) }

    val hasDraft: Boolean
        get() = input.isNotBlank() || pending.isNotEmpty() || account.isNotBlank() ||
            auxiliaryAccount.isNotBlank() || displayName.isNotBlank() || customName.isNotBlank()
}
