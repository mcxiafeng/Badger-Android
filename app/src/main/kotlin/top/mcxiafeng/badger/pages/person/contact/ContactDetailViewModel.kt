package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.mcxiafeng.badger.ai.AiTagException
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.utils.PinyinUtils
import javax.inject.Inject

/**
 * 平台解析结果（不含本地文件路径，头像由 UI 层下载保存）
 */
data class ResolvedPlatformInfo(
    val name: String?,
    val avatarUrl: String?
)

/**
 * ViewModel 向 UI 层发出的一次性事件
 */
sealed class ContactDetailEvent {
    data class ShowToast(val message: String) : ContactDetailEvent()
    data object RefreshData : ContactDetailEvent()
}

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    val repository: ContactRepository,
    val collectionRepository: CollectionRepository,
    val fieldRepository: FieldRepository,
    val tagRepository: TagRepository,
    private val aiTagGenerator: AiTagGenerator,
    private val userProfileTicker: UserProfileTicker,
) : ViewModel() {

    /**
     * 写入/更新某联系人的基础信息字段(性别 / 生日 / 国家 / 地区)。
     * 写完后让 ContactDao 触发 PagingSource/Flow 失效(通过 ContactRepository 的 bumpContact)。
     */
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
                val fresh = repository.getContactWithFieldsById(contactId)
                if (fresh != null) {
                    _contactWithFields.value = fresh
                }
                _events.send(ContactDetailEvent.ShowToast("已更新"))
                _events.send(ContactDetailEvent.RefreshData)
            } catch (e: Exception) {
                Log.e("ContactDetailViewModel", "updateBasicInfoField failed key=$fieldKey", e)
                _events.send(ContactDetailEvent.ShowToast("更新失败:${e.message}"))
            }
        }
    }

    private val _contactWithFields = MutableStateFlow<ContactWithFields?>(null)
    val contactWithFields: StateFlow<ContactWithFields?> = _contactWithFields.asStateFlow()

    private val _platformData = MutableStateFlow<List<ContactPlatform>>(emptyList())
    val platformData: StateFlow<List<ContactPlatform>> = _platformData.asStateFlow()

    /** 联系人当前标签列表 ——
     *  通过 [tagRepository.observeTagsByContact] 订阅 Room Flow（tag 表 JOIN contact_tag），
     *  让 Tag 表的颜色 / 名字 / showDot 等任意字段变更都能立即反映到详情页。
     *  loadContact 时启动一个新 collect 协程，旧协程自动取消。
     */
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

    init {
        Log.d("Tester", "ContactDetailViewModel initialized")
    }

    // ========== 数据加载 ==========

    fun loadContact(contactId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.getContactWithFieldsById(contactId)
                _contactWithFields.value = result
                val platforms = repository.getContactPlatforms(contactId)
                _platformData.value = platforms
                Log.d("Tester", "联系人数据已加载: contactId=$contactId")
            } catch (e: Exception) {
                Log.e("Tester", "加载联系人失败", e)
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
                Log.e("ContactDetailViewModel", "observeTagsByContact failed contactId=$contactId", e)
            }
        }
    }

    fun reloadContact(contactId: Long) {
        viewModelScope.launch {
            val result = repository.getContactWithFieldsById(contactId)
            _contactWithFields.value = result
            val platforms = repository.getContactPlatforms(contactId)
            _platformData.value = platforms
            val tags = tagRepository.getTagsByContact(contactId)
            _tags.value = tags
        }
    }

    // ========== Bio / Tags 改动 ==========

    /**
     * 更新个人介绍。
     * 写完后调 [reloadContact] 刷新 bio 字段 + 触发 PagingSource/Flow(由 updateContactBio → bumpContact)。
     *
     * [P1-8] 增强:
     * - 失败时回滚本地 _contactWithFields.bio 到旧值,避免 UI 显示"已成功"但 DB 写失败
     * - 成功后调 [appViewModel.refreshUserProfile] 触发 PersonPage 的 userProfileTick 链
     */
    fun updateBio(contactId: Long, bio: String?) {
        viewModelScope.launch {
            // [P1-8] 记录旧值,失败时回滚
            val oldBio = _contactWithFields.value?.contact?.bio
            try {
                repository.updateContactBio(contactId, bio)
                val fresh = repository.getContactWithFieldsById(contactId)
                if (fresh != null) {
                    _contactWithFields.value = fresh
                }
                // [P1-8] 触发 PersonPage 的 userProfileTick 链
                userProfileTicker.tick()
                _events.send(ContactDetailEvent.RefreshData)
                Log.d("Tester", "Bio 已更新: id=$contactId, len=${bio?.length}")
            } catch (e: Exception) {
                Log.e("Tester", "updateBio failed, rollback to oldBio", e)
                // [P1-8] 失败回滚本地状态
                _contactWithFields.update { current ->
                    current?.copy(
                        contact = current.contact.copy(bio = oldBio)
                    )
                }
                _events.send(ContactDetailEvent.ShowToast("更新个人介绍失败: ${e.message ?: "未知错误"}"))
            }
        }
    }

    /**
     * 更新联系人标签(已勾选集合 vs 之前集合的差集):
     * - added:新勾选的 tag → addTagToContact
     * - removed:之前勾选但现在未勾选 → removeTagFromContact
     */
    fun updateTags(contactId: Long, addedIds: Set<Long>, removedIds: Set<Long>) {
        viewModelScope.launch {
            try {
                addedIds.forEach { tagId -> tagRepository.addTagToContact(contactId, tagId) }
                removedIds.forEach { tagId -> tagRepository.removeTagFromContact(contactId, tagId) }
                // [修复防御]: tags 由 loadContact 内启动的 Room Flow 订阅自动刷新,
                // 这里不再手动重拉(getTagsByContact),否则会出现"先空 → 后填"的闪烁。
                _events.send(ContactDetailEvent.RefreshData)
                Log.d("Tester", "Tags updated: contact=$contactId added=${addedIds.size} removed=${removedIds.size}")
            } catch (e: Exception) {
                Log.e("Tester", "updateTags failed", e)
                _events.send(ContactDetailEvent.ShowToast("更新标签失败"))
            }
        }
    }

    /**
     * 创建新 Tag 并立即关联到联系人。
     * - name 已存在时 upsertTag 返回旧 id,不重复创建。
     */
    fun createTagAndAssign(contactId: Long, name: String, color: Long): Long {
        var newId = -1L
        viewModelScope.launch {
            try {
                newId = tagRepository.upsertTag(name, color, source = "manual")
                tagRepository.addTagToContact(contactId, newId)
                // tags 由 Room Flow 自动刷新,无需手动重拉。
                _events.send(ContactDetailEvent.RefreshData)
                Log.d("Tester", "createTagAndAssign: id=$newId contact=$contactId")
            } catch (e: Exception) {
                Log.e("Tester", "createTagAndAssign failed", e)
                _events.send(ContactDetailEvent.ShowToast("创建标签失败"))
            }
        }
        return newId
    }

    /** AI 标签生成的协程句柄,新调用时取消旧的（[P1-7] 防抖） */
    private var aiTagJob: Job? = null

    /**
     * 调用 AI 推荐标签。
     * 已有 Tag 列表 + bio 一起送入 LLM,要求优先复用已有。
     * LLM 失败时降级为本地启发式 substring 匹配。
     *
     * [P1-7] 增强:
     * - 旧请求未结束 → 取消（避免连点 ✨ 触发多次 LLM 调用）
     * - 30s 超时 → 降级本地启发式
     * - 协程被取消时不覆盖 _aiTagCandidates 状态
     * - ViewModel.onCleared 时取消,避免离开页面后还在跑
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
                        Log.w("Tester", "AI 超时 (${AI_TAG_TIMEOUT_MS}ms), 降级本地启发式")
                        _aiTagError.value = "AI 推荐超时,已使用本地匹配"
                        aiTagGenerator.fallbackLocal(bio, existingTags)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiTagException) {
                    Log.w("Tester", "AI 失败,降级本地启发式: ${e.message}")
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
                Log.d("Tester", "AI candidates ready: ${candidates.size}")
            } catch (e: CancellationException) {
                Log.d("Tester", "generateAiTags cancelled (newer call superseded)")
            } catch (e: Exception) {
                Log.e("Tester", "generateAiTags unexpected failure", e)
                _aiTagError.value = "生成失败: ${e.message}"
            } finally {
                _aiTagLoading.value = false
            }
        }
    }

    /**
     * 用户在 AI 预览 Dialog 中点"采纳"后:
     * [P1-5] 整批原子写入,失败整体回滚（事务在 [tagRepository.applyAiTagCandidatesAtomic] 内）。
     * 同时 distinctBy 去重,避免同名 candidate 被勾两次撞 unique 索引 ABORT。
     */
    fun applyAiTagCandidates(contactId: Long, selected: List<AiTagGenerator.TagCandidate>) {
        viewModelScope.launch {
            try {
                tagRepository.applyAiTagCandidatesAtomic(contactId, selected)
                _aiTagCandidates.value = emptyList()
                // tags 由 Room Flow 自动刷新
                _events.send(ContactDetailEvent.RefreshData)
                Log.d("Tester", "applyAiTagCandidates: ${selected.size} tags applied atomically")
            } catch (e: Exception) {
                Log.e("Tester", "applyAiTagCandidates failed", e)
                _events.send(ContactDetailEvent.ShowToast("采纳 AI 标签失败:${e.message ?: "未知错误"}"))
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
    }

    fun clearAiTagCandidates() {
        _aiTagCandidates.value = emptyList()
        _aiTagError.value = null
    }

    // ========== 查询方法（suspend，由调用方控制执行） ==========

    /** 通过平台适配器解析字段值，返回昵称和头像 URL */
    suspend fun resolvePlatformForField(
        platformKey: String,
        fieldValue: String
    ): ResolvedPlatformInfo? {
        return try {
            val contactType = FIELD_DEF_MAP[platformKey]?.contactType
            val adapter = contactType?.let { PlatformAdapterRegistry.getAdapter(it) }
            if (adapter == null || !adapter.canSync) {
                Log.w("Tester", "平台无可用适配器: $platformKey")
                return null
            }
            val link = fieldValue.ifBlank {
                FIELD_DEF_MAP[platformKey]?.let { def ->
                    def.linkTemplate?.replace("%s", fieldValue)
                        ?: buildPlatformLink(platformKey, fieldValue)
                }
            } ?: fieldValue
            val result = adapter.resolve(link)
            if (result != null) {
                Log.d("Tester", "字段同步解析完成: $platformKey name=${result.name}")
                ResolvedPlatformInfo(
                    name = result.name?.takeIf { it.isNotBlank() },
                    avatarUrl = result.avatarUrl?.takeIf { it.isNotBlank() }
                )
            } else null
        } catch (e: Exception) {
            Log.e("Tester", "字段同步解析失败", e)
            null
        }
    }

    /** 获取联系人平台列表（用于头像回退等场景） */
    suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        repository.getContactPlatforms(contactId)

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
                val updated = freshContact.copy(name = newName, updateTime = System.currentTimeMillis())
                repository.updateContact(updated)
                _contactWithFields.update { it?.copy(contact = updated) }
                _events.send(ContactDetailEvent.RefreshData)
                Log.d("Tester", "联系人姓名已更新: $newName")
            } catch (e: Exception) {
                Log.e("Tester", "更新姓名失败", e)
            }
        }
    }

    /** 更新联系人头像路径（文件已由 UI 层保存） */
    fun applyAvatarUpdate(contactId: Long, avatarPath: String) {
        viewModelScope.launch {
            try {
                val currentContact = repository.getContactWithFieldsById(contactId)?.contact ?: return@launch
                val updated = currentContact.copy(
                    avatarPath = avatarPath,
                    updateTime = System.currentTimeMillis()
                )
                repository.updateContact(updated)
                _contactWithFields.update { it?.copy(contact = updated) }
                _events.send(ContactDetailEvent.ShowToast("头像已更新"))
                _events.send(ContactDetailEvent.RefreshData)
                Log.d("Tester", "头像已更新: contactId=$contactId path=$avatarPath")
            } catch (e: Exception) {
                Log.e("Tester", "设置头像失败", e)
                _events.send(ContactDetailEvent.ShowToast("设置头像失败"))
            }
        }
    }

    /** 通用联系人更新（直接传入已构造好的 Contact 对象） */
    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            try {
                // [修复防御]: 详情页改名时主动重算 pinyinInitial,避免主列表排序桶错乱
                // (主列表 ORDER BY pinyinInitial ASC + name ASC;pinyinInitial 留旧值时,
                // 新名字会按 name 二级排序穿插在旧桶里)。Repository 内部也会再做一次
                // normalizePinyinInitial 作为最后兜底——这里前置重算,让传给 Repository
                // 的 contract 与 UI 期望严格一致(便于 logging & 测试)。
                val expected = PinyinUtils.getContactPinyinInitial(contact.name)
                val normalized = if (contact.pinyinInitial == expected) contact
                else contact.copy(pinyinInitial = expected)
                repository.updateContact(normalized)
                _contactWithFields.update { it?.copy(contact = normalized) }
                _events.send(ContactDetailEvent.RefreshData)
                Log.d(
                    "Tester",
                    "联系人已更新: id=${contact.id} name='${contact.name}' pinyinInitial=${normalized.pinyinInitial}",
                )
            } catch (e: Exception) {
                Log.e("Tester", "更新联系人失败", e)
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
                    updated = updated.copy(updateTime = System.currentTimeMillis())
                    repository.updateContact(updated)
                    _contactWithFields.update { it?.copy(contact = updated) }
                    _events.send(ContactDetailEvent.ShowToast("同步成功"))
                    _events.send(ContactDetailEvent.RefreshData)
                    Log.d("Tester", "同步结果已应用: name=${updated.name}")
                } else {
                    _events.send(ContactDetailEvent.ShowToast("未获取到可同步的信息"))
                }
            } catch (e: Exception) {
                Log.e("Tester", "应用同步结果失败", e)
                _events.send(ContactDetailEvent.ShowToast("同步失败"))
            }
        }
    }

    // ========== 字段值增删改 ==========

    /** 删除指定字段值 */
    fun deleteFieldValue(contactId: Long, valueId: Long) {
        viewModelScope.launch {
            try {
                val allValues = fieldRepository.getFieldValuesByContactOnce(contactId)
                val target = allValues.find { it.id == valueId }
                if (target != null) {
                    fieldRepository.deleteFieldValue(target)
                    Log.d("Tester", "字段值已删除: valueId=$valueId")
                }
            } catch (e: Exception) {
                Log.e("Tester", "删除字段值失败", e)
            }
        }
    }

    /** 更新字段值 */
    fun updateFieldValue(contactId: Long, valueId: Long, newValue: String) {
        viewModelScope.launch {
            try {
                val allValues = fieldRepository.getFieldValuesByContactOnce(contactId)
                val target = allValues.find { it.id == valueId }
                if (target != null) {
                    fieldRepository.updateFieldValue(
                        target.copy(value = newValue, updateTime = System.currentTimeMillis())
                    )
                    Log.d("Tester", "字段值已更新: valueId=$valueId")
                }
            } catch (e: Exception) {
                Log.e("Tester", "更新字段值失败", e)
            }
        }
    }

    // ========== 社交平台 ==========

    /** 删除社交平台 */
    fun removePlatform(contactId: Long, fieldKey: String) {
        viewModelScope.launch {
            try {
                repository.removeContactPlatform(contactId, fieldKey)
                Log.d("Tester", "平台已移除: fieldKey=$fieldKey")
            } catch (e: Exception) {
                Log.e("Tester", "移除平台失败", e)
            }
        }
    }

    /** 添加/更新社交平台 */
    fun addOrUpdatePlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        viewModelScope.launch {
            try {
                repository.updateContactPlatform(contactId, fieldKey, entry)
                _events.send(ContactDetailEvent.RefreshData)
                Log.d("Tester", "平台已更新: fieldKey=$fieldKey")
            } catch (e: Exception) {
                Log.e("Tester", "更新平台失败", e)
            }
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
                Log.d("Tester", "名片夹已更新: added=${addedIds.size}, removed=${removedIds.size}")
            } catch (e: Exception) {
                Log.e("Tester", "更新名片夹失败", e)
            }
        }
    }

    // ========== 附加到已有联系人 ==========

    /** 将当前联系人的字段附加到已有联系人 */
    fun attachToExisting(
        sourceContact: Contact,
        sourceFields: List<ContactFieldDisplay>,
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
                Log.d("Tester", "字段已附加: from=${sourceContact.id} to=${existingContact.id}")
            } catch (e: Exception) {
                Log.e("Tester", "附加字段失败", e)
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
}
