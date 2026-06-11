package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
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
    val fieldRepository: FieldRepository
) : ViewModel() {

    private val _contactWithFields = MutableStateFlow<ContactWithFields?>(null)
    val contactWithFields: StateFlow<ContactWithFields?> = _contactWithFields.asStateFlow()

    private val _platformData = MutableStateFlow<List<ContactPlatform>>(emptyList())
    val platformData: StateFlow<List<ContactPlatform>> = _platformData.asStateFlow()

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
    }

    fun reloadContact(contactId: Long) {
        viewModelScope.launch {
            val result = repository.getContactWithFieldsById(contactId)
            _contactWithFields.value = result
            val platforms = repository.getContactPlatforms(contactId)
            _platformData.value = platforms
        }
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
                repository.updateContact(contact)
                _contactWithFields.update { it?.copy(contact = contact) }
                Log.d("Tester", "联系人已更新: id=${contact.id}")
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

    // ========== 扫描记录 ==========

    /** 删除扫描记录 */
    fun deleteScanResult(scanResultId: Long) {
        viewModelScope.launch {
            try {
                collectionRepository.deleteScanResultById(scanResultId)
                Log.d("Tester", "扫描记录已删除: scanResultId=$scanResultId")
            } catch (e: Exception) {
                Log.e("Tester", "删除扫描记录失败", e)
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
