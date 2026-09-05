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
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.pages.person.contact.dialogs.attachCurrentContactToExisting
import top.mcxiafeng.badger.shared.util.PinyinUtils
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

/**
 * 平台解析结果（不含本地文件路径，头像由 UI 层下载保存）
 */
data class ResolvedPlatformInfo(
    val name: String?,
    val avatarUrl: String?
)

/**
 * 批量解析单条结果（供 BatchImportPlatformsDialog 展示 + 用户勾选后批量添加）
 *
 * @param url 用户输入的原始 URL
 * @param fieldKey 服务端识别的平台 key（如 "bilibili"、"qq"）
 * @param resolved 解析详情；null 表示该 URL 解析失败
 * @param selected 用户是否勾选（UI 层控制，初始 true）
 */
data class BatchResolvedItem(
    val url: String,
    val fieldKey: String,
    val resolved: ResolvedPlatformInfo?,
    val selected: Boolean = true,
)

/**
 * ViewModel 向 UI 层发出的一次性事件
 */
sealed class ContactDetailEvent {
    data class ShowToast(val message: String) : ContactDetailEvent()
    data object RefreshData : ContactDetailEvent()
}

/**
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin `inject()` 字段注入。
 */
class ContactDetailViewModel : ViewModel() {

    val repository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val collectionRepository: CollectionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val fieldRepository: FieldRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val tagRepository: TagRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val aiTagGenerator: AiTagGenerator = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userProfileTicker: UserProfileTicker = top.mcxiafeng.badger.di.KoinComponentBy.get()

    /** 更新基础信息字段（性别/生日/国家/地区）。 */
    fun updateBasicInfoField(
        contactId: Long,
        fieldKey: String,
        newValue: String,
    ) {
        viewModelScope.launch {
            try {
                fieldRepository.updateFieldValueByKey(contactId, fieldKey, newValue)
                // 触发 PagingSource/Flow 失效(参见 TagRepositoryImpl 同模式)
                repository.bumpContact(contactId)
                // 重读 contactWithFields 让 UI 立即更新
                val fresh = repository.getPersonWithFieldsById(contactId)
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

    /** 联系人当前标签列表，通过 Room Flow 订阅自动更新。 */
    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()
    private var tagsCollectJob: Job? = null

    /** AI 生成的候选标签(给 AiTagPreviewDialog) */
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

    // ========== 数据加载 ==========

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
        // 启动（或切换）tags 的 Room Flow 订阅 —— 取消旧订阅,采集新联系人的 tag 表 Flow,
        // 任意 tag 字段(颜色 / 名字 / showDot)变更都会自动更新 [tags]。
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

    // ========== Bio / Tags 改动 ==========

    /**
     * 更新个人介绍，失败回滚本地状态。
     */
    fun updateBio(contactId: Long, bio: String?) {
        viewModelScope.launch {
            // [P1-8] 记录旧值,失败时回滚
            val oldBio = _contactWithFields.value?.contact?.bio
            try {
                repository.updateContactBio(contactId, bio)
                val fresh = repository.getPersonWithFieldsById(contactId)
                if (fresh != null) {
                    _contactWithFields.value = fresh
                }
                // [P1-8] 触发 PersonPage 的 userProfileTick 链
                userProfileTicker.tick()
                _events.send(ContactDetailEvent.RefreshData)
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "updateBio failed, rollback to oldBio", e)
                // [P1-8] 失败回滚本地状态
                _contactWithFields.update { current ->
                    current?.copy(
                        contact = current.contact.copy(bio = oldBio)
                    )
                }
                failWithToast("更新个人简介", e)
            }
        }
    }

    /** 更新标签（增量 add/remove）。 */
    fun updateTags(contactId: Long, addedIds: Set<Long>, removedIds: Set<Long>) {
        viewModelScope.launch {
            try {
                addedIds.forEach { tagId -> tagRepository.addTagToContact(contactId, tagId) }
                removedIds.forEach { tagId -> tagRepository.removeTagFromContact(contactId, tagId) }
                // [修复防御]: tags 由 loadContact 内启动的 Room Flow 订阅自动刷新,
                // 这里不再手动重拉(getTagsByContact),否则会出现"先空 → 后填"的闪烁。
                _events.send(ContactDetailEvent.RefreshData)
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "updateTags failed", e)
                failWithToast("更新标签", e)
            }
        }
    }

    /** 创建新 Tag 并关联到联系人，name 已存在时复用。 */
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

    /** AI 标签生成的协程句柄,新调用时取消旧的（[P1-7] 防抖） */
    private var aiTagJob: Job? = null

    /**
     * 调用 AI 推荐标签，30s 超时降级本地启发式。
     */
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
                    // [P1-7] 30s 超时保护
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
                // [P1-7] 协程被取消时不写状态,避免取消后还覆盖 candidates
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

    /**
     * 用户在 AI 预览 Dialog 点"采纳"后，整批原子写入。
     */
    fun applyAiTagCandidates(contactId: Long, selected: List<AiTagGenerator.TagCandidate>) {
        viewModelScope.launch {
            try {
                tagRepository.applyAiTagCandidatesAtomic(contactId, selected)
                _aiTagCandidates.value = emptyList()
                // tags 由 Room Flow 自动刷新
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
        /** AI 单次推荐最长 30s,超时后降级本地启发式 */
        const val AI_TAG_TIMEOUT_MS = 30_000L
        /** Logger tag for the [§15 #4] unified catch helper. */
        const val TAG = "ContactDetailViewModel"
    }

    fun clearAiTagCandidates() {
        _aiTagCandidates.value = emptyList()
        _aiTagError.value = null
    }

    // ========== 查询方法（suspend，由调用方控制执行） ==========

    /** 通过平台适配器解析字段值，返回昵称和头像 URL。 */
    suspend fun resolvePlatformForField(
        platformKey: String,
        fieldValue: String
    ): ResolvedPlatformInfo? {
        return try {
            val def = FIELD_DEF_MAP[platformKey]
            val contactType = def?.contactType
            // sync 判定基于 platformKey 字符串（参见 SYNCABLE_KINDS），不再依赖
            // ContactType —— 服务端的 `/v1/resolver/<kind>/...` 才是真值源。
            if (!platformKey.kindCanSync) {
                BadgerLog.w(TAG, "平台无可用适配器: $platformKey")
                return null
            }
            val link = if (fieldValue.isNotBlank()) fieldValue else {
                def?.linkTemplate?.replace("%s", fieldValue)
                    ?: buildPlatformLink(platformKey, fieldValue)
            }
            // 切到 IO 线程：`ContactNetworkResolver.identify` 内部走网络同步调用，
            // 阻塞当前协程所在调度器。
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

    /** 获取联系人平台列表（用于头像回退等场景） */
    suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        repository.getContactPlatforms(contactId)

    /** 批量解析多个 URL，返回每条的平台 key + 解析详情。 */
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

    /** 从 DB 重新读取联系人 */
    suspend fun getContactById(contactId: Long): Contact? =
        repository.getContactById(contactId)

    /** 获取联系人的所有字段值 */
    suspend fun getFieldValuesByContactOnce(contactId: Long) =
        fieldRepository.getFieldValuesByContactOnce(contactId)

    // ========== 联系人基本更新 ==========

    /** 更新联系人姓名 */
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

    /** 更新联系人头像路径（文件已由 UI 层保存） */
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

    /** 通用联系人更新。 */
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

    /** 应用同步结果（名字 + 头像路径） */
    fun applySyncResult(contactId: Long, newName: String?, avatarPath: String?) {
        viewModelScope.launch {
            try {
                val freshContact = repository.getContactById(contactId) ?: return@launch
                var updated = freshContact
                if (!newName.isNullOrBlank()) {
                    updated = updated.copy(name = newName)
                }
                if (!avatarPath.isNullOrBlank()) {
                    updated = updated.copy(avatarPath = avatarPath)
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

    // ========== 便捷方法（页面调用，内部 launch + reload） ==========

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

    // ========== 字段值增删改 ==========

    /** 删除字段值。 */
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

    /** 更新字段值。 */
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

    // ========== 社交平台 ==========

    /** 删除社交平台 */
    suspend fun removePlatform(contactId: Long, fieldKey: String) {
        try {
            repository.removeContactPlatform(contactId, fieldKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BadgerLog.e(TAG, "移除平台失败", e)
        }
    }

    /** 添加/更新社交平台。 */
    suspend fun addOrUpdatePlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        try {
            repository.updateContactPlatform(contactId, fieldKey, entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BadgerLog.e(TAG, "更新平台失败", e)
        }
    }

    // ========== 名片夹管理 ==========

    /** 更新联系人所属名片夹 */
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

    // ========== 附加到已有联系人 ==========

    /** 将当前联系人的字段附加到已有联系人 */
    fun attachToExisting(
        sourceContact: Contact,
        sourceFields: List<PersonFieldDisplay>,
        existingContact: Contact,
        selectedFieldKeys: List<String>,
        selectedCustomFieldIds: List<Long>
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
                    selectedCustomFieldIds = selectedCustomFieldIds
                )
                            } catch (e: Exception) {
                BadgerLog.e(TAG, "附加字段失败", e)
            }
        }
    }

    // ========== 事件 ==========

    fun emitToast(message: String) {
        viewModelScope.launch { _events.send(ContactDetailEvent.ShowToast(message)) }
    }

    fun emitRefresh() {
        viewModelScope.launch { _events.send(ContactDetailEvent.RefreshData) }
    }

    /** 统一 catch 处理：记录日志 + 发 toast。 */
    private fun failWithToast(operation: String, e: Throwable, fallback: String = "未知错误") {
        BadgerLog.e(TAG, "$operation failed", e)
        emitToast("$operation 失败:${e.message ?: fallback}")
    }
}
