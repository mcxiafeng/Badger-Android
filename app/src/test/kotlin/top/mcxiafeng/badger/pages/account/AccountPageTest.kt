package top.mcxiafeng.badger.pages.account

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.room.InvalidationTracker
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.data.sync.ConflictItem
import top.mcxiafeng.badger.data.sync.ImportOption
import top.mcxiafeng.badger.data.sync.LocalImportItem
import top.mcxiafeng.badger.data.sync.LocalImportPlan
import top.mcxiafeng.badger.data.sync.SyncCoordinator
import top.mcxiafeng.badger.data.sync.SyncProgress
import top.mcxiafeng.badger.data.sync.WorkspaceConflicts
import top.mcxiafeng.badger.data.sync.WorkspaceImport
import top.mcxiafeng.badger.network.auth.AuthApi
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountPageTest {
    @get:Rule val compose = createComposeRule()
    private val stores = mutableListOf<ViewModelStore>()
    @After fun close() { stores.forEach { it.clear() } }

    @Test fun localLogin() {
        val vm = viewModel(remote = false)
        var navigated = false
        compose.setContent { MiuixTheme { AccountPage("overview", {}, { navigated = true }, vm) } }
        compose.onNodeWithText("本地用户").assertExists()
        compose.onNodeWithText("登录云端账号").performClick()
        assertThat(navigated).isTrue()
    }

    @Test fun importConfirm() {
        val importer = mockk<WorkspaceImport>()
        val plan = LocalImportPlan("batch", listOf(LocalImportItem("person/source", "联系人 · 本地朋友", "本地备注",
            listOf(ImportOption("skip", "不导入"), ImportOption("new", "新建副本")), "new")))
        coEvery { importer.prepare(any()) } returns plan
        coEvery { importer.confirm(any(), "batch") } returns 1
        val vm = viewModel(remote = true, importer = importer)
        compose.setContent { MiuixTheme { AccountPage("import", {}, {}, vm) } }
        compose.onNodeWithText("联系人 · 本地朋友").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("确认导入"))
        compose.onNodeWithText("确认导入").performClick()
        compose.waitForIdle()
        coVerify(exactly = 1) { importer.confirm(any(), "batch") }
        compose.onNodeWithText("已保存 1 项本地资料副本，原件仍保留").assertExists()
    }

    @Test fun stalePreviewRebuild() {
        val importer = mockk<WorkspaceImport>()
        val old = LocalImportPlan("old", listOf(LocalImportItem("person/source", "联系人 · 旧预览", "备注",
            listOf(ImportOption("new", "新建副本")), "new")))
        val fresh = old.copy(id = "fresh", items = old.items.map { it.copy(title = "联系人 · 新预览") })
        coEvery { importer.prepare(any()) } returns old
        coEvery { importer.confirm(any(), "old") } throws IllegalArgumentException("云端资料已有更新，请重新建立导入预览")
        coEvery { importer.rebuild(any()) } returns fresh
        coEvery { importer.confirm(any(), "fresh") } returns 1
        val vm = viewModel(remote = true, importer = importer)
        compose.setContent { MiuixTheme { AccountPage("import", {}, {}, vm) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("确认导入"))
        compose.onNodeWithText("确认导入").performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("重新建立预览"))
        compose.onNodeWithText("重新建立预览").performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("联系人 · 新预览"))
        compose.onNodeWithText("联系人 · 新预览").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("确认导入"))
        compose.onNodeWithText("确认导入").performClick()
        compose.waitForIdle()
        coVerify(exactly = 1) { importer.rebuild(any()) }
        coVerify(exactly = 1) { importer.confirm(any(), "fresh") }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun loginImportPrompt() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val importer = mockk<WorkspaceImport>()
        coEvery { importer.shouldOffer(any()) } returns true
        coEvery { importer.dismissOffer(any()) } returns Unit
        val coordinator = mockk<SyncCoordinator>()
        val database = mockk<AppDatabase>()
        every { database.invalidationTracker } returns mockk<InvalidationTracker>(relaxed = true)
        every { coordinator.database(any()) } returns database
        coEvery { coordinator.synchronize(any()) } returns Unit
        val vm = viewModel(remote = true, importer = importer, coordinator = coordinator, ioDispatcher = dispatcher)
        var opened = false
        compose.setContent { MiuixTheme { AccountUiHost(onImport = { opened = true }, viewModel = vm) } }
        scheduler.advanceUntilIdle()
        compose.waitForIdle()
        assertThat(vm.state.value.offerImport).isTrue()
        compose.onNodeWithText("导入本地资料？").assertExists()
        compose.onNodeWithText("查看资料").performClick()
        scheduler.advanceUntilIdle()
        compose.waitForIdle()
        coVerify(exactly = 1) { importer.dismissOffer(any()) }
        assertThat(opened).isTrue()
        assertThat(vm.state.value.offerImport).isFalse()
        compose.onNodeWithText("导入本地资料？").assertDoesNotExist()
    }

    @Test fun relationGuard() {
        var adopted: Pair<Boolean, Boolean>? = null
        val item = ConflictItem("field/id", "公司", emptyList(), canKeepBoth = false, canAdoptLocal = false,
            notice = "云端仍有联系人使用此字段")
        compose.setContent { MiuixTheme { ConflictResolution(item, false) { local, both -> adopted = local to both } } }
        compose.onNodeWithText("分歧项采用本机内容").assertIsNotEnabled()
        compose.onNodeWithText("确认选择").assertIsNotEnabled()
        compose.onNodeWithText("分歧项采用云端内容").performClick()
        compose.onNodeWithText("确认选择").performClick()
        assertThat(adopted).isEqualTo(false to false)
    }

    private fun viewModel(remote: Boolean, importer: WorkspaceImport = mockk(relaxed = true),
        coordinator: SyncCoordinator = mockk(), ioDispatcher: CoroutineDispatcher = Dispatchers.IO): AccountViewModel {
        val context: Application = RuntimeEnvironment.getApplication()
        val accounts = AccountStore(context)
        if (remote) accounts.activateRemote("server", "user", "epoch", "账号A", "https://example.test")
        every { coordinator.progress } returns MutableStateFlow(mapOf(accounts.current().key to SyncProgress(message = "已同步")))
        val sessions = mockk<AuthSessionStore>()
        every { sessions.sessionFlow } returns MutableStateFlow<AuthSession?>(null)
        val conflicts = mockk<WorkspaceConflicts>()
        coEvery { conflicts.list(any()) } returns emptyList()
        val vm = AccountViewModel(accounts, mockk<AuthApi>(relaxed = true), sessions, coordinator, importer, conflicts, ioDispatcher)
        stores.add(ViewModelStore().apply { put("account", vm) })
        return vm
    }
}
