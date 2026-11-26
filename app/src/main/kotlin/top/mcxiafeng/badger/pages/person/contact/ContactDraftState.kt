package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.CardCollection

internal const val CONTACT_NAME_LIMIT = 15
internal const val CONTACT_NOTE_LIMIT = 150

/**
 * 名片草稿。编辑器只负责文本三件套（姓名/备注/介绍），
 * 平台、基础信息、标签、头像全部由详情页直接编辑，编辑器保存时不触碰。
 * [collectionIds] 仅承载路由传入的目标名片夹（新建时落入，编辑模式不使用）。
 */
@Immutable
data class ContactDraft(
    val name: String = "",
    val note: String = "",
    val bio: String = "",
    val collectionIds: Set<Long> = emptySet(),
) {
    val hasChanges: Boolean get() = name.isNotEmpty() || note.isNotEmpty() || bio.isNotEmpty() ||
        collectionIds.isNotEmpty()
    val canSave: Boolean get() = name.isNotBlank() &&
        name.codePointCount(0, name.length) <= CONTACT_NAME_LIMIT &&
        note.codePointCount(0, note.length) <= CONTACT_NOTE_LIMIT
}

@Immutable
data class ContactDraftUiState(
    val draft: ContactDraft = ContactDraft(),
    val collections: List<CardCollection> = emptyList(),
    val isLoading: Boolean = true,
    val targetInitialized: Boolean = false,
    val editInitialized: Boolean = false,
    val dirty: Boolean = false,
    val isSaving: Boolean = false,
    val accountChanged: Boolean = false,
    val savedContactId: Long? = null,
    val error: String? = null,
) {
    val editable: Boolean get() = !isSaving && !accountChanged && savedContactId == null
    val canSave: Boolean get() = editable && !isLoading && targetInitialized && draft.canSave &&
        draft.collectionIds.all { id -> collections.any { it.id == id } }
}
