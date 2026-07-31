package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.testutil.MainDispatcherRule

/**
 * [§16] CloudBackupViewModel 测试。
 *
 * 覆盖 3 类契约：
 * 1. `refresh()` 正常路径 → uiState.items.size == 2
 * 2. `delete(id)` 成功 → 从 items 移除对应项
 * 3. `delete(id)` 服务端 5xx → uiState.error != null, items 不变
 *
 * 通过 Koin 注入 mock ServerApiFactory，ServerApi mock 透传到 VM 字段 `serverApiFactory`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CloudBackupViewModelTest {

    @get:org.junit.Rule
    val dispatcherRule = MainDispatcherRule()

    private val testDispatcher get() = dispatcherRule.testDispatcher

    private lateinit var serverApiFactory: ServerApiFactory
    private lateinit var serverApi: ServerApi

    private val sampleItems = listOf(
        ServerApi.BackupSummary(
            id = "b1",
            name = "auto-2026-08-01",
            size = 1024 * 1024L,
            createdAt = "2026-08-01T10:00:00Z",
        ),
        ServerApi.BackupSummary(
            id = "b2",
            name = "auto-2026-08-02",
            size = 2 * 1024 * 1024L,
            createdAt = "2026-08-02T10:00:00Z",
        ),
    )

    @Before
    fun setUp() {
        // [修复防御]: VM 内 viewModelScope.launch → withContext(Dispatchers.IO)。
        // 用 UnconfinedTestDispatcher 把 Main + IO 同时同步推进,让 refresh/delete
        // 块在协程调度上等价于同步运行,断言时 uiState.value 一定是终态。
        Dispatchers.setMain(UnconfinedTestDispatcher())
        serverApi = mockk(relaxed = true)
        serverApiFactory = mockk(relaxed = true) {
            io.mockk.every { get() } returns serverApi
        }
        // [§14.2] 为 ViewModel 注入 mock 依赖(GlobalContext.startKoin)。
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { serverApiFactory }
                },
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { GlobalContext.stopKoin() }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CloudBackupViewModel =
        CloudBackupViewModel(dispatcher = kotlinx.coroutines.Dispatchers.Unconfined)

    // ========== 1. refresh 正常路径 ==========

    @Test
    fun `refresh loads list and populates items`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { serverApi.listBackups() } returns sampleItems
        val vm = createViewModel()

        vm.refresh()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.loading).isFalse()
        assertThat(s.error).isNull()
        assertThat(s.items).hasSize(2)
        assertThat(s.items[0].id).isEqualTo("b1")
        assertThat(s.items[1].id).isEqualTo("b2")
        coVerify(exactly = 1) { serverApi.listBackups() }
    }

    // ========== 2. delete 成功 ==========

    @Test
    fun `delete removes the matching item on success`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { serverApi.listBackups() } returns sampleItems
        coEvery { serverApi.deleteBackup("b1") } returns true
        val vm = createViewModel()

        vm.refresh()
        advanceUntilIdle()
        assertThat(vm.uiState.value.items).hasSize(2)

        vm.delete("b1")
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.deletingId).isNull()
        assertThat(s.error).isNull()
        assertThat(s.items.map { it.id }).containsExactly("b2")
        coVerify(exactly = 1) { serverApi.deleteBackup("b1") }
    }

    // ========== 3. delete 服务端失败 ==========

    @Test
    fun `delete failure writes error and keeps items`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { serverApi.listBackups() } returns sampleItems
        coEvery { serverApi.deleteBackup("b2") } throws ApiException(
            status = 500,
            bodyText = "boom",
            what = "backups.delete",
        )
        val vm = createViewModel()

        vm.refresh()
        advanceUntilIdle()
        assertThat(vm.uiState.value.items).hasSize(2)

        vm.delete("b2")
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.deletingId).isNull()
        assertThat(s.error).isNotNull()
        assertThat(s.items).hasSize(2)
        coVerify(exactly = 1) { serverApi.deleteBackup("b2") }
    }

    // ========== 4. delete 404 视为幂等成功 ==========

    @Test
    fun `delete 404 is treated as idempotent success and removes item`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { serverApi.listBackups() } returns sampleItems
        // 真实 ApiException 404 在 ServerApi.deleteBackup 内被吃掉并返 true;
        // 这里模拟的就是被吃掉后的行为,VM 不应感知 404。
        coEvery { serverApi.deleteBackup("b1") } returns true
        val vm = createViewModel()

        vm.refresh()
        advanceUntilIdle()

        vm.delete("b1")
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.error).isNull()
        assertThat(s.items.map { it.id }).containsExactly("b2")
    }

    // ========== 5. clearError 复位 transient error ==========

    @Test
    fun `clearError resets transient error`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { serverApi.listBackups() } throws ApiException(
            status = 503,
            bodyText = "down",
            what = "backups.list",
        )
        val vm = createViewModel()

        vm.refresh()
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isNotNull()

        vm.clearError()

        assertThat(vm.uiState.value.error).isNull()
    }
}