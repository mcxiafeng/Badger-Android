package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.repository.AddPlatformsResult
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.domain.ParsePlatformInputUseCase
import top.mcxiafeng.badger.network.PlatformIdExtractor
import top.mcxiafeng.badger.ocr.ADDABLE_PLATFORMS
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.utils.PinyinUtils

@HiltViewModel
internal class AddPlatformViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val parser: ParsePlatformInputUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(restoreDraft())
    val uiState = _uiState.asStateFlow()
    private var targetId: Long? = null
    private var existingKeys = emptySet<String>()
    private val platformItems by lazy {
        ADDABLE_PLATFORMS.map { def ->
            PlatformListItem(def, PinyinUtils.getContactPinyinInitial(def.displayName), false)
        }.sortedWith(compareBy({ it.initial }, { it.definition.displayName }))
    }

    fun load(contactId: Long) {
        if (targetId == contactId && !_uiState.value.isLoading) return
        targetId = contactId
        Log.d(TAG, "加载添加平台页面")
        viewModelScope.launch {
            try {
                checkNotNull(repository.getContactById(contactId)) { "联系人已不存在" }
                existingKeys = repository.getContactPlatformKeys(contactId).map(::normalize).toSet()
                _uiState.update { it.copy(isLoading = false, error = null) }
                refreshCandidates()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "加载平台失败", error)
                _uiState.update { it.copy(isLoading = false, error = "无法加载联系人，请返回后重试") }
            }
        }
    }

    fun updateInput(input: String) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(input = input, error = null) }
        refreshCandidates()
        persistDraft()
    }

    fun openManual() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(step = AddPlatformStep.MANUAL, error = null) }
        refreshPlatformList()
        persistDraft()
        Log.d(TAG, "打开手动平台列表")
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        refreshPlatformList()
        Log.d(TAG, "更新平台搜索: hasQuery=${query.isNotBlank()}")
    }

    fun selectPlatform(key: String) {
        if (_uiState.value.isSaving) return
        if (key.isNotBlank() && normalize(key) in occupiedKeys()) return
        _uiState.update { it.copy(step = AddPlatformStep.FORM, selectedKey = key,
            account = "", auxiliaryAccount = "", displayName = "", customName = "", error = null) }
        persistDraft()
        Log.d(TAG, "打开平台表单: custom=${key.isBlank()}")
    }

    fun updateAccount(value: String) = updateForm { copy(account = value) }
    fun updateAuxiliary(value: String) = updateForm { copy(auxiliaryAccount = value) }
    fun updateDisplayName(value: String) = updateForm { copy(displayName = value) }
    fun updateCustomName(value: String) = updateForm { copy(customName = value) }

    private fun updateForm(change: AddPlatformUiState.() -> AddPlatformUiState) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.change().copy(error = null) }
        persistDraft()
    }

    fun addManualEntry() {
        val state = _uiState.value
        if (state.isSaving) return
        val key = state.selectedKey.ifBlank { state.customName.trim().lowercase() }
        val error = when {
            key.isBlank() -> "请输入平台名称"
            key.length > MAX_PLATFORM_NAME_LENGTH -> "平台名称最多 $MAX_PLATFORM_NAME_LENGTH 个字符"
            state.selectedKey.isBlank() && normalize(key) in FIELD_DEF_MAP -> "请从平台列表选择该平台"
            normalize(key) in occupiedKeys() -> "该平台已添加，请编辑原条目"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            Log.d(TAG, "手动添加校验未通过")
            return
        }
        val parsed = parser.manual(key, state.account.ifBlank { state.auxiliaryAccount }, state.displayName)
        if (parsed.entry == null) {
            _uiState.update { it.copy(error = parsed.error) }
            return
        }
        val entry = parsed.entry.copy(value = state.auxiliaryAccount.trim().ifBlank { parsed.entry.value })
        _uiState.update { it.copy(pending = it.pending + (key to entry), step = AddPlatformStep.AUTOMATIC,
            account = "", auxiliaryAccount = "", displayName = "", customName = "", error = null) }
        refreshCandidates()
        persistDraft()
        Log.d(TAG, "手动条目加入待添加列表")
    }

    fun removePending(key: String) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(pending = it.pending - key, error = null) }
        refreshCandidates()
        persistDraft()
        Log.d(TAG, "移除待添加条目")
    }

    fun back(): Boolean {
        if (_uiState.value.isSaving) return true
        val step = when (_uiState.value.step) {
            AddPlatformStep.FORM -> AddPlatformStep.MANUAL
            AddPlatformStep.MANUAL -> AddPlatformStep.AUTOMATIC
            AddPlatformStep.AUTOMATIC -> return false
        }
        _uiState.update { it.copy(step = step, error = null) }
        refreshPlatformList()
        persistDraft()
        Log.d(TAG, "返回添加平台上一步: step=$step")
        return true
    }

    fun save() {
        if (_uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.isSaved) return
        _uiState.update { it.copy(error = null) }
        refreshCandidates()
        val state = _uiState.value
        val contactId = targetId ?: return
        if (state.error != null || state.candidates.any { it.message != null } || state.additions.isEmpty()) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        Log.d(TAG, "开始保存平台: count=${state.additions.size}")
        viewModelScope.launch {
            try {
                when (val result = repository.addContactPlatforms(contactId, state.additions)) {
                    AddPlatformsResult.Saved -> {
                        savedStateHandle.remove<Bundle>(DRAFT)
                        _uiState.update { it.copy(isSaving = false, isSaved = true) }
                        Log.d(TAG, "平台已存本机: count=${state.additions.size}")
                    }
                    is AddPlatformsResult.Duplicate -> {
                        existingKeys = existingKeys + result.keys
                        _uiState.update { it.copy(isSaving = false, error = "部分平台已添加，请检查后重试") }
                        refreshCandidates()
                        Log.d(TAG, "保存遇到重复平台: count=${result.keys.size}")
                    }
                    AddPlatformsResult.ContactMissing -> {
                        _uiState.update { it.copy(isSaving = false, error = "联系人已不存在，无法添加平台") }
                        Log.d(TAG, "保存中止: 联系人已不存在")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "保存平台失败", error)
                _uiState.update { it.copy(isSaving = false, error = "保存失败，输入已保留，请重试") }
            }
        }
    }

    private fun refreshCandidates() {
        val state = _uiState.value
        if (state.input.length > MAX_INPUT_LENGTH || state.input.lineSequence().count() > MAX_INPUT_LINES) {
            _uiState.update { it.copy(candidates = emptyList(), error =
                "最多输入 $MAX_INPUT_LINES 行、$MAX_INPUT_LENGTH 个字符，请分批添加") }
            return
        }
        val occupied = (existingKeys + state.pending.keys.map(::normalize)).toMutableSet()
        val candidates = parser(state.input).map { result ->
            val duplicate = result.fieldKey?.let { !occupied.add(normalize(it)) } == true
            PlatformCandidate(result.lineNumber, result.fieldKey, result.entry,
                result.error ?: if (duplicate) "该平台已添加或在列表中重复，请修改此行" else null)
        }
        _uiState.update { it.copy(candidates = candidates) }
        refreshPlatformList()
    }

    private fun occupiedKeys(): Set<String> = existingKeys + _uiState.value.additions.keys.map(::normalize)

    private fun refreshPlatformList() {
        val state = _uiState.value
        val query = state.query.trim()
        val occupied = occupiedKeys()
        val items = platformItems.filter { item ->
            val def = item.definition
            query.isBlank() || def.displayName.contains(query, true) || def.fieldKey.contains(query, true) ||
                item.initial.equals(query, true) || def.aliases.any { it.contains(query, true) }
        }.map { it.copy(isAdded = normalize(it.definition.fieldKey) in occupied) }
        _uiState.update { it.copy(platforms = items) }
    }

    private fun persistDraft() {
        val state = _uiState.value
        // Bundle 的显式字段不依赖反射或混淆后的 Kotlin 类名。
        savedStateHandle[DRAFT] = Bundle().apply {
            putString("input", state.input)
            putString("step", state.step.name)
            putString("query", state.query)
            putString("selectedKey", state.selectedKey)
            putString("customName", state.customName)
            putString("account", state.account)
            putString("auxiliaryAccount", state.auxiliaryAccount)
            putString("displayName", state.displayName)
            putParcelableArrayList("pending", ArrayList(state.pending.map { (key, entry) ->
                Bundle().apply {
                    putString("key", key)
                    putString("value", entry.value)
                    putString("displayName", entry.displayName)
                    putString("jumpLink", entry.jumpLink)
                    putString("originalLink", entry.originalLink)
                    putString("avatarUrl", entry.avatarUrl)
                }
            }))
        }
    }

    @Suppress("DEPRECATION") // minSdk 26，需要兼容旧版 Bundle 读取接口。
    private fun restoreDraft(): AddPlatformUiState {
        val draft = savedStateHandle.get<Bundle>(DRAFT) ?: return AddPlatformUiState()
        return try {
            val pending = draft.getParcelableArrayList<Bundle>("pending").orEmpty().associate { entry ->
                requireNotNull(entry.getString("key")) to PlatformEntry(
                    displayName = entry.getString("displayName"), jumpLink = entry.getString("jumpLink").orEmpty(),
                    originalLink = entry.getString("originalLink"), value = entry.getString("value"),
                    avatarUrl = entry.getString("avatarUrl"))
            }
            AddPlatformUiState(input = draft.getString("input").orEmpty(),
                step = AddPlatformStep.valueOf(requireNotNull(draft.getString("step"))),
                query = draft.getString("query").orEmpty(), selectedKey = draft.getString("selectedKey").orEmpty(),
                customName = draft.getString("customName").orEmpty(), account = draft.getString("account").orEmpty(),
                auxiliaryAccount = draft.getString("auxiliaryAccount").orEmpty(),
                displayName = draft.getString("displayName").orEmpty(), pending = pending)
        } catch (error: Exception) {
            Log.e(TAG, "无法恢复平台输入草稿", error)
            AddPlatformUiState(error = "无法恢复输入，请重新填写")
        }
    }

    private fun normalize(key: String) = PlatformIdExtractor.normalizeToKey(key)

    companion object {
        private const val TAG = "AddPlatformViewModelTester"
        private const val DRAFT = "platform_draft"
        private const val MAX_INPUT_LENGTH = 20_000
        private const val MAX_INPUT_LINES = 100
        private const val MAX_PLATFORM_NAME_LENGTH = 40
    }
}
