package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.SyncStatusRepository

/**
 * [V2-P9] SyncStatusPage 的 ViewModel。
 *
 * 设计:
 * - 持久状态走 [uiState]: `StateFlow<SyncStatusUiState>`,viewModelScope 内 `stateIn`,
 *   旋转屏 / 深色模式切换不丢。
 * - 瞬时反馈走 [messages]: `Channel<SyncStatusMessage>`,Composable `LaunchedEffect` 收
 *   `snackbarHostState.showSnackbar`。
 * - [uiState] 内部 `flatMapLatest(refreshTrigger)` —— 下拉刷新 / 副作用后 emit 一个
 *   trigger,VM 重新读 snapshot + batteryOptimized。
 * - 副作用(retryAll / purgeFinished)通过 [onEvent] 转发 Repository。
 *
 * **传感器信号**:
 * `batteryOptimized` 不是 Flow 来源 —— Android `PowerManager.isIgnoringBatteryOptimizations`
 * 是同步方法,只在 VM 内部读,放在每次 snapshot 重读的同时读一次。
 *
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin `inject()` 字段注入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModel : ViewModel() {

    private val context: Context = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val repository: SyncStatusRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val tag = TAG

    /**
     * 刷新触发器。每次 VM.onEvent(Refresh / RetryAll / PurgeFinished) 完都 emit,
     * 让 uiState 重新订阅底层 snapshot + batteryOptimized 组合。
     *
     * 用 [MutableStateFlow] 是为了 `flatMapLatest` 在 trigger 变化时取消上游订阅;
     * 首次值 0L 让 stateIn 立刻 emit Loading 兜底,避免 null pointer。
     */
    private val refreshTrigger = MutableStateFlow(0L)

    val uiState: StateFlow<SyncStatusUiState> = refreshTrigger
        .flatMapLatest {
            flow {
                emit(SyncStatusUiState.Loading)
                try {
                    val snapshot = repository.snapshot()
                    val batteryOptimized = readBatteryOptimized()
                    emit(SyncStatusUiState.Success(snapshot, batteryOptimized))
                } catch (e: Exception) {
                    Log.e(tag, "uiState: 读 snapshot/battery 失败", e)
                    emit(SyncStatusUiState.Error(e.message ?: "加载失败"))
                }
            }
        }
        .catch { e ->
            Log.e(tag, "uiState: catch 外层异常(理论上不会触发,内层已 catch)", e)
            emit(SyncStatusUiState.Error(e.message ?: "加载失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SyncStatusUiState.Loading,
        )

    private val _messages = Channel<SyncStatusMessage>(Channel.BUFFERED)
    val messages: Flow<SyncStatusMessage> = _messages.receiveAsFlow()

    fun onEvent(event: SyncStatusEvent) {
        Log.d(tag, "onEvent: $event")
        when (event) {
            SyncStatusEvent.Refresh -> triggerRefresh()
            SyncStatusEvent.RetryAll -> retryAll()
            SyncStatusEvent.PurgeFinished -> purgeFinished()
            is SyncStatusEvent.RetryOne -> {
                // P9 UI 不暴露单条 retry;P10 批量撤销复用
                viewModelScope.launch {
                    val ok = repository.retryOne(event.opId)
                    val msg = if (ok) "历史失败项(队列已退役,不再自动重试)" else "无法重试(op 已变更)"
                    _messages.send(SyncStatusMessage.Info(msg))
                    triggerRefresh()
                }
            }
            SyncStatusEvent.DismissMessage -> {
                // Channel 是 hot 流,用户主动 dismiss 通过 SnackbarDuration 控制,这里 no-op
                Log.d(tag, "DismissMessage: no-op(由 Snackbar duration 控制)")
            }
        }
    }

    private fun triggerRefresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    private fun retryAll() {
        viewModelScope.launch {
            // [Phase 3] 队列退役:retryAll 触发一次增量同步
            val count = repository.retryAll()
            val msg = if (count == 0) {
                "已触发增量同步(无新增变更)"
            } else {
                "已触发增量同步,应用 $count 条变更"
            }
            _messages.send(SyncStatusMessage.Info(msg))
            Log.d(tag, "retryAll: applied=$count")
            triggerRefresh()
        }
    }

    private fun purgeFinished() {
        viewModelScope.launch {
            val deleted = repository.purgeFinished()
            val msg = if (deleted == 0) {
                "没有可清理的历史"
            } else {
                "已清理 $deleted 条已完成记录"
            }
            _messages.send(SyncStatusMessage.Info(msg))
            Log.d(tag, "purgeFinished: deleted=$deleted")
            triggerRefresh()
        }
    }

    /**
     * 读系统 battery_optimizations 白名单状态。
     *
     * [修复防御]: API 仅 Android 6.0+ (API 23) 才有,API<23 时永远返 true
     * (Android 6.0 之前没有 doze 概念,默认未受限)。RuntimeException
     * (罕见 OEM 抛 WrongStateException 等) catch + 兜底返 false 让用户进设置页。
     */
    private fun readBatteryOptimized(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // API<23: 没有 doze,无需关注,默认"已优化"= true(隐藏引导)
            return true
        }
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (e: Exception) {
            Log.w(tag, "readBatteryOptimized: 读 isIgnoringBatteryOptimizations 失败,fallback false", e)
            false
        }
    }

    private companion object {
        const val TAG = "SyncStatusVM"
    }
}
