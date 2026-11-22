package top.mcxiafeng.badger.pages.server

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.network.core.ServerAddress
import top.mcxiafeng.badger.network.core.ServerAddressStore
import top.mcxiafeng.badger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAddressesViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var addressStore: ServerAddressStore
    private lateinit var publicApi: PublicApi
    private lateinit var viewModel: ServerAddressesViewModel

    private val addressesFlow = MutableStateFlow<List<ServerAddress>>(emptyList())
    private val activeUrlFlow = MutableStateFlow("http://10.0.0.1:8080")
    private val configuredUrlFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        addressStore = mockk(relaxed = true)
        publicApi = mockk(relaxed = true)
        every { addressStore.addresses } returns addressesFlow
        every { addressStore.activeUrl } returns activeUrlFlow
        every { addressStore.configuredUrl } returns configuredUrlFlow
        every { publicApi.lastSuccessBase } returns "https://other.example.com:51026"
        viewModel = ServerAddressesViewModel(addressStore, publicApi)
    }

    private fun pushAddresses(vararg urls: String) {
        addressesFlow.value = urls.map { ServerAddress(url = it) }
    }

    // —— 列表观察与自动测试 ——

    @Test
    fun activeBase_tracksConfiguredUrlWithoutAnAddressList() = runTest {
        val selectedUrl = "https://selected.example.com:51026"
        configuredUrlFlow.value = selectedUrl
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeBase).isEqualTo(selectedUrl)
        assertThat(viewModel.uiState.value.servers).isEmpty()

        val changedUrl = "https://changed.example.com:51026"
        configuredUrlFlow.value = changedUrl
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeBase).isEqualTo(changedUrl)
    }

    @Test
    fun newAddressesAutoTest_andSuccessMarksReachable() = runTest {
        coEvery { publicApi.getDirect("http://a", PublicApi.INFO_PATH) } returns HttpResult.Success("{}")

        pushAddresses("http://a")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.testResults["http://a"]?.success).isTrue()
        verify { publicApi.recordSuccessBase("http://a") }
    }

    @Test
    fun autoTest_networkFailure_mapsMessage() = runTest {
        coEvery { publicApi.getDirect(any(), any()) } returns
            HttpResult.Failure(0, null, null, HttpResult.ErrorType.NETWORK)

        pushAddresses("http://down")
        advanceUntilIdle()

        val result = viewModel.uiState.value.testResults["http://down"]
        assertThat(result?.success).isFalse()
        assertThat(result?.message).isEqualTo("无法连接")
    }

    @Test
    fun autoTest_httpFailureReportsCode() = runTest {
        coEvery { publicApi.getDirect(any(), any()) } returns
            HttpResult.Failure(503, "x", null, HttpResult.ErrorType.SERVER)

        pushAddresses("http://err")
        advanceUntilIdle()

        val result = viewModel.uiState.value.testResults["http://err"]
        assertThat(result?.success).isFalse()
        assertThat(result?.message).isEqualTo("HTTP 503")
    }

    // —— 增改删与去重 ——

    @Test
    fun addAddress_rejectsDuplicateCaseInsensitive() = runTest {
        pushAddresses("http://a:8080")
        advanceUntilIdle()

        val error = viewModel.addAddress("别名", "HTTP://A:8080", https = false)
        advanceUntilIdle()

        assertThat(error).isEqualTo("该地址已存在")
        verify(exactly = 0) { addressStore.setAddresses(any()) }
    }

    @Test
    fun addAddress_appendsWithDefaultPort() = runTest {
        pushAddresses("http://a")
        advanceUntilIdle()

        val error = viewModel.addAddress("主站", "b.example.com", https = true)
        advanceUntilIdle()

        assertThat(error).isNull()
        // 添加原样追加队尾，不覆盖已有地址
        verify {
            addressStore.setAddresses(
                listOf(
                    ServerAddress(url = "http://a"),
                    ServerAddress(name = "主站", url = "https://b.example.com:51026"),
                )
            )
        }
    }

    @Test
    fun addAddress_invalidUrlBlocked() = runTest {
        val error = viewModel.addAddress("", "bad ipv6 ::1:80", https = false)
        advanceUntilIdle()
        assertThat(error).isNotNull()
        verify(exactly = 0) { addressStore.setAddresses(any()) }
    }

    @Test
    fun updateAddress_urlChangeInvalidatesTestAndMigratesActive() = runTest {
        pushAddresses("http://old")
        advanceUntilIdle()
        activeUrlFlow.value = "http://old"

        val error = viewModel.updateAddress("http://old", "新名", "new.example.com:8080", https = true)
        advanceUntilIdle()

        assertThat(error).isNull()
        verify { addressStore.setAddresses(listOf(ServerAddress(name = "新名", url = "https://new.example.com:8080"))) }
        verify { addressStore.setActive("https://new.example.com:8080") }
        assertThat(viewModel.uiState.value.testResults).doesNotContainKey("http://old")
    }

    @Test
    fun removeAddress_updatesStoreAndClearsTestState() = runTest {
        coEvery { publicApi.getDirect(any(), any()) } returns HttpResult.Success("{}")
        pushAddresses("http://a", "http://b")
        advanceUntilIdle()

        viewModel.removeAddress("http://a")
        advanceUntilIdle()

        verify { addressStore.setAddresses(listOf(ServerAddress(url = "http://b"))) }
        assertThat(viewModel.uiState.value.testResults).doesNotContainKey("http://a")
    }

    // —— 拖动排序 ——

    @Test
    fun reorderAddresses_persistsNewOrder() = runTest {
        pushAddresses("http://a", "http://b", "http://c")
        advanceUntilIdle()

        viewModel.reorderAddresses(listOf("http://c", "http://a", "http://b"))
        advanceUntilIdle()

        verify {
            addressStore.setAddresses(
                listOf(ServerAddress(url = "http://c"), ServerAddress(url = "http://a"), ServerAddress(url = "http://b"))
            )
        }
    }

    // —— 表单状态 ——

    @Test
    fun formValidation_gatesSave() = runTest {
        viewModel.onFormUrlChange("bad ipv6 ::1:80")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canSave).isFalse()

        viewModel.onFormUrlChange("good.example.com:8080")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canSave).isTrue()
    }
}
