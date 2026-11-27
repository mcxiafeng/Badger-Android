package top.mcxiafeng.badger.pages.account

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.swipe
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.InvalidationTracker
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
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
import top.mcxiafeng.badger.data.sync.ConflictDifference
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
        compose.onNodeWithText("登录与切换账号").performClick()
        assertThat(navigated).isTrue()
    }

    @Test fun importConfirm() {
        val importer = mockk<WorkspaceImport>()
        val plan = LocalImportPlan("batch", listOf(LocalImportItem("person/source", "联系人 · 本地朋友", "本地备注",
            listOf(ImportOption("skip", "不导入"), ImportOption("new", "新建副本")), "new")))
        coEvery { importer.prepare(any()) } returns plan
        coEvery { importer.confirm(any(), "batch") } returns 1
        val vm = viewModel(remote = true, importer = importer)
        var returned = false
        compose.setContent { MiuixTheme { AccountPage("import", { returned = true }, {}, vm) } }
        compose.onNodeWithText("联系人 · 本地朋友").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("导入所选（1 项）"))
        compose.onNodeWithText("导入所选（1 项）").performClick()
        compose.waitForIdle()
        coVerify(exactly = 1) { importer.confirm(any(), "batch") }
        compose.onNodeWithText("已保存 1 项本地资料副本，原件仍保留").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        assertThat(returned).isTrue()
        coVerify(exactly = 1) { importer.prepare(any()) }
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
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("导入所选（1 项）"))
        compose.onNodeWithText("导入所选（1 项）").performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("重新预览"))
        // Miuix 原生 SmallTopAppBar 在其栏带内吞掉触摸；滚动到顶部的条目落在栏下，改用语义点击。
        compose.onNodeWithText("重新预览").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("联系人 · 新预览"))
        compose.onNodeWithText("联系人 · 新预览").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("导入所选（1 项）"))
        compose.onNodeWithText("导入所选（1 项）").performClick()
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
        coEvery { coordinator.refresh(any(), false) } returns false
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

    @Test fun foregroundStartsOncePerEntryAndCancelsWhenStopped() {
        val scheduler = TestCoroutineScheduler()
        val coordinator = mockk<SyncCoordinator>()
        val tracker = mockk<InvalidationTracker>(relaxed = true)
        val database = mockk<AppDatabase>()
        every { database.invalidationTracker } returns tracker
        every { coordinator.database(any()) } returns database
        var cancelled = 0
        coEvery { coordinator.refresh(any(), false) } coAnswers {
            try { awaitCancellation() } finally { cancelled++ }
        }
        val vm = viewModel(remote = true, coordinator = coordinator, ioDispatcher = StandardTestDispatcher(scheduler), validSession = true)
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MiuixTheme { AccountUiHost({}, vm) }
            }
        }
        scheduler.runCurrent()
        coVerify(exactly = 0) { coordinator.refresh(any(), any()) }

        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        scheduler.runCurrent()
        coVerify(exactly = 1) { coordinator.refresh(any(), false) }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        scheduler.runCurrent()
        coVerify(exactly = 1) { coordinator.refresh(any(), false) }

        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        scheduler.runCurrent()
        assertThat(cancelled).isEqualTo(1)
        verify(exactly = 1) { tracker.removeObserver(any()) }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        scheduler.runCurrent()
        coVerify(exactly = 2) { coordinator.refresh(any(), false) }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.DESTROYED }
        scheduler.runCurrent()
        assertThat(cancelled).isEqualTo(2)
    }

    @Test fun pullingAccountOverviewRequestsRefreshWithoutReplacingContent() {
        val scheduler = TestCoroutineScheduler()
        val coordinator = mockk<SyncCoordinator>()
        val finish = CompletableDeferred<Unit>()
        coEvery { coordinator.refresh(any(), false) } coAnswers { finish.await(); false }
        val vm = viewModel(remote = true, coordinator = coordinator, ioDispatcher = StandardTestDispatcher(scheduler))
        compose.setContent { MiuixTheme { AccountPage("overview", {}, {}, vm) } }
        val list = compose.onNode(hasScrollToIndexAction())
        // Miuix 原生顶栏吞掉栏带内触摸；手势从栏带下方开始，向下拖出刷新。
        list.performTouchInput {
            swipe(start = Offset(centerX, TOP_BAR_CLEAR_PX), end = Offset(centerX, height * 0.9f), durationMillis = 300)
        }
        // miuix 刷新头在弹簧动画完成后才进入 Refreshing 并触发回调，先推进帧时钟。
        compose.waitForIdle()
        scheduler.runCurrent()
        compose.waitForIdle()

        compose.onNodeWithText("正在同步资料").assertIsDisplayed()
        compose.onNodeWithText("同步状态").assertIsDisplayed()
        list.performTouchInput { swipe(start = Offset(centerX, TOP_BAR_CLEAR_PX), end = Offset(centerX, height * 0.9f), durationMillis = 300) }
        compose.waitForIdle()
        scheduler.runCurrent()
        coVerify(exactly = 1) { coordinator.refresh(any(), false) }
        finish.complete(Unit)
        scheduler.runCurrent()
        compose.waitForIdle()
        compose.onNodeWithText("正在同步资料").assertDoesNotExist()
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

    @Test fun switchEntryAndBack() {
        val source = mockk<WorkspaceConflicts>()
        val conflict = ConflictItem("person/one", "朋友甲", listOf(ConflictDifference("称呼", "本机称呼", "云端称呼")), true)
        coEvery { source.list(any()) } returns listOf(conflict)
        val vm = viewModel(remote = true, conflictSource = source)
        var exited = false
        var openedSwitch = false
        compose.setContent { MiuixTheme { AccountPage("overview", { exited = true }, { openedSwitch = true }, vm) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("登录与切换账号"))
        compose.onNodeWithText("登录与切换账号").performClick()
        assertThat(openedSwitch).isTrue()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("冲突处理"))
        compose.onNodeWithText("冲突处理").performClick()
        compose.onNodeWithText("朋友甲").performClick()
        assertThat(vm.state.value.selectedConflict).isEqualTo(conflict.key)
        compose.onNodeWithText("本机称呼").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("朋友甲").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        assertThat(vm.state.value.section).isEqualTo("overview")
        assertThat(exited).isFalse()
        compose.onNodeWithContentDescription("返回").performClick()
        assertThat(exited).isTrue()
    }

    @Test fun emptyConflictsDoNotScroll() {
        val vm = viewModel(remote = true)
        compose.setContent { MiuixTheme { AccountPage("conflicts", {}, {}, vm) } }
        compose.onNodeWithText("没有待处理的冲突").assertIsDisplayed()
        compose.onAllNodes(hasScrollToIndexAction()).assertCountEquals(0)
    }

    @Test fun conflictLoadErrorCanRetry() {
        val source = mockk<WorkspaceConflicts>()
        coEvery { source.list(any()) } throws IllegalStateException("资料读取失败")
        val vm = viewModel(remote = true, conflictSource = source)
        compose.setContent { MiuixTheme { AccountPage("conflicts", {}, {}, vm) } }
        compose.onNodeWithText("资料读取失败").assertExists()
        compose.onNodeWithText("没有待处理的冲突").assertDoesNotExist()
        coEvery { source.list(any()) } returns emptyList()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("没有待处理的冲突").assertIsDisplayed()
    }

    @Test fun skippedImportRequiresASelectionAtLargeFont() {
        val importer = mockk<WorkspaceImport>()
        val item = LocalImportItem("person/source", "联系人 · 本地朋友", "备注与头像",
            listOf(ImportOption("skip", "不导入"), ImportOption("new", "新建副本")), "skip")
        val plan = LocalImportPlan("batch", listOf(item))
        coEvery { importer.prepare(any()) } returns plan
        coEvery { importer.select(any(), item.key, "new") } returns plan.copy(items = listOf(item.copy(selection = "new")))
        val vm = viewModel(remote = true, importer = importer)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT_SCALE)) {
                MiuixTheme { AccountPage("import", {}, {}, vm) }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("导入所选（0 项）"))
        compose.onNodeWithText("导入所选（0 项）").assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("新建副本"))
        compose.onNodeWithText("新建副本").performClick()
        assertThat(vm.state.value.importSelectionCount).isEqualTo(1)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("导入所选（1 项）"))
        compose.onNodeWithText("导入所选（1 项）").assertIsEnabled().assertIsDisplayed()
    }

    @Test fun changedDifferenceClearsChoice() {
        val conflict = mutableStateOf(ConflictItem("person/one", "朋友甲",
            listOf(ConflictDifference("称呼", "本机", "云端")), true))
        compose.setContent { MiuixTheme { ConflictResolution(conflict.value, false) { _, _ -> } } }
        compose.onNodeWithText("分歧项采用本机内容").performClick()
        compose.runOnIdle { conflict.value = conflict.value.copy(differences = listOf(ConflictDifference("称呼", "新本机", "新云端"))) }
        compose.onNodeWithText("确认选择").assertIsNotEnabled()
    }

    @Test fun reenteredConflictRequiresFreshChoice() {
        val source = mockk<WorkspaceConflicts>()
        val conflict = ConflictItem("person/one", "朋友甲", listOf(ConflictDifference("称呼", "本机", "云端")), true)
        coEvery { source.list(any()) } returns listOf(conflict)
        val vm = viewModel(remote = true, conflictSource = source)
        compose.setContent { MiuixTheme { AccountPage("overview", {}, {}, vm) } }
        fun openConflict() {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("冲突处理"))
            compose.onNodeWithText("冲突处理").performClick()
            compose.onNodeWithText("朋友甲").performClick()
        }
        openConflict()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("分歧项采用云端内容"))
        compose.onNodeWithText("分歧项采用云端内容").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        coEvery { source.list(any()) } returns listOf(conflict.copy(
            differences = listOf(ConflictDifference("称呼", "本机", "新的云端内容"))))
        openConflict()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("确认选择"))
        compose.onNodeWithText("确认选择").assertIsNotEnabled()
    }

    @Test fun authorizationStateHidesAddressAndOffersDirectLogin() {
        val vm = viewModel(remote = true, syncProgress = SyncProgress(message = "UNAUTHORIZED", pending = true))
        var reauthenticated = false
        var switched = false
        compose.setContent { MiuixTheme { AccountPage("overview", {}, { switched = true }, vm,
            onReauthenticate = { reauthenticated = true }) } }
        compose.onNodeWithText("需要重新登录").assertExists()
        compose.onNodeWithText("UNAUTHORIZED").assertDoesNotExist()
        compose.onNodeWithText("https://example.test").assertDoesNotExist()
        compose.onNodeWithText("服务连接详情").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("重新登录"))
        compose.onNodeWithText("重新登录").performClick()
        assertThat(reauthenticated).isTrue()
        assertThat(switched).isFalse()
    }

    @Test fun emptyImportIsNormalAndCanBeCheckedAgain() {
        val importer = mockk<WorkspaceImport>()
        coEvery { importer.prepare(any()) } throws IllegalArgumentException("没有可导入的本地资料")
        val plan = LocalImportPlan("new-batch", listOf(LocalImportItem("person/new", "新本地资料", "备注",
            listOf(ImportOption("new", "新建副本")), "new")))
        coEvery { importer.rebuild(any()) } returns plan
        val vm = viewModel(remote = true, importer = importer)
        compose.setContent { MiuixTheme { AccountPage("import", {}, {}, vm) } }
        compose.onNodeWithText("暂时没有可导入的资料").assertIsDisplayed()
        assertThat(vm.state.value.error).isNull()
        compose.onNodeWithText("导入目标").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("https://example.test").assertDoesNotExist()
        compose.onAllNodes(hasScrollToIndexAction()).assertCountEquals(0)
        compose.onNodeWithText("重新检查").performClick()
        compose.onNodeWithText("新本地资料").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("导入所选（1 项）"))
        compose.onNodeWithText("导入所选（1 项）").assertIsEnabled()
        coVerify(exactly = 1) { importer.rebuild(any()) }
        coVerify(exactly = 0) { importer.confirm(any(), any()) }
    }

    @Test fun emptyImportKeepsActionAndTargetApartAtLargeFont() {
        val importer = mockk<WorkspaceImport>()
        coEvery { importer.prepare(any()) } throws IllegalArgumentException("没有可导入的本地资料")
        val vm = viewModel(remote = true, importer = importer)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT_SCALE)) {
                MiuixTheme { AccountPage("import", {}, {}, vm) }
            }
        }
        compose.onNodeWithText("重新检查").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("导入目标").assertIsDisplayed().assertHasNoClickAction()
        val actionBounds = compose.onNodeWithText("重新检查").fetchSemanticsNode().boundsInRoot
        val targetBounds = compose.onNodeWithText("导入目标").fetchSemanticsNode().boundsInRoot
        assertThat(actionBounds.bottom).isAtMost(targetBounds.top)
    }

    @Test fun importReadFailureDoesNotBecomeAnEmptyState() {
        val importer = mockk<WorkspaceImport>()
        coEvery { importer.prepare(any()) } throws IllegalStateException("本地资料读取失败")
        val vm = viewModel(remote = true, importer = importer)
        compose.setContent { MiuixTheme { AccountPage("import", {}, {}, vm) } }
        compose.onNodeWithText("本地资料读取失败").assertExists()
        compose.onNodeWithText("暂时没有可导入的资料").assertDoesNotExist()
        compose.onNodeWithText("重新预览").assertExists()
        assertThat(vm.state.value.importEmpty).isFalse()
    }

    private fun viewModel(remote: Boolean, importer: WorkspaceImport = mockk(relaxed = true),
        coordinator: SyncCoordinator = mockk(), ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        conflictSource: WorkspaceConflicts? = null, syncProgress: SyncProgress = SyncProgress(message = "已同步"),
        validSession: Boolean = false): AccountViewModel {
        val context: Application = RuntimeEnvironment.getApplication()
        val accounts = AccountStore(context)
        if (remote) accounts.activateRemote("server", "user", "epoch", "账号A", "https://example.test")
        every { coordinator.progress } returns MutableStateFlow(mapOf(accounts.current().key to syncProgress))
        val sessions = mockk<AuthSessionStore>()
        val scope = accounts.current()
        val session = if (validSession) AuthSession(requireNotNull(scope.userId), "synthetic",
            serverId = requireNotNull(scope.serverId), dataEpoch = requireNotNull(scope.dataEpoch), baseUrl = scope.baseUrl)
        else null
        every { sessions.sessionFlow } returns MutableStateFlow(session)
        val conflicts = conflictSource ?: mockk<WorkspaceConflicts>().also {
            coEvery { it.list(any()) } returns emptyList()
        }
        val vm = AccountViewModel(accounts, mockk<AuthApi>(relaxed = true), sessions, coordinator, importer, conflicts, ioDispatcher)
        stores.add(ViewModelStore().apply { put("account", vm) })
        return vm
    }

    companion object {
        private const val LARGE_FONT_SCALE = 2f
        // Miuix 原生顶栏吞掉栏带内触摸；手势从栏带下方开始。
        private const val TOP_BAR_CLEAR_PX = 96f
    }
}
