package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.repository.TagRepository
import javax.inject.Inject

/**
 * 标签管理页 ViewModel。
 *
 * 设计要点（按 NowInAndroid 模式）：
 * - 持久状态走 [uiState] : `StateFlow<TagManagerUiState>`，
 *   viewModelScope 内 `stateIn` 保证旋转屏 / 切深色模式不丢状态。
 * - 瞬时反馈走 [messages] : `Channel<TagManagerMessage>`，
 *   Composable 用 `LaunchedEffect` 收集后调 `SnackbarHostState.showSnackbar`。
 * - 数据来源 + 本地 UI 控制状态（filter / sort / 多选）通过 `combine` 合并为单一 Success StateFlow。
 * - 所有写入成功会自动通过 `tagRepository.observeAllTags()` 重发，无需手动刷新。
 *
 * 与旧实现的差异：旧 `TagManagerSettingsViewModel` 只为透出 Repository 而存在；
 * 本次重写让它真正承担状态机角色。
 */
@HiltViewModel
class TagManagerSettingsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val tagsFlow: Flow<List<Tag>> = tagRepository.observeAllTags()

    private val filterMode = MutableStateFlow(TagFilterMode.All)
    private val sortMode = MutableStateFlow(TagSortMode.Alphabetical)
    private val multiSelect = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

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
        ) as TagManagerUiState
    }
        .catch { e ->
            Log.e(TAG, "observeAllTags failed", e)
            val errorState: TagManagerUiState = TagManagerUiState.Error(e.message ?: "加载失败")
            emit(errorState)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TagManagerUiState.Loading,
        )

    private val _messages = Channel<TagManagerMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onEvent(event: TagManagerEvent) {
        Log.d(TAG, "onEvent: $event")
        when (event) {
            is TagManagerEvent.ChangeFilter -> filterMode.value = event.mode
            is TagManagerEvent.ChangeSort -> sortMode.value = event.mode

            is TagManagerEvent.EnterMultiSelect -> {
                multiSelect.value = true
                val cur = selectedIds.value
                val initial = event.initialSelectedId
                selectedIds.value = if (initial != null) cur + initial else cur
            }
            TagManagerEvent.ExitMultiSelect -> {
                multiSelect.value = false
                selectedIds.value = emptySet()
            }
            is TagManagerEvent.ToggleSelect -> {
                val cur = selectedIds.value
                selectedIds.value = if (event.tagId in cur) cur - event.tagId else cur + event.tagId
            }
            TagManagerEvent.SelectAll -> {
                val s = uiState.value
                if (s is TagManagerUiState.Success) {
                    selectedIds.value = s.visibleTags.map { it.id }.toSet()
                }
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
            TagManagerEvent.Refresh -> Unit
        }
    }

    // ========== 业务操作 ==========

    private fun createTag(name: String, colorArgb: Long) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            sendError("标签名不能为空")
            return@launch
        }
        try {
            tagRepository.upsertTag(trimmed, color = colorArgb, source = "manual")
            Log.d(TAG, "createTag success: '$trimmed'")
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
            // 重复名校验：renameTag 由 unique 索引兜底，但要先发友好错误。
            val existing = tagRepository.searchTagsByName(trimmed)
                .firstOrNull { it.id != id && it.name.equals(trimmed, ignoreCase = true) }
            if (existing != null) {
                sendError("已存在同名标签「${existing.name}」")
                return@launch
            }
            tagRepository.renameTag(id, trimmed)
            Log.d(TAG, "renameTag success: id=$id newName='$trimmed'")
            sendInfo("已重命名")
        } catch (e: Exception) {
            Log.e(TAG, "renameTag failed", e)
            sendError("重命名失败：${e.message ?: "未知错误"}")
        }
    }

    private fun setTagColor(id: Long, colorArgb: Long) = viewModelScope.launch {
        try {
            tagRepository.setTagColor(id, colorArgb)
            Log.d(TAG, "setTagColor success: id=$id color=0x${colorArgb.toString(16)}")
            sendInfo("颜色已更新")
        } catch (e: Exception) {
            Log.e(TAG, "setTagColor failed", e)
            sendError("改色失败：${e.message ?: "未知错误"}")
        }
    }

    private fun setShowDot(id: Long, show: Boolean) = viewModelScope.launch {
        try {
            tagRepository.setTagDotVisible(id, show)
            Log.d(TAG, "setShowDot success: id=$id show=$show")
        } catch (e: Exception) {
            Log.e(TAG, "setShowDot failed", e)
            sendError("操作失败：${e.message ?: "未知错误"}")
        }
    }

    private fun forceDelete(id: Long) = viewModelScope.launch {
        try {
            val affected = tagRepository.forceDeleteTag(id)
            Log.d(TAG, "forceDelete success: id=$id affected=${affected.size}")
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
            Log.d(TAG, "merge success: from=$from to=$to")
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
        Log.d(TAG, "batchSetColor done: ok=$okCount fail=$failCount")
        sendInfo(
            when {
                failCount == 0 -> "已更新 $okCount 个标签颜色"
                okCount == 0 -> "更新失败：$failCount 个"
                else -> "已更新 $okCount 个，$failCount 个失败"
            }
        )
        multiSelect.value = false
        selectedIds.value = emptySet()
    }

    private fun batchDelete(ids: List<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        var totalAffected = 0
        ids.forEach { id ->
            try {
                val affected = tagRepository.forceDeleteTag(id)
                totalAffected += affected.size
            } catch (e: Exception) {
                Log.e(TAG, "batchDelete: id=$id failed", e)
            }
        }
        Log.d(TAG, "batchDelete done: deleted=${ids.size} affectedContacts=$totalAffected")
        sendInfo(
            if (totalAffected == 0) "已删除 ${ids.size} 个标签"
            else "已删除 ${ids.size} 个标签，影响 $totalAffected 个联系人"
        )
        multiSelect.value = false
        selectedIds.value = emptySet()
    }

    // ========== 消息发送 ==========

    private suspend fun sendInfo(text: String) {
        _messages.send(TagManagerMessage.Info(text))
    }

    private suspend fun sendError(text: String) {
        _messages.send(TagManagerMessage.Error(text))
    }

    private companion object {
        const val TAG = "TagManagerVM"
    }
}
