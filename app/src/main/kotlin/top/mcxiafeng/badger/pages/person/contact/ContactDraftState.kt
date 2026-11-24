package top.mcxiafeng.badger.pages.person.contact

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.util.UUID
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP

internal const val CONTACT_NAME_LIMIT = 15
internal const val CONTACT_NOTE_LIMIT = 150
internal const val CONTACT_PLATFORM_NAME_LIMIT = 30
internal val DRAFT_BASIC_KEYS = setOf("gender", "birthday", "country", "region")

@Immutable
data class ContactDraftField(
    val id: String = UUID.randomUUID().toString(),
    val key: String? = null,
    val customFieldId: Long? = null,
    val name: String,
    val value: String,
) {
    val display: ContactFieldDisplay get() = ContactFieldDisplay(
        valueId = 0, fieldId = null, customFieldId = customFieldId, fieldName = name,
        fieldKey = key, icon = null, fieldType = "text", value = value, sortOrder = 0,
    )
}

@Immutable
data class ContactDraft(
    val name: String = "",
    val note: String = "",
    val fields: List<ContactDraftField> = emptyList(),
    val platforms: Map<String, PlatformEntry> = emptyMap(),
    val tags: Set<String> = emptySet(),
    val collectionIds: Set<Long> = emptySet(),
) {
    val hasChanges: Boolean get() = name.isNotEmpty() || note.isNotEmpty() ||
        fields.isNotEmpty() || platforms.isNotEmpty() || tags.isNotEmpty() || collectionIds.isNotEmpty()
    val canSave: Boolean get() = name.isNotBlank() &&
        name.codePointCount(0, name.length) <= CONTACT_NAME_LIMIT &&
        note.codePointCount(0, note.length) <= CONTACT_NOTE_LIMIT
    val basicFields: List<ContactFieldDisplay> get() = fields.filter { it.key in DRAFT_BASIC_KEYS }.map { it.display }
    val contactFields: List<ContactDraftField> get() = fields.filter { it.key !in DRAFT_BASIC_KEYS }
    fun info(key: String): String = fields.firstOrNull { it.key == key }?.value.orEmpty()
}

@Immutable
data class ContactDraftUiState(
    val draft: ContactDraft = ContactDraft(),
    val availableTags: List<Tag> = emptyList(),
    val collections: List<CardCollection> = emptyList(),
    val customFields: List<CustomField> = emptyList(),
    val avatar: Bitmap? = null,
    val isLoading: Boolean = true,
    val targetInitialized: Boolean = false,
    val isSaving: Boolean = false,
    val accountChanged: Boolean = false,
    val savedContactId: Long? = null,
    val error: String? = null,
) {
    val editable: Boolean get() = !isSaving && !accountChanged && savedContactId == null
    val canSave: Boolean get() = editable && !isLoading && targetInitialized && draft.canSave &&
        draft.collectionIds.all { id -> collections.any { it.id == id } }
    val collectionSummary: String get() = if (draft.collectionIds.isEmpty()) "未加入名片夹" else
        draft.collectionIds.joinToString("、") { id -> collections.firstOrNull { it.id == id }?.name ?: "已失效的名片夹" }
}

internal fun draftFieldName(key: String): String = FIELD_DEF_MAP[key]?.displayName ?: key
