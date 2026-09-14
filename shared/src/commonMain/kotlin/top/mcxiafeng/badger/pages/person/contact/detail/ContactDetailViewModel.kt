package top.mcxiafeng.badger.pages.person.contact.detail

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
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.model.PersonWithFields
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.pages.person.contact.dialogs.attachCurrentContactToExisting
import top.mcxiafeng.badger.shared.util.PinyinUtils
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

data class ResolvedPlatformInfo(
    val name: String?,
    val avatarUrl: String?
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

class ContactDetailViewModel : ViewModel() {

    val repository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val collectionRepository: CollectionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val fieldRepository: FieldRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val tagRepository: TagRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val aiTagGenerator: AiTagGenerator = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userProfileTicker: UserProfileTicker = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val platformManifestRepository: PlatformManifestRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    
    fun updateBasicInfoField(
        contactId: Long,
        fieldKey: String,
        newValue: String,
    ) {
        viewModelScope.launch {
            try {
                BadgerLog.d("ContactDetailVMTester", "updateBasicInfoField: contactId=$contactId key=$fieldKey valueLen=${newValue.length}")
                fieldRepository.updateFieldValueByKey(contactId, fieldKey, newValue)
                
                repository.pushBasicInfoEdit(contactId)
                
                repository.bumpContact(contactId)
                
                val fresh = repository.getPersonWithFieldsById(contactId)
                val freshRegion = fresh?.fieldValues?.firstOrNull { it.fieldKey == fieldKey }?.value
                BadgerLog.d("ContactDetailVMTester", "updateBasicInfoField: fresh=${fresh != null} fields=${fresh?.fieldValues?.size ?: -1} $fieldKey=${freshRegion?.let { "len${it.length}" } ?: "null"}")
                if (fresh != null) {
                    _contactWithFields.value = fresh
                }
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
            try {
                val result = repository.getPersonWithFieldsById(contactId)
                _contactWithFields.value = result
                val platforms = repository.getContactPlatforms(contactId)
                _platformData.value = platforms
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "加载联系人失败", e)
            } finally {
                _isLoading.value = false
            }
        }
        
        
        tagsCollectJob?.cancel()
        tagsCollectJob = viewModelScope.launch {
            try {
                tagRepository.observeTagsByContact(contactId).collect { list ->
                    _tags.value = list
                }
            } catch (e: Exception) {
                BadgerLog.e("ContactDetailViewModel", "observeTagsByContact failed contactId=$contactId", e)
            }
        }
    }

    fun reloadContact(contactId: Long) {
        viewModelScope.launch {
            val result = repository.getPersonWithFieldsById(contactId)
            _contactWithFields.value = result
            val platforms = repository.getContactPlatforms(contactId)
            _platformData.value = platforms
            val tags = tagRepository.getTagsByContact(contactId)
            _tags.value = tags
        }
    }

    

    

    fun updateBio(contactId: Long, bio: String?) {
        viewModelScope.launch {
            
            val oldBio = _contactWithFields.value?.contact?.bio
            try {
                repository.updateContactBio(contactId, bio)
                val fresh = repository.getPersonWithFieldsById(contactId)
                if (fresh != null) {
                    _contactWithFields.value = fresh
                }
                
                userProfileTicker.tick()
                _events.send(ContactDetailEvent.RefreshData)
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "updateBio failed, rollback to oldBio", e)
                
                _contactWithFields.update { current ->
                    current?.copy(
                        contact = current.contact.copy(bio = oldBio)
                    )
                }
                failWithToast("更新个人简介", e)
            }
        }
    }

    
    fun updateTags(contactId: Long, addedIds: Set<Long>, removedIds: Set<Long>) {
        viewModelScope.launch {
            try {
                addedIds.forEach { tagId -> tagRepository.addTagToContact(contactId, tagId) }
                removedIds.forEach { tagId -> tagRepository.removeTagFromContact(contactId, tagId) }
                
                
                _events.send(ContactDetailEvent.RefreshData)
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "updateTags failed", e)
                failWithToast("更新标签", e)
            }
        }
    }

    
    suspend fun createTagAndAssign(contactId: Long, name: String, color: Long): Long {
        return try {
            val newId = tagRepository.upsertTag(name, color, source = "manual")
            tagRepository.addTagToContact(contactId, newId)
            _events.send(ContactDetailEvent.RefreshData)
            newId
        } catch (e: Exception) {
            BadgerLog.e(TAG, "createTagAndAssign failed", e)
            failWithToast("创建标签", e)
            -1L
        }
    }

    
    private var aiTagJob: Job? = null

    

    fun generateAiTags(contactId: Long) {
        aiTagJob?.cancel()
        aiTagJob = viewModelScope.launch {
            _aiTagLoading.value = true
            _aiTagError.value = null
            _aiTagCandidates.value = emptyList()
            try {
                val bio = _contactWithFields.value?.contact?.bio?.takeIf { it.isNotBlank() }
                    ?: run {
                        _aiTagError.value = "请先填写个人介绍,AI 才能更准确推荐"
                        _aiTagLoading.value = false
                        return@launch
                    }
                val existingTags = tagRepository.getAllTagsOnce()
                val candidates = try {
                    
                    withTimeoutOrNull(AI_TAG_TIMEOUT_MS) {
                        aiTagGenerator.suggest(bio, existingTags)
                    } ?: run {
                        BadgerLog.w(TAG, "AI 超时 (${AI_TAG_TIMEOUT_MS}ms), 降级本地启发式")
                        _aiTagError.value = "AI 推荐超时,已使用本地匹配"
                        aiTagGenerator.fallbackLocal(bio, existingTags)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiTagException) {
                    BadgerLog.w(TAG, "AI 失败,降级本地启发式: ${e.message}")
                    _aiTagError.value = e.message
                    aiTagGenerator.fallbackLocal(bio, existingTags)
                        .ifEmpty {
                            _aiTagError.value = "AI 推荐失败,且本地启发式未匹配任何标签"
                            emptyList()
                        }
                }
                
                ensureActive()
                _aiTagCandidates.value = candidates
                            } catch (e: CancellationException) {
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "generateAiTags unexpected failure", e)
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
                BadgerLog.e(TAG, "applyAiTagCandidates failed", e)
                failWithToast("采纳 AI 标签", e)
            }
        }
    }

    override fun onCleared() {
        aiTagJob?.cancel()
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

    

    
    suspend fun resolvePlatformForField(
        platformKey: String,
        fieldValue: String
    ): ResolvedPlatformInfo? {
        return try {
            
            
            if (!platformManifestRepository.canSync(platformKey)) {
                BadgerLog.w(TAG, "平台无可用适配器: $platformKey")
                return null
            }
            if (fieldValue.isBlank()) {
                
                
                BadgerLog.w(TAG, "resolvePlatformForField: $platformKey 字段值为空,无法解析")
                return null
            }
            val link = fieldValue
            
            
            val result = withContext(BadgerDispatchers.io) {
                KoinComponentBy.get<ContactNetworkResolver>().identify(link)
            }
            if (result != null) {
                                ResolvedPlatformInfo(
                    name = result.nickname?.takeIf { it.isNotBlank() },
                    avatarUrl = result.avatarUrl?.takeIf { it.isNotBlank() }
                )
            } else null
        } catch (e: Exception) {
            BadgerLog.e(TAG, "字段同步解析失败", e)
            null
        }
    }

    
    suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        repository.getContactPlatforms(contactId)

    
    suspend fun batchResolvePlatforms(urls: List<String>): List<BatchResolvedItem> =
        withContext(BadgerDispatchers.io) {
            val responses = KoinComponentBy.get<ContactNetworkResolver>().identifyBatch(urls)
            urls.zip(responses) { url, resp ->
                val kind = resp?.kind ?: "unknown"
                BatchResolvedItem(
                    url = url,
                    fieldKey = kind,
                    resolved = resp?.let {
                        ResolvedPlatformInfo(
                            name = it.name?.takeIf { n -> n.isNotBlank() },
                            avatarUrl = it.avatarUrl?.takeIf { a -> a.isNotBlank() },
                        )
                    },
                )
            }
        }

    
    suspend fun getContactById(contactId: Long): Contact? =
        repository.getContactById(contactId)

    
    suspend fun getFieldValuesByContactOnce(contactId: Long) =
        fieldRepository.getFieldValuesByContactOnce(contactId)

    

    
    fun updateName(contactId: Long, newName: String) {
        viewModelScope.launch {
            try {
                val freshContact = repository.getContactById(contactId) ?: return@launch
                val updated = freshContact.copy(name = newName, updateTime = nowMs())
                repository.updateContact(updated)
                _contactWithFields.update { it?.copy(contact = updated) }
                _events.send(ContactDetailEvent.RefreshData)
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "更新姓名失败", e)
            }
        }
    }

    
    fun applyAvatarUpdate(contactId: Long, avatarPath: String) {
        viewModelScope.launch {
            try {
                val currentContact = repository.getPersonWithFieldsById(contactId)?.contact ?: return@launch
                val updated = currentContact.copy(
                    avatarPath = avatarPath,
                    updateTime = nowMs()
                )
                repository.updateContact(updated)
                _contactWithFields.update { it?.copy(contact = updated) }
                _events.send(ContactDetailEvent.ShowToast("头像已更新"))
                _events.send(ContactDetailEvent.RefreshData)
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "设置头像失败", e)
                failWithToast("设置头像", e)
            }
        }
    }

    
    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            try {
                val expected = PinyinUtils.getContactPinyinInitial(contact.name)
                val normalized = if (contact.pinyinInitial == expected) contact
                else contact.copy(pinyinInitial = expected)
                repository.updateContact(normalized)
                _contactWithFields.update { it?.copy(contact = normalized) }
                _events.send(ContactDetailEvent.RefreshData)
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "更新联系人失败", e)
            }
        }
    }

    

    fun applySyncResult(contactId: Long, newName: String?, avatarPath: String?, avatarUrl: String?) {
        viewModelScope.launch {
            try {
                val freshContact = repository.getContactById(contactId) ?: return@launch
                var updated = freshContact
                if (!newName.isNullOrBlank()) {
                    updated = updated.copy(name = newName)
                }
                if (!avatarUrl.isNullOrBlank() && (avatarUrl != freshContact.avatarUrl || !avatarPath.isNullOrBlank())) {
                    
                    
                    updated = updated.copy(avatarUrl = avatarUrl, avatarPath = avatarPath)
                }
            if (updated != freshContact) {
                    updated = updated.copy(updateTime = nowMs())
                    repository.updateContact(updated)
                    _contactWithFields.update { it?.copy(contact = updated) }
                    _events.send(ContactDetailEvent.ShowToast("同步成功"))
                    _events.send(ContactDetailEvent.RefreshData)
                                    } else {
                    _events.send(ContactDetailEvent.ShowToast("未获取到可同步的信息"))
                }
            } catch (e: Exception) {
                BadgerLog.e(TAG, "应用同步结果失败", e)
                _events.send(ContactDetailEvent.ShowToast("同步失败"))
            }
        }
    }

    

    fun deleteFieldAndReload(contactId: Long, valueId: Long) {
        viewModelScope.launch {
            deleteFieldValue(contactId, valueId)
            reloadContact(contactId)
        }
    }

    fun updateFieldAndReload(contactId: Long, valueId: Long, newValue: String) {
        viewModelScope.launch {
            updateFieldValue(contactId, valueId, newValue)
            reloadContact(contactId)
        }
    }

    fun addOrUpdatePlatformAndReload(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        viewModelScope.launch {
            addOrUpdatePlatform(contactId, fieldKey, entry)
            reloadContact(contactId)
        }
    }

    

    
    suspend fun deleteFieldValue(contactId: Long, valueId: Long) {
        try {
            val allValues = fieldRepository.getFieldValuesByContactOnce(contactId)
            val target = allValues.find { it.id == valueId }
            if (target != null) {
                fieldRepository.deleteFieldValue(target)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BadgerLog.e(TAG, "删除字段值失败", e)
        }
    }

    
    suspend fun updateFieldValue(contactId: Long, valueId: Long, newValue: String) {
        try {
            val allValues = fieldRepository.getFieldValuesByContactOnce(contactId)
            val target = allValues.find { it.id == valueId }
            if (target != null) {
                fieldRepository.updateFieldValue(
                    target.copy(value = newValue, updateTime = nowMs())
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BadgerLog.e(TAG, "更新字段值失败", e)
        }
    }

    

    
    suspend fun removePlatform(contactId: Long, fieldKey: String) {
        try {
            repository.removeContactPlatform(contactId, fieldKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BadgerLog.e(TAG, "移除平台失败", e)
        }
    }

    
    suspend fun addOrUpdatePlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        try {
            repository.updateContactPlatform(contactId, fieldKey, entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BadgerLog.e(TAG, "更新平台失败", e)
        }
    }

    

    
    fun updateCollections(contactId: Long, addedIds: List<Long>, removedIds: List<Long>) {
        viewModelScope.launch {
            try {
                for (collectionId in addedIds) {
                    collectionRepository.addContactToCollection(
                        contactId = contactId,
                        collectionId = collectionId,
                        sourceType = "manual"
                    )
                }
                for (collectionId in removedIds) {
                    collectionRepository.removeContactFromCollection(contactId, collectionId)
                }
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "更新名片夹失败", e)
            }
        }
    }

    

    
    fun attachToExisting(
        sourceContact: Contact,
        sourceFields: List<PersonFieldDisplay>,
        existingContact: Contact,
        selectedFieldKeys: List<String>,
        selectedCustomFieldIds: List<Long>,
        avatarChecked: Boolean
    ) {
        viewModelScope.launch {
            try {
                attachCurrentContactToExisting(
                    repository = repository,
                    fieldRepository = fieldRepository,
                    sourceContact = sourceContact,
                    sourceFields = sourceFields,
                    existingContact = existingContact,
                    selectedFieldKeys = selectedFieldKeys,
                    selectedCustomFieldIds = selectedCustomFieldIds,
                    avatarChecked = avatarChecked
                )
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "附加字段失败", e)
            }
        }
    }

    

    fun emitToast(message: String) {
        viewModelScope.launch { _events.send(ContactDetailEvent.ShowToast(message)) }
    }

    fun emitRefresh() {
        viewModelScope.launch { _events.send(ContactDetailEvent.RefreshData) }
    }

    
    private fun failWithToast(operation: String, e: Throwable, fallback: String = "未知错误") {
        BadgerLog.e(TAG, "$operation failed", e)
        emitToast("$operation 失败:${e.message ?: fallback}")
    }
}
