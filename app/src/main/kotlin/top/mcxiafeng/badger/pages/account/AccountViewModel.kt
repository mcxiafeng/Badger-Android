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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.sync.BUSINESS_TABLES
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
    val conflicts: List<ConflictItem> = emptyList(),
    val selectedConflict: String? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    accounts: AccountStore,
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
    private var automaticJob: Job? = null
    private var offerHandled = false

    init {
        viewModelScope.launch {
            accounts.scopeFlow.collect { current ->
                if (current.key == scope.key) _state.update { it.copy(name = current.username, serverAddress = current.baseUrl) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun startAutomaticSync() {
        if (scope.isLocal || automaticJob != null) return
        automaticJob = viewModelScope.launch(ioDispatcher) {
            val database = coordinator.database(scope)
            callbackFlow {
                val observer = object : InvalidationTracker.Observer(BUSINESS_TABLES) {
                    override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
                }
                database.invalidationTracker.addObserver(observer)
                val sessionJob = launch { sessions.sessionFlow.collect { trySend(Unit) } }
                trySend(Unit)
                awaitClose { sessionJob.cancel(); database.invalidationTracker.removeObserver(observer) }
            }.conflate().debounce(SYNC_DEBOUNCE_MS).collect {
                do {
                    coordinator.synchronize(scope)
                    val more = coordinator.progress.value[scope.key]?.canContinue == true
                    if (more) delay(CONTINUATION_DELAY_MS)
                } while (more)
                refreshOffer()
            }
        }
        Log.d(TAG, "已启动固定用户空间的前台同步观察")
    }

    fun open(section: String) {
        require(section in setOf("overview", "import", "conflicts"))
        _state.update { it.copy(section = section, selectedConflict = null, error = null, message = null) }
        action {
            when (section) {
                "import" -> {
                    val plan = importer.prepare(scope)
                    _state.update { it.copy(importPlan = plan) }
                }
                "conflicts" -> refreshConflicts()
            }
        }
    }

    fun synchronize() = action { coordinator.synchronize(scope); refreshConflicts() }
    fun selectImport(key: String, choice: String) = action {
        val plan = importer.select(scope, key, choice)
        _state.update { it.copy(importPlan = plan) }
    }
    fun rebuildImport() = action {
        val plan = importer.rebuild(scope)
        _state.update { it.copy(importPlan = plan, message = "已重新建立预览，请核对并确认选择") }
    }
    fun confirmImport() = action {
        val plan = requireNotNull(_state.value.importPlan)
        val count = importer.confirm(scope, plan.id)
        refreshConflicts()
        _state.update { it.copy(importPlan = null, section = "overview", message = "已保存 $count 项本地资料副本，原件仍保留") }
    }
    fun showConflict(key: String?) { _state.update { it.copy(selectedConflict = key, error = null) } }
    fun resolveConflict(key: String, preferLocal: Boolean, both: Boolean = false) = action {
        conflicts.resolve(scope, key, preferLocal, both)
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

    private suspend fun refreshOffer() {
        if (offerHandled) return
        try {
            val offer = importer.shouldOffer(scope)
            _state.update { it.copy(offerImport = offer) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.e(TAG, "读取本地导入询问状态失败，原件保留", e) }
    }
    private suspend fun refreshConflicts() {
        val items = conflicts.list(scope)
        _state.update { it.copy(conflicts = items) }
    }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
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
        private const val SYNC_DEBOUNCE_MS = 800L
        private const val CONTINUATION_DELAY_MS = 500L
    }
}
