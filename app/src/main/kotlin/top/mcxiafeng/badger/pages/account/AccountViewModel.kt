package top.mcxiafeng.badger.pages.account

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.InvalidationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.sync.BUSINESS_TABLES
import top.mcxiafeng.badger.data.sync.ConflictChangedException
import top.mcxiafeng.badger.data.sync.ConflictItem
import top.mcxiafeng.badger.data.sync.LocalImportPlan
import top.mcxiafeng.badger.data.sync.SyncCoordinator
import top.mcxiafeng.badger.data.sync.SyncProgress
import top.mcxiafeng.badger.data.sync.WorkspaceConflicts
import top.mcxiafeng.badger.data.sync.WorkspaceImport
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.di.IoDispatcher
import top.mcxiafeng.badger.network.auth.AuthApi
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import javax.inject.Inject

@Immutable
data class AccountUiState(
    val name: String = "本地用户",
    val serverAddress: String = "",
    val local: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val section: String = "overview",
    val offerImport: Boolean = false,
    val importPlan: LocalImportPlan? = null,
    val importEmpty: Boolean = false,
    val conflicts: List<ConflictItem> = emptyList(),
    val selectedConflict: String? = null,
) {
    val importSelectionCount: Int
        get() = importPlan?.items?.count { it.selection != WorkspaceImport.SKIP } ?: 0
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountStore,
    private val auth: AuthApi,
    private val sessions: AuthSessionStore,
    private val coordinator: SyncCoordinator,
    private val importer: WorkspaceImport,
    private val conflicts: WorkspaceConflicts,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val scope = accounts.current()
    private val _state = MutableStateFlow(AccountUiState(name = scope.username, serverAddress = scope.baseUrl, local = scope.isLocal))
    val state = _state.asStateFlow()
    val progress = coordinator.progress.map { it[scope.key] ?: SyncProgress(message = if (scope.isLocal) "资料仅保存在本机" else "尚未同步") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncProgress(message = if (scope.isLocal) "资料仅保存在本机" else "尚未同步"))
    val canRefresh = !scope.isLocal
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private var offerHandled = false

    init {
        viewModelScope.launch {
            accounts.scopeFlow.collect { current ->
                if (current.key == scope.key) _state.update { it.copy(name = current.username, serverAddress = current.baseUrl) }
            }
        }
    }

    internal suspend fun observeForegroundSync() = withContext(ioDispatcher) {
        if (scope.isLocal) return@withContext
        combine(accounts.scopeFlow, sessions.sessionFlow) { current, session ->
            current.takeIf { it.key == scope.key }?.let { it.baseUrl to session }
        }.distinctUntilChanged().collectLatest { binding ->
            if (binding == null) return@collectLatest
            val (baseUrl, session) = binding
            Log.d(TAG, "前台触发当前账号同步")
            if (session?.serverId == scope.serverId && session?.userUuid == scope.userId &&
                session?.dataEpoch == scope.dataEpoch && session?.baseUrl == baseUrl) {
                observeLocalChanges()
            } else {
                // The coordinator can report a missing session without sending a request.
                syncInPasses()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeLocalChanges() {
        val changes = Channel<Unit>(Channel.CONFLATED)
        val observer = object : InvalidationTracker.Observer(BUSINESS_TABLES) {
            override fun onInvalidated(tables: Set<String>) { changes.trySend(Unit) }
        }
        val tracker = try {
            coordinator.database(scope).invalidationTracker.also { it.addObserver(observer) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "无法观察本机资料变更；type=${e.javaClass.simpleName}")
            syncInPasses()
            return
        }
        try {
            syncInPasses()
            changes.receiveAsFlow().debounce(SYNC_DEBOUNCE_MS).conflate().collect {
                syncInPasses(onlyIfChanged = true)
            }
        } finally {
            tracker.removeObserver(observer)
            changes.cancel()
        }
    }

    private suspend fun syncInPasses(onlyIfChanged: Boolean = false) {
        var more = coordinator.refresh(scope, onlyIfChanged)
        while (more && accounts.current().key == scope.key) {
            delay(CONTINUATION_DELAY_MS)
            more = coordinator.refresh(scope, onlyIfChanged = true)
        }
    }

    fun open(section: String) {
        require(section in setOf("overview", "import", "conflicts"))
        Log.d(TAG, "打开账号任务：$section")
        _state.update { it.copy(section = section, selectedConflict = null, error = null, message = null,
            importEmpty = if (section == "import") false else it.importEmpty,
            importPlan = if (section == "import") null else it.importPlan) }
        action {
            when (section) {
                "import" -> loadImportPlan(rebuild = false)
                "conflicts" -> refreshConflicts()
            }
        }
    }

    fun synchronize() = requestSync()

    internal fun requestSync(onFinished: (SyncProgress) -> Unit = {}) {
        if (!canRefresh || accounts.current().key != scope.key) return
        action {
            Log.d(TAG, "用户请求同步当前空间")
            _refreshing.value = true
            try {
                withContext(ioDispatcher) { syncInPasses() }
                if (_state.value.section == "conflicts") refreshConflicts()
                if (accounts.current().key == scope.key) coordinator.progress.value[scope.key]?.let(onFinished)
            } finally {
                _refreshing.value = false
            }
        }
    }
    fun selectImport(key: String, choice: String) = action {
        Log.d(TAG, "更新导入选择，跳过=${choice == WorkspaceImport.SKIP}")
        val plan = importer.select(scope, key, choice)
        _state.update { it.copy(importPlan = plan) }
    }
    fun rebuildImport() = action { loadImportPlan(rebuild = true) }

    private suspend fun loadImportPlan(rebuild: Boolean) {
        try {
            val plan = if (rebuild) importer.rebuild(scope) else importer.prepare(scope)
            _state.update { it.copy(importPlan = plan, importEmpty = plan.items.isEmpty(),
                message = if (rebuild && plan.items.isNotEmpty()) "预览已更新" else null) }
            Log.d(TAG, "导入预览已显示，empty=${plan.items.isEmpty()}")
        } catch (e: IllegalArgumentException) {
            if (e.message != EMPTY_IMPORT_MESSAGE) throw e
            // 只将现有检查明确返回的“无资料”呈现为空态，其他失败继续进入错误反馈。
            Log.d(TAG, "本地暂无可导入资料，显示正常空态")
            _state.update { it.copy(importPlan = null, importEmpty = true, message = null) }
        }
    }
    fun confirmImport() = action {
        val plan = requireNotNull(_state.value.importPlan)
        Log.d(TAG, "确认导入所选资料，selected=${_state.value.importSelectionCount}")
        val count = importer.confirm(scope, plan.id)
        refreshConflicts()
        _state.update { it.copy(importPlan = null, section = "overview", message = "已保存 $count 项本地资料副本，原件仍保留") }
    }
    fun showConflict(key: String?) {
        Log.d(TAG, "切换冲突详情：selected=${key != null}")
        _state.update { it.copy(selectedConflict = key, error = null) }
    }
    fun resolveConflict(item: ConflictItem, preferLocal: Boolean, both: Boolean = false) = action {
        Log.d(TAG, "提交冲突选择，preferLocal=$preferLocal both=$both")
        try {
            conflicts.resolve(scope, item, preferLocal, both)
        } catch (e: ConflictChangedException) {
            refreshConflicts()
            Log.d(TAG, "旧裁决未提交，已重新读取冲突列表")
            _state.update { it.copy(selectedConflict = null, error = e.message) }
            return@action
        }
        refreshConflicts()
        _state.update { it.copy(selectedConflict = null, message = "冲突选择已保存") }
    }
    fun logout() = action { auth.logout(expectedScopeKey = scope.key) }

    fun dismissOffer() {
        offerHandled = true
        _state.update { it.copy(offerImport = false) }
        viewModelScope.launch(ioDispatcher) {
            try { importer.dismissOffer(scope) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "保存本地导入询问状态失败", e) }
        }
    }

    internal suspend fun refreshImportOffer() {
        if (scope.isLocal || offerHandled) return
        try {
            val offer = withContext(ioDispatcher) { importer.shouldOffer(scope) }
            if (!offerHandled && accounts.current().key == scope.key) _state.update { it.copy(offerImport = offer) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.e(TAG, "读取本地导入询问状态失败，原件保留", e) }
    }
    private suspend fun refreshConflicts() {
        val items = conflicts.list(scope)
        _state.update { it.copy(conflicts = items) }
    }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) {
            Log.d(TAG, "账号任务仍在执行，忽略重复操作")
            return
        }
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "账号资料操作失败", e)
                _state.update { it.copy(error = e.message ?: "操作失败，请重试") }
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    companion object {
        private const val TAG = "AccountViewModelTester"
        private const val EMPTY_IMPORT_MESSAGE = "没有可导入的本地资料"
        private const val SYNC_DEBOUNCE_MS = 800L
        private const val CONTINUATION_DELAY_MS = 500L
    }
}
