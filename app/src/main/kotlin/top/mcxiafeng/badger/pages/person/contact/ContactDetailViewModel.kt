package top.mcxiafeng.badger.pages.person.contact

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.mcxiafeng.badger.ai.AiTagException
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.AvatarStorage
import top.mcxiafeng.badger.data.PersonFieldDisplay
import top.mcxiafeng.badger.data.PersonWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.utils.PinyinUtils

data class ResolvedPlatformInfo(
    val name: String?,
    val avatarUrl: String?,
)

data class BatchResolvedItem(
    val url: String,
    val fieldKey: String,
    val resolved: ResolvedPlatformInfo?,
    val selected: Boolean = true,
)

sealed class ContactDetailEvent {
    data class ShowToast(val message: String) : ContactDetailEvent()
    data object RefreshData : ContactDetailEvent()
}

/** ViewModel for contact detail; storage and data mutations stay outside the screen. */
class ContactDetailViewModel(
    val repository: ContactRepository,
    val collectionRepository: CollectionRepository,
    val fieldRepository: FieldRepository,
    val tagRepository: TagRepository,
    private val aiTagGenerator: AiTagGenerator,
    private val userProfileTicker: UserProfileTicker,
    private val avatarStorage: AvatarStorage,
    private val networkResolver: ContactNetworkResolver,
) : ViewModel() {

    fun updateBasicInfoField(contactId: Long, fieldKey: String, newValue: String) {
        viewModelScope.launch {
            try {
                fieldRepository.updateFieldValueByKey(contactId, fieldKey, newValue)
                repository.bumpContact(contactId)
                val fresh = repository.getPersonWithFieldsById(contactId)
                if (fresh != null) _contactWithFields.value = fresh
                _events.send(ContactDetailEvent.ShowToast("已更新"))
                _events.send(ContactDetailEvent.RefreshData)
            } catch (e: Exception) {
                failWithToast("更新基础信息(字段=$fieldKey)", e)
            }
        }
    }

    private val _contactWithFields = MutableStateFlow<PersonWithFields?>(null)
    val contactWithFields: StateFlow<PersonWithFields?> = _contactWithFields.asStateFlow()
    private val _platformData = MutableStateFlow<List<ContactPlatform>>(emptyList())
    val platformData: StateFlow<List<ContactPlatform>> = _platformData.asStateFlow()
    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()
    private var tagsCollectJob: Job? = null
    private val _contactCollectionIds = MutableStateFlow<Set<Long>>(emptySet())
    val contactCollectionIds: StateFlow<Set<Long>> = _contactCollectionIds.asStateFlow()
    private var collectionIdsCollectJob: Job? = null
    private val _aiTagCandidates = MutableStateFlow<List<AiTagGenerator.TagCandidate>>(emptyList())
    val aiTagCandidates: StateFlow<List<AiTagGenerator.TagCandidate>> = _aiTagCandidates.asStateFlow()
    private val _aiTagLoading = MutableStateFlow(false)
    val aiTagLoading: StateFlow<Boolean> = _aiTagLoading.asStateFlow()
    private val _aiTagError = MutableStateFlow<String?>(null)
    val aiTagError: StateFlow<String?> = _aiTagError.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _events = Channel<ContactDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun loadContact(contactId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try { loadContactState(contactId) }
            catch (e: Exception) { Log.e(TAG, "加载联系人失败", e) }
            finally { _isLoading.value = false }
        }
        tagsCollectJob?.cancel()
        tagsCollectJob = viewModelScope.launch {
            runCatching { tagRepository.observeTagsByContact(contactId).collect { _tags.value = it } }
                .onFailure { Log.e(TAG, "observeTagsByContact failed contactId=$contactId", it) }
        }
        collectionIdsCollectJob?.cancel()
        collectionIdsCollectJob = viewModelScope.launch {
            runCatching { collectionRepository.getContactCollectionIds(contactId).collect { _contactCollectionIds.value = it.toSet() } }
                .onFailure { Log.e(TAG, "observeContactCollectionIds failed contactId=$contactId", it) }
        }
    }

    fun reloadContact(contactId: Long) {
        viewModelScope.launch {
            runCatching { loadContactState(contactId) }
                .onFailure { Log.e(TAG, "重新加载联系人失败", it) }
        }
    }

    suspend fun reloadContactAwait(contactId: Long) { loadContactState(contactId) }

    private suspend fun loadContactState(contactId: Long) {
        _contactWithFields.value = repository.getPersonWithFieldsById(contactId)
        _platformData.value = repository.getContactPlatforms(contactId)
    }

    fun updateBio(contactId: Long, bio: String?) {
        viewModelScope.launch {
            val oldBio = _contactWithFields.value?.contact?.bio
            try {
                repository.updateContactBio(contactId, bio)
                repository.getPersonWithFieldsById(contactId)?.let { _contactWithFields.value = it }
                userProfileTicker.tick()
                _events.send(ContactDetailEvent.RefreshData)
            } catch (e: Exception) {
                Log.e(TAG, "updateBio failed, rollback to oldBio", e)
                _contactWithFields.update { current -> current?.copy(contact = current.contact.copy(bio = oldBio)) }
                failWithToast("更新个人简介", e)
            }
        }
    }

    fun updateTags(contactId: Long, addedIds: Set<Long>, removedIds: Set<Long>) {
        viewModelScope.launch {
            try {
                addedIds.forEach { tagRepository.addTagToContact(contactId, it) }
                removedIds.forEach { tagRepository.removeTagFromContact(contactId, it) }
                _events.send(ContactDetailEvent.RefreshData)
            } catch (e: Exception) {
                Log.e(TAG, "updateTags failed", e)
                failWithToast("更新标签", e)
            }
        }
    }

    suspend fun createTagAndAssign(contactId: Long, name: String, color: Long): Long = try {
        val newId = tagRepository.upsertTag(name, color, source = "manual")
        tagRepository.addTagToContact(contactId, newId)
        _events.send(ContactDetailEvent.RefreshData)
        newId
    } catch (e: Exception) {
        Log.e(TAG, "createTagAndAssign failed", e)
        failWithToast("创建标签", e)
        -1L
    }

    private var aiTagJob: Job? = null
    fun generateAiTags(contactId: Long) {
        aiTagJob?.cancel()
        aiTagJob = viewModelScope.launch {
            _aiTagLoading.value = true
            _aiTagError.value = null
            _aiTagCandidates.value = emptyList()
            try {
                val bio = _contactWithFields.value?.contact?.bio?.takeIf { it.isNotBlank() } ?: run {
                    _aiTagError.value = "请先填写个人介绍,AI 才能更准确推荐"
                    return@launch
                }
                val existingTags = tagRepository.getAllTagsOnce()
                val candidates = try {
                    withTimeoutOrNull(AI_TAG_TIMEOUT_MS) { aiTagGenerator.suggest(bio, existingTags) } ?: run {
                        Log.w(TAG, "AI 超时 (${AI_TAG_TIMEOUT_MS}ms), 降级本地启发式")
                        _aiTagError.value = "AI 推荐超时,已使用本地匹配"
                        aiTagGenerator.fallbackLocal(bio, existingTags)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiTagException) {
                    Log.w(TAG, "AI 失败,降级本地启发式: ${e.message}")
                    _aiTagError.value = e.message
                    aiTagGenerator.fallbackLocal(bio, existingTags).ifEmpty {
                        _aiTagError.value = "AI 推荐失败,且本地启发式未匹配任何标签"
                        emptyList()
                    }
                }
                ensureActive()
                _aiTagCandidates.value = candidates
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "generateAiTags unexpected failure", e)
                _aiTagError.value = "生成失败: ${e.message}"
            } finally {
                _aiTagLoading.value = false
            }
        }
    }

    fun applyAiTagCandidates(contactId: Long, selected: List<AiTagGenerator.TagCandidate>) {
        viewModelScope.launch {
            try {
                tagRepository.applyAiTagCandidatesAtomic(contactId, selected)
                _aiTagCandidates.value = emptyList()
                _events.send(ContactDetailEvent.RefreshData)
            } catch (e: Exception) {
                Log.e(TAG, "applyAiTagCandidates failed", e)
                failWithToast("采纳 AI 标签", e)
            }
        }
    }

    override fun onCleared() {
        aiTagJob?.cancel()
        tagsCollectJob?.cancel()
        collectionIdsCollectJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val AI_TAG_TIMEOUT_MS = 30_000L
        const val TAG = "ContactDetailViewModel"
    }

    fun clearAiTagCandidates() {
        _aiTagCandidates.value = emptyList()
        _aiTagError.value = null
    }

    suspend fun resolvePlatformForField(platformKey: String, fieldValue: String): ResolvedPlatformInfo? {
        return try {
            val def = FIELD_DEF_MAP[platformKey]
            if (!platformKey.kindCanSync) {
                Log.w(TAG, "平台无可用适配器: $platformKey")
                return null
            }
            val link = buildPlatformLink(platformKey, fieldValue)
            val result = withContext(Dispatchers.IO) {
                networkResolver.identify(link)
            }
            result?.let { ResolvedPlatformInfo(it.name?.takeIf(String::isNotBlank), it.avatarUrl?.takeIf(String::isNotBlank)) }
        } catch (e: Exception) {
            Log.e(TAG, "字段同步解析失败", e)
            null
        }
    }

    suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> = repository.getContactPlatforms(contactId)

    suspend fun batchResolvePlatforms(urls: List<String>): List<BatchResolvedItem> = withContext(Dispatchers.IO) {
        val responses = networkResolver.identifyBatch(urls)
        urls.zip(responses) { url, resp ->
            BatchResolvedItem(
                url = url,
                fieldKey = resp?.kind ?: "unknown",
                resolved = resp?.let { ResolvedPlatformInfo(it.name?.takeIf(String::isNotBlank), it.avatarUrl?.takeIf(String::isNotBlank)) },
            )
        }
    }

    suspend fun getContactById(contactId: Long): Contact? = repository.getContactById(contactId)
    suspend fun getFieldValuesByContactOnce(contactId: Long) = fieldRepository.getFieldValuesByContactOnce(contactId)

    fun updateName(contactId: Long, newName: String) {
        viewModelScope.launch {
            if (updateNameAwait(contactId, newName)) _events.send(ContactDetailEvent.RefreshData)
        }
    }

    suspend fun updateNameAwait(contactId: Long, newName: String): Boolean = try {
        val freshContact = repository.getContactById(contactId) ?: return false
        val updated = freshContact.copy(name = newName, updateTime = System.currentTimeMillis())
        repository.updateContact(updated)
        _contactWithFields.update { it?.copy(contact = updated) }
        true
    } catch (e: Exception) {
        Log.e(TAG, "更新姓名失败", e)
        failWithToast("更新姓名", e)
        false
    }

    fun applyAvatarUpdate(contactId: Long, avatarPath: String) {
        viewModelScope.launch {
            try {
                val currentContact = repository.getPersonWithFieldsById(contactId)?.contact ?: return@launch
                val updated = currentContact.copy(avatarPath = avatarPath, updateTime = System.currentTimeMillis())
                repository.updateContact(updated)
                _contactWithFields.update { it?.copy(contact = updated) }
                _events.send(ContactDetailEvent.ShowToast("头像已更新"))
                _events.send(ContactDetailEvent.RefreshData)
            } catch (e: Exception) {
                Log.e(TAG, "设置头像失败", e)
                failWithToast("设置头像", e)
            }
        }
    }

    fun saveAndApplyAvatar(contactId: Long, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val file = avatarStorage.saveContactAvatar(contactId, bitmap)
                applyAvatarUpdateAwait(contactId, file.absolutePath)
                _events.send(ContactDetailEvent.ShowToast("头像已更新"))
                _events.send(ContactDetailEvent.RefreshData)
            } catch (e: Exception) {
                Log.e(TAG, "设置头像失败", e)
                failWithToast("设置头像", e)
            }
        }
    }

    private suspend fun applyAvatarUpdateAwait(contactId: Long, avatarPath: String) {
        val currentContact = repository.getPersonWithFieldsById(contactId)?.contact ?: return
        val updated = currentContact.copy(avatarPath = avatarPath, updateTime = System.currentTimeMillis())
        repository.updateContact(updated)
        _contactWithFields.update { it?.copy(contact = updated) }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch { updateContactAwait(contact, emitRefresh = true) }
    }

    suspend fun updateContactAwait(contact: Contact, emitRefresh: Boolean = false): Boolean = try {
        val expected = PinyinUtils.getContactPinyinInitial(contact.name)
        val normalized = if (contact.pinyinInitial == expected) contact else contact.copy(pinyinInitial = expected)
        repository.updateContact(normalized)
        _contactWithFields.update { it?.copy(contact = normalized) }
        if (emitRefresh) _events.send(ContactDetailEvent.RefreshData)
        true
    } catch (e: Exception) {
        Log.e(TAG, "更新联系人失败", e)
        if (emitRefresh) failWithToast("更新联系人", e)
        false
    }

    fun applySyncResult(contactId: Long, newName: String?, avatarPath: String?) {
        viewModelScope.launch {
            try {
                val freshContact = repository.getContactById(contactId) ?: return@launch
                var updated = freshContact
                if (!newName.isNullOrBlank()) updated = updated.copy(name = newName)
                if (!avatarPath.isNullOrBlank()) updated = updated.copy(avatarPath = avatarPath)
                if (updated != freshContact) {
                    updated = updated.copy(updateTime = System.currentTimeMillis())
                    repository.updateContact(updated)
                    _contactWithFields.update { it?.copy(contact = updated) }
                    _events.send(ContactDetailEvent.ShowToast("同步成功"))
                    _events.send(ContactDetailEvent.RefreshData)
                } else _events.send(ContactDetailEvent.ShowToast("未获取到可同步的信息"))
            } catch (e: Exception) {
                Log.e(TAG, "应用同步结果失败", e)
                _events.send(ContactDetailEvent.ShowToast("同步失败"))
            }
        }
    }

    fun deleteFieldValue(contactId: Long, valueId: Long) { viewModelScope.launch { deleteFieldValueAwait(contactId, valueId) } }

    suspend fun deleteFieldValueAwait(contactId: Long, valueId: Long): Boolean = try {
        val target = fieldRepository.getFieldValuesByContactOnce(contactId).find { it.id == valueId }
        if (target != null) { fieldRepository.deleteFieldValue(target); true } else false
    } catch (e: Exception) {
        Log.e(TAG, "删除字段值失败", e)
        failWithToast("删除字段", e)
        false
    }

    fun updateFieldValue(contactId: Long, valueId: Long, newValue: String) { viewModelScope.launch { updateFieldValueAwait(contactId, valueId, newValue) } }

    suspend fun updateFieldValueAwait(contactId: Long, valueId: Long, newValue: String): Boolean = try {
        val target = fieldRepository.getFieldValuesByContactOnce(contactId).find { it.id == valueId } ?: return false
        fieldRepository.updateFieldValue(target.copy(value = newValue, updateTime = System.currentTimeMillis()))
        true
    } catch (e: Exception) {
        Log.e(TAG, "更新字段值失败", e)
        failWithToast("更新字段", e)
        false
    }

    fun removePlatform(contactId: Long, fieldKey: String) { viewModelScope.launch { removePlatformAwait(contactId, fieldKey) } }

    suspend fun removePlatformAwait(contactId: Long, fieldKey: String): Boolean = try {
        repository.removeContactPlatform(contactId, fieldKey)
        true
    } catch (e: Exception) {
        Log.e(TAG, "移除平台失败", e)
        failWithToast("移除平台", e)
        false
    }

    fun addOrUpdatePlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        viewModelScope.launch { if (addOrUpdatePlatformAwait(contactId, fieldKey, entry)) _events.send(ContactDetailEvent.RefreshData) }
    }

    suspend fun addOrUpdatePlatformAwait(contactId: Long, fieldKey: String, entry: PlatformEntry): Boolean = try {
        repository.updateContactPlatform(contactId, fieldKey, entry)
        true
    } catch (e: Exception) {
        Log.e(TAG, "更新平台失败", e)
        failWithToast("更新平台", e)
        false
    }

    fun updateCollections(contactId: Long, addedIds: List<Long>, removedIds: List<Long>) { viewModelScope.launch { updateCollectionsAwait(contactId, addedIds, removedIds) } }

    suspend fun updateCollectionsAwait(contactId: Long, addedIds: List<Long>, removedIds: List<Long>): Boolean = try {
        for (collectionId in addedIds) collectionRepository.addContactToCollection(contactId, collectionId, sourceType = "manual")
        for (collectionId in removedIds) collectionRepository.removeContactFromCollection(contactId, collectionId)
        true
    } catch (e: Exception) {
        Log.e(TAG, "更新名片夹失败", e)
        failWithToast("更新名片夹", e)
        false
    }

    fun attachToExisting(
        sourceContact: Contact,
        sourceFields: List<PersonFieldDisplay>,
        existingContact: Contact,
        selectedFieldKeys: List<String>,
        selectedCustomFieldIds: List<Long>,
    ) {
        viewModelScope.launch {
            try {
                attachCurrentContactToExisting(repository, fieldRepository, sourceContact, sourceFields, existingContact, selectedFieldKeys, selectedCustomFieldIds)
            } catch (e: Exception) { Log.e(TAG, "附加字段失败", e) }
        }
    }

    fun emitToast(message: String) { viewModelScope.launch { _events.send(ContactDetailEvent.ShowToast(message)) } }
    fun emitRefresh() { viewModelScope.launch { _events.send(ContactDetailEvent.RefreshData) } }

    private fun failWithToast(operation: String, e: Throwable, fallback: String = "未知错误") {
        Log.e(TAG, "$operation failed", e)
        emitToast("$operation 失败:${e.message ?: fallback}")
    }
}