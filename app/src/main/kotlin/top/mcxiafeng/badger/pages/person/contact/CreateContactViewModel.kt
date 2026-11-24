package top.mcxiafeng.badger.pages.person.contact

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountScopedContext
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.domain.ParsePlatformInputUseCase
import top.mcxiafeng.badger.network.PlatformIdExtractor
import top.mcxiafeng.badger.utils.Methods

@HiltViewModel
class CreateContactViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val collectionRepository: CollectionRepository,
    private val fieldRepository: FieldRepository,
    private val tagRepository: TagRepository,
    private val database: AppDatabase,
    @AccountScopedContext private val context: Context,
    private val parser: ParsePlatformInputUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val accountScope: AccountScope,
    private val accountStore: AccountStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(restoreState())
    val uiState = _uiState.asStateFlow()
    private val submitting = AtomicBoolean(false)

    init {
        observeChoices()
        viewModelScope.launch {
            accountStore.scopeFlow.collect { scope ->
                if (scope.key != accountScope.key) invalidateAccount()
            }
        }
    }

    private fun observeChoices() = viewModelScope.launch {
        try {
            combine(tagRepository.observeAllTags(), collectionRepository.getAllCollections(),
                fieldRepository.getAllEnabledCustomFields()) { tags, collections, custom ->
                Triple(tags, collections, custom)
            }.collect { (tags, collections, custom) ->
                _uiState.update { it.copy(availableTags = tags, collections = collections,
                    customFields = custom, isLoading = false) }
                Log.d(TAG, "草稿选项已更新")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "加载草稿选项失败", error)
            _uiState.update { it.copy(error = "可选资料加载失败，请返回后重试；填写内容已保留") }
        }
    }

    /** The route owns the initial folder; restored user selections must not be overwritten. */
    fun initializeTarget(collectionId: Long?) {
        if (!checkAccount()) return
        if (_uiState.value.targetInitialized) return
        if (!savedStateHandle.contains(TARGET_KEY)) {
            edit { copy(collectionIds = collectionIds + listOfNotNull(collectionId)) }
            savedStateHandle[TARGET_KEY] = true
        }
        _uiState.update { it.copy(targetInitialized = true) }
        Log.d(TAG, "已接收导航名片夹: assigned=${collectionId != null}")
    }

    fun updateName(value: String) = edit { copy(name = limit(value, CONTACT_NAME_LIMIT)) }
    fun updateNote(value: String) = edit { copy(note = limit(value, CONTACT_NOTE_LIMIT)) }
    fun setTags(names: Set<String>) = edit { copy(tags = names.map(String::trim).filter(String::isNotBlank).toSet()) }
    fun setCollections(ids: Set<Long>) = edit { copy(collectionIds = ids.toSet()) }
    fun removePlatform(key: String) = edit { copy(platforms = platforms - key) }
    fun removeField(id: String) = edit { copy(fields = fields.filterNot { it.id == id }) }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun updateInfo(key: String, value: String) {
        if (key !in DRAFT_BASIC_KEYS) return
        edit {
            val old = fields.firstOrNull { it.key == key }
            val next = if (value.isBlank()) emptyList() else listOf(ContactDraftField(
                id = old?.id ?: UUID.randomUUID().toString(), key = key, name = draftFieldName(key), value = value.trim()))
            copy(fields = fields.filterNot { it.key == key } + next)
        }
    }

    fun stageField(key: String?, customId: Long?, name: String, value: String, editingId: String? = null): Boolean {
        if (!checkAccount() || !_uiState.value.editable || value.isBlank() || name.isBlank()) return false
        val field = ContactDraftField(id = editingId ?: UUID.randomUUID().toString(), key = key,
            customFieldId = customId, name = name.trim(), value = value.trim())
        edit { copy(fields = fields.filterNot { it.id == field.id } + field) }
        return true
    }

    /** Local validation only: adding or editing a platform never writes a contact or calls a URL. */
    fun stagePlatform(key: String, account: String, displayName: String, auxiliary: String = "", editingKey: String? = null): Boolean {
        if (!checkAccount() || !_uiState.value.editable) return false
        val normalized = PlatformIdExtractor.normalizeToKey(key.trim())
        val invalid = normalized.isBlank() || normalized.length > CONTACT_PLATFORM_NAME_LIMIT ||
            normalized in DRAFT_BASIC_KEYS || normalized in setOf("phone", "email") ||
            (normalized != editingKey && normalized in _uiState.value.draft.platforms)
        if (invalid) return reportError("请检查平台名称；已有平台请编辑原条目")
        val result = parser.manual(normalized, account.ifBlank { auxiliary }, displayName)
        val entry = result.entry ?: return reportError(result.error ?: "请检查平台账号")
        edit { copy(platforms = platforms + (normalized to entry.copy(
            value = auxiliary.trim().ifBlank { entry.value }, avatarUrl = platforms[editingKey]?.avatarUrl))) }
        Log.d(TAG, "平台已暂存: count=${_uiState.value.draft.platforms.size}")
        return true
    }

    fun setAvatar(bitmap: Bitmap) {
        if (!checkAccount() || !_uiState.value.editable) return
        _uiState.update { it.copy(avatar = bitmap, error = null) }
        Log.d(TAG, "头像仅留在草稿内存中")
    }

    fun removeAvatar() {
        if (!checkAccount() || !_uiState.value.editable) return
        _uiState.update { it.copy(avatar = null) }
        Log.d(TAG, "已移除草稿头像")
    }

    private fun edit(change: ContactDraft.() -> ContactDraft) {
        if (!checkAccount() || !_uiState.value.editable) return
        _uiState.update { it.copy(draft = it.draft.change(), error = null) }
        savedStateHandle[DRAFT_KEY] = ContactDraftCodec.encode(_uiState.value.draft)
        savedStateHandle[OWNER_KEY] = accountScope.key
        Log.d(TAG, "草稿已更新，尚未写入联系人")
    }

    fun save() {
        if (!checkAccount() || !_uiState.value.canSave || !submitting.compareAndSet(false, true)) return
        val snapshot = _uiState.value
        _uiState.update { it.copy(isSaving = true, error = null) }
        Log.d(TAG, "开始完整保存草稿")
        viewModelScope.launch {
            try {
                val id = persist(snapshot.draft, snapshot.avatar)
                savedStateHandle[SAVED_ID_KEY] = id
                savedStateHandle.remove<Bundle>(DRAFT_KEY)
                _uiState.update { it.copy(savedContactId = id) }
                checkAccount()
                Log.d(TAG, "草稿事务保存成功")
            } catch (error: CancellationException) {
                Log.d(TAG, "草稿保存已取消")
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "保存失败，保留草稿供重试", error)
                _uiState.update { it.copy(error = "保存失败，填写内容已保留，请检查名片夹后重试") }
            } finally {
                submitting.set(false)
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Called again immediately before navigation so a committed old-account ID cannot open in another account. */
    fun canNavigate(): Boolean = checkAccount() && _uiState.value.savedContactId != null

    internal suspend fun persist(draft: ContactDraft, avatar: Bitmap?): Long = withContext(Dispatchers.IO) {
        require(draft.canSave)
        requireActiveAccount()
        var avatarFile: File? = null
        try {
            avatarFile = avatar?.let { Methods.saveBitmapAsAvatar(context, it, "contact_${UUID.randomUUID()}.webp") }
            database.withTransaction {
                requireActiveAccount()
                val id = contactRepository.insertContact(Contact(name = draft.name.trim(),
                    note = draft.note.trim().ifBlank { null }, avatarPath = avatarFile?.absolutePath))
                saveFields(id, draft)
                savePlatforms(id, draft)
                val tags = draft.tags.map { tagRepository.upsertTag(it) }
                if (tags.isNotEmpty()) tagRepository.addTagsToContact(id, tags)
                draft.collectionIds.forEach { collectionId ->
                    checkNotNull(collectionRepository.getCollectionById(collectionId)) { "Draft collection no longer exists" }
                    collectionRepository.addContactToCollection(id, collectionId, "manual")
                }
                requireActiveAccount()
                id
            }
        } catch (error: Exception) {
            avatarFile?.let { if (!it.delete() && it.exists()) Log.e(TAG, "清理未保存头像失败") }
            Log.e(TAG, "草稿持久化未完成", error)
            throw error
        }
    }

    private suspend fun saveFields(id: Long, draft: ContactDraft) {
        val newCustomIds = mutableMapOf<String, Long>()
        draft.fields.forEach { field ->
            val value = if (field.key != null) {
                val definition = checkNotNull(fieldRepository.getFieldByKey(field.key)) { "Draft field no longer exists" }
                ContactFieldValue(contactId = id, fieldId = definition.id, value = field.value)
            } else {
                val customId = field.customFieldId ?: newCustomIds.getOrPut(field.name) {
                    fieldRepository.insertCustomField(CustomField(fieldName = field.name, fieldType = "text", options = ""))
                }
                checkNotNull(fieldRepository.getCustomFieldById(customId)) { "Draft custom field no longer exists" }
                ContactFieldValue(contactId = id, customFieldId = customId, value = field.value)
            }
            fieldRepository.insertFieldValue(value)
        }
    }

    private suspend fun savePlatforms(id: Long, draft: ContactDraft) {
        // New uncommitted row: do not acquire repository write mutexes inside Room's transaction.
        database.contactPlatformDao().insertPlatforms(draft.platforms.map { (key, entry) ->
            ContactPlatform(contactId = id, platformKey = key, value = entry.value,
                displayName = entry.displayName, jumpLink = entry.jumpLink,
                originalLink = entry.originalLink, avatarUrl = entry.avatarUrl)
        })
    }

    private fun checkAccount(): Boolean {
        if (accountStore.current().key != accountScope.key) invalidateAccount()
        return !_uiState.value.accountChanged
    }

    private fun requireActiveAccount() {
        check(accountStore.current().key == accountScope.key && !_uiState.value.accountChanged) { "Draft account changed" }
    }

    private fun invalidateAccount() {
        if (_uiState.value.accountChanged) return
        _uiState.update { it.copy(accountChanged = true, error = "账号已切换，请返回后在当前账号重新新建名片") }
        Log.d(TAG, "账号切换，已锁定旧账号草稿")
    }

    private fun reportError(message: String): Boolean {
        _uiState.update { it.copy(error = message) }
        Log.d(TAG, "草稿校验未通过")
        return false
    }

    private fun restoreState(): ContactDraftUiState {
        val owner = savedStateHandle.get<String>(OWNER_KEY)
        if (owner != null && owner != accountScope.key) {
            savedStateHandle.remove<Bundle>(DRAFT_KEY)
            savedStateHandle.remove<Boolean>(TARGET_KEY)
            savedStateHandle.remove<Long>(SAVED_ID_KEY)
            Log.d(TAG, "忽略其他账号的恢复草稿")
        }
        savedStateHandle[OWNER_KEY] = accountScope.key
        return try {
            ContactDraftUiState(draft = ContactDraftCodec.decode(savedStateHandle[DRAFT_KEY]),
                savedContactId = savedStateHandle[SAVED_ID_KEY])
        } catch (error: Exception) {
            Log.e(TAG, "恢复草稿失败", error)
            ContactDraftUiState(error = "未能恢复上次草稿，请重新填写")
        }
    }

    private fun limit(value: String, count: Int): String =
        if (value.codePointCount(0, value.length) <= count) value else value.substring(0, value.offsetByCodePoints(0, count))

    private companion object {
        const val TAG = "CreateContactViewModelTester"
        const val DRAFT_KEY = "contact_draft"
        const val OWNER_KEY = "contact_draft_owner"
        const val TARGET_KEY = "contact_draft_target_initialized"
        const val SAVED_ID_KEY = "contact_draft_saved_id"
    }
}
