package top.mcxiafeng.badger.pages.person.contact

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository

/**
 * 名片编辑器 ViewModel。只负责文本三件套：姓名 / 备注 / 个人介绍。
 * 头像、基础信息、标签、平台、名片夹关联全部由详情页直接编辑，本编辑器保存时不触碰。
 * 新建模式下 [collectionIds] 仅承载路由传入的目标名片夹。
 */
@HiltViewModel
class CreateContactViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val collectionRepository: CollectionRepository,
    private val database: AppDatabase,
    private val savedStateHandle: SavedStateHandle,
    private val accountScope: AccountScope,
    private val accountStore: AccountStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(restoreState())
    val uiState = _uiState.asStateFlow()
    private val submitting = AtomicBoolean(false)

    /** 编辑模式的目标联系人 id；null 表示新建。取自路由参数（SavedStateHandle）。 */
    private var editingContactId: Long? = savedStateHandle[EDITING_ID_KEY]

    init {
        observeCollections()
        viewModelScope.launch {
            accountStore.scopeFlow.collect { scope ->
                if (scope.key != accountScope.key) invalidateAccount()
            }
        }
    }

    /** 观察名片夹列表：canSave 需要校验目标名片夹仍然有效。 */
    private fun observeCollections() = viewModelScope.launch {
        try {
            collectionRepository.getAllCollections().collect { collections ->
                _uiState.update { it.copy(collections = collections, isLoading = false) }
                Log.d(TAG, "草稿选项已更新: collections=${collections.size}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "加载名片夹列表失败", error)
            _uiState.update { it.copy(error = "名片夹加载失败，请返回后重试；填写内容已保留") }
        }
    }

    /** The route owns the initial folder; restored user selections must not be overwritten. */
    fun initializeTarget(collectionId: Long?) {
        if (!checkAccount()) return
        if (_uiState.value.targetInitialized) return
        if (!savedStateHandle.contains(TARGET_KEY)) {
            if (collectionId != null) {
                edit { copy(collectionIds = collectionIds + listOfNotNull(collectionId)) }
            }
            savedStateHandle[TARGET_KEY] = true
        }
        _uiState.update { it.copy(targetInitialized = true) }
        Log.d(TAG, "已接收导航名片夹: assigned=${collectionId != null}")
    }

    /** 编辑模式：把已有联系人的文本资料载入草稿。进程重建后草稿已恢复则跳过。 */
    fun initializeEditing(contactId: Long?) {
        if (!checkAccount()) return
        if (contactId == null || _uiState.value.editInitialized) return
        editingContactId = contactId
        savedStateHandle[EDITING_ID_KEY] = contactId
        if (savedStateHandle.contains(EDIT_INIT_KEY)) {
            // 进程重建后草稿已在 restoreState 中恢复，无需重新从库加载（避免覆盖未保存改动）。
            _uiState.update { it.copy(editInitialized = true, targetInitialized = true) }
            Log.d(TAG, "编辑模式已恢复草稿: contactId=$contactId")
            return
        }
        savedStateHandle[EDIT_INIT_KEY] = true
        viewModelScope.launch { loadContactIntoDraft(contactId) }
    }

    private suspend fun loadContactIntoDraft(contactId: Long) {
        try {
            val contact = contactRepository.getContactById(contactId)
            if (contact == null) {
                _uiState.update { it.copy(error = "联系人不存在或已删除", isLoading = false, editInitialized = true) }
                Log.w(TAG, "编辑目标联系人不存在: contactId=$contactId")
                return
            }
            val draft = ContactDraft(name = contact.name, note = contact.note.orEmpty(), bio = contact.bio.orEmpty())
            _uiState.update { it.copy(draft = draft, isLoading = false,
                editInitialized = true, targetInitialized = true, dirty = false) }
            savedStateHandle[DRAFT_KEY] = ContactDraftCodec.encode(draft)
            Log.d(TAG, "编辑草稿已加载: contactId=$contactId")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "加载编辑草稿失败", error)
            _uiState.update { it.copy(error = "加载联系人资料失败，请返回重试", isLoading = false, editInitialized = true) }
        }
    }

    fun updateName(value: String) = edit { copy(name = limit(value, CONTACT_NAME_LIMIT)) }
    fun updateNote(value: String) = edit { copy(note = limit(value, CONTACT_NOTE_LIMIT)) }
    fun updateBio(value: String) = edit { copy(bio = value) }

    private fun edit(change: ContactDraft.() -> ContactDraft) {
        if (!checkAccount() || !_uiState.value.editable) return
        _uiState.update { it.copy(draft = it.draft.change(), error = null, dirty = true) }
        savedStateHandle[DRAFT_KEY] = ContactDraftCodec.encode(_uiState.value.draft)
        savedStateHandle[OWNER_KEY] = accountScope.key
        Log.d(TAG, "草稿已更新，尚未写入联系人")
    }

    fun save() {
        if (!checkAccount() || !_uiState.value.canSave || !submitting.compareAndSet(false, true)) return
        val snapshot = _uiState.value
        _uiState.update { it.copy(isSaving = true, error = null) }
        Log.d(TAG, "开始保存草稿，编辑模式=${editingContactId != null}")
        viewModelScope.launch {
            try {
                val id = if (editingContactId != null) persistUpdate(editingContactId!!, snapshot.draft)
                    else persist(snapshot.draft)
                savedStateHandle[SAVED_ID_KEY] = id
                savedStateHandle.remove<Bundle>(DRAFT_KEY)
                _uiState.update { it.copy(savedContactId = id) }
                checkAccount()
                Log.d(TAG, "草稿保存成功: id=$id")
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

    /** 新建：插入联系人并落入目标名片夹。头像/字段/平台/标签一律不在编辑器内维护。 */
    internal suspend fun persist(draft: ContactDraft): Long = withContext(Dispatchers.IO) {
        require(draft.canSave)
        requireActiveAccount()
        try {
            database.withTransaction {
                requireActiveAccount()
                val id = contactRepository.insertContact(Contact(name = draft.name.trim(),
                    note = draft.note.trim().ifBlank { null }, bio = draft.bio.trim().ifBlank { null }))
                draft.collectionIds.forEach { collectionId ->
                    checkNotNull(collectionRepository.getCollectionById(collectionId)) { "Draft collection no longer exists" }
                    collectionRepository.addContactToCollection(id, collectionId, "manual")
                }
                requireActiveAccount()
                id
            }
        } catch (error: Exception) {
            Log.e(TAG, "草稿持久化未完成", error)
            throw error
        }
    }

    /** 编辑：只更新联系人的姓名/备注/介绍三列，其余（头像/字段/平台/标签/名片夹）一律不触碰。 */
    internal suspend fun persistUpdate(id: Long, draft: ContactDraft): Long = withContext(Dispatchers.IO) {
        require(draft.canSave)
        requireActiveAccount()
        val existing = contactRepository.getContactById(id) ?: error("Contact no longer exists")
        contactRepository.updateContact(existing.copy(
            name = draft.name.trim(),
            note = draft.note.trim().ifBlank { null },
            bio = draft.bio.trim().ifBlank { null },
            updateTime = System.currentTimeMillis(),
        ))
        Log.d(TAG, "联系人文本已更新: id=$id")
        id
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
        _uiState.update { it.copy(accountChanged = true, error = "账号已切换，请返回后在当前账号重新编辑名片") }
        Log.d(TAG, "账号切换，已锁定旧账号草稿")
    }

    private fun restoreState(): ContactDraftUiState {
        val owner = savedStateHandle.get<String>(OWNER_KEY)
        if (owner != null && owner != accountScope.key) {
            savedStateHandle.remove<Bundle>(DRAFT_KEY)
            savedStateHandle.remove<Boolean>(TARGET_KEY)
            savedStateHandle.remove<Long>(SAVED_ID_KEY)
            savedStateHandle.remove<Long>(EDITING_ID_KEY)
            savedStateHandle.remove<Boolean>(EDIT_INIT_KEY)
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
        const val EDITING_ID_KEY = "contact_draft_editing_id"
        const val EDIT_INIT_KEY = "contact_draft_edit_initialized"
    }
}
