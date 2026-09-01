package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.repository.TagRepository

/** 标签管理页 ViewModel：集中管理列表状态、筛选排序、多选和 CRUD 操作。 */
class TagManagerSettingsViewModel(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val filterMode = MutableStateFlow(TagFilterMode.All)
    private val sortMode = MutableStateFlow(TagSortMode.Alphabetical)
    private val multiSelect = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 每次 Refresh 都重新创建 Repository Flow；瞬时观察异常自动退避重试。 */
    private val tagsFlow: Flow<List<Tag>> = refreshTrigger.flatMapLatest {
        tagRepository.observeAllTags()
            .retryWhen { _, attempt ->
                if (attempt < 2) {
                    kotlinx.coroutines.delay(300L * (attempt + 1))
                    true
                } else {
                    false
                }
            }
    }

    val uiState: StateFlow<TagManagerUiState> = combine(
        tagsFlow,
        filterMode,
        sortMode,
        multiSelect,
        selectedIds,
    ) { tags: List<Tag>, f: TagFilterMode, s: TagSortMode, ms: Boolean, sel: Set<Long> ->
        TagManagerUiState.Success(
            tags = tags,
            filterMode = f,
            sortMode = s,
            selectedIds = sel,
            multiSelect = ms,
        )
    }
        .catch { e ->
            Log.e(TAG, "observeAllTags failed", e)
            emit(TagManagerUiState.Error(e.message ?: "加载失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TagManagerUiState.Loading,
        )

    private val _messages = Channel<TagManagerMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onEvent(event: TagManagerEvent) {
        when (event) {
            is TagManagerEvent.ChangeFilter -> filterMode.value = event.mode
            is TagManagerEvent.ChangeSort -> sortMode.value = event.mode
            is TagManagerEvent.EnterMultiSelect -> {
                multiSelect.value = true
                event.initialSelectedId?.let { selectedIds.value += it }
            }
            TagManagerEvent.ExitMultiSelect -> {
                multiSelect.value = false
                selectedIds.value = emptySet()
            }
            is TagManagerEvent.ToggleSelect -> {
                selectedIds.value = if (event.tagId in selectedIds.value) {
                    selectedIds.value - event.tagId
                } else {
                    selectedIds.value + event.tagId
                }
            }
            TagManagerEvent.SelectAll -> {
                val state = uiState.value as? TagManagerUiState.Success ?: return
                selectedIds.value = state.visibleTags.mapTo(linkedSetOf()) { it.id }
            }
            TagManagerEvent.ClearSelection -> selectedIds.value = emptySet()
            is TagManagerEvent.Create -> createTag(event.name, event.colorArgb)
            is TagManagerEvent.Rename -> renameTag(event.tagId, event.newName)
            is TagManagerEvent.SetColor -> setTagColor(event.tagId, event.colorArgb)
            is TagManagerEvent.SetShowDot -> setShowDot(event.tagId, event.show)
            is TagManagerEvent.ForceDelete -> forceDelete(event.tagId)
            is TagManagerEvent.Merge -> merge(event.fromTagId, event.toTagId)
            is TagManagerEvent.BatchSetColor -> batchSetColor(event.tagIds, event.colorArgb)
            is TagManagerEvent.BatchDelete -> batchDelete(event.tagIds)
            TagManagerEvent.Refresh -> refreshTrigger.update { it + 1 }
        }
    }

    private fun createTag(name: String, colorArgb: Long) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            sendError("标签名不能为空")
            return@launch
        }
        try {
            tagRepository.upsertTag(trimmed, color = colorArgb, source = "manual")
            sendInfo("已新建「$trimmed」")
        } catch (e: Exception) {
            Log.e(TAG, "createTag failed", e)
            sendError("新建失败：${e.message ?: "未知错误"}")
        }
    }

    private fun renameTag(id: Long, newName: String) = viewModelScope.launch {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            sendError("标签名不能为空")
            return@launch
        }
        try {
            val existing = tagRepository.searchTagsByName(trimmed)
                .firstOrNull { it.id != id && it.name.equals(trimmed, ignoreCase = true) }
            if (existing != null) {
                sendError("已存在同名标签「${existing.name}」")
                return@launch
            }
            tagRepository.renameTag(id, trimmed)
            sendInfo("已重命名")
        } catch (e: Exception) {
            Log.e(TAG, "renameTag failed", e)
            sendError("重命名失败：${e.message ?: "未知错误"}")
        }
    }

    private fun setTagColor(id: Long, colorArgb: Long) = viewModelScope.launch {
        try {
            tagRepository.setTagColor(id, colorArgb)
            sendInfo("颜色已更新")
        } catch (e: Exception) {
            Log.e(TAG, "setTagColor failed", e)
            sendError("改色失败：${e.message ?: "未知错误"}")
        }
    }

    private fun setShowDot(id: Long, show: Boolean) = viewModelScope.launch {
        try {
            tagRepository.setTagDotVisible(id, show)
        } catch (e: Exception) {
            Log.e(TAG, "setShowDot failed", e)
            sendError("操作失败：${e.message ?: "未知错误"}")
        }
    }

    private fun forceDelete(id: Long) = viewModelScope.launch {
        try {
            val affected = tagRepository.forceDeleteTag(id)
            sendInfo(if (affected.isEmpty()) "标签已删除" else "已删除，影响 ${affected.size} 个联系人")
        } catch (e: Exception) {
            Log.e(TAG, "forceDelete failed", e)
            sendError("删除失败：${e.message ?: "未知错误"}")
        }
    }

    private fun merge(from: Long, to: Long) = viewModelScope.launch {
        if (from == to) {
            sendError("源标签与目标标签不能相同")
            return@launch
        }
        try {
            tagRepository.reassignTagUsage(from, to)
            sendInfo("已合并")
        } catch (e: Exception) {
            Log.e(TAG, "merge failed", e)
            sendError("合并失败：${e.message ?: "未知错误"}")
        }
    }

    private fun batchSetColor(ids: List<Long>, colorArgb: Long) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        var okCount = 0
        var failCount = 0
        ids.forEach { id ->
            try {
                tagRepository.setTagColor(id, colorArgb)
                okCount++
            } catch (e: Exception) {
                Log.e(TAG, "batchSetColor: id=$id failed", e)
                failCount++
            }
        }
        sendInfo(
            when {
                failCount == 0 -> "已更新 $okCount 个标签颜色"
                okCount == 0 -> "更新失败：$failCount 个"
                else -> "已更新 $okCount 个，$failCount 个失败"
            },
        )
        multiSelect.value = false
        selectedIds.value = emptySet()
    }

    private fun batchDelete(ids: List<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        var totalAffected = 0
        ids.forEach { id ->
            try {
                totalAffected += tagRepository.forceDeleteTag(id).size
            } catch (e: Exception) {
                Log.e(TAG, "batchDelete: id=$id failed", e)
            }
        }
        sendInfo(
            if (totalAffected == 0) "已删除 ${ids.size} 个标签"
            else "已删除 ${ids.size} 个标签，影响 $totalAffected 个联系人",
        )
        multiSelect.value = false
        selectedIds.value = emptySet()
    }

    private suspend fun sendInfo(text: String) = _messages.send(TagManagerMessage.Info(text))
    private suspend fun sendError(text: String) = _messages.send(TagManagerMessage.Error(text))

    private companion object {
        const val TAG = "TagManagerVM"
    }
}
