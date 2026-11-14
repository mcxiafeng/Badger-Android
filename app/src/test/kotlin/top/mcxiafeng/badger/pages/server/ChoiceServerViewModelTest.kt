package top.mcxiafeng.badger.pages.server

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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
import top.mcxiafeng.badger.network.core.ServerAddressStore
import top.mcxiafeng.badger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ChoiceServerViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var addressStore: ServerAddressStore
    private lateinit var publicApi: PublicApi
    private lateinit var viewModel: ChoiceServerViewModel

    private val historyFlow = MutableStateFlow<List<String>>(emptyList())

    @Before
    fun setup() {
        addressStore = mockk(relaxed = true)
        publicApi = mockk(relaxed = true)
        every { addressStore.history } returns historyFlow
        viewModel = ChoiceServerViewModel(addressStore, publicApi)
    }

    @Test
    fun onInput_stripsSchemeAndAdoptsHttps() = runTest {
        viewModel.onInput("https://badger.example.com/")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.input).isEqualTo("badger.example.com")
        assertThat(state.https).isTrue()
        assertThat(state.urlReason).isNull()
        assertThat(state.canProceed).isTrue()
    }

    @Test
    fun onInput_invalidHostBlocked() = runTest {
        viewModel.onInput("[bad-ipv6")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.urlReason).isNotNull()
        assertThat(viewModel.uiState.value.canProceed).isFalse()
    }

    @Test
    fun proceed_blankInputBlocked() = runTest {
        val error = viewModel.proceed()
        advanceUntilIdle()
        assertThat(error).isNotNull()
        coVerify(exactly = 0) { publicApi.getDirect(any(), any()) }
    }

    @Test
    fun proceed_timeoutReturnsUserMessage() = runTest {
        viewModel.onInput("badger.example.com:51026")
        advanceUntilIdle()
        coEvery { publicApi.getDirect(any(), any()) } returns
            HttpResult.Failure(0, null, null, HttpResult.ErrorType.TIMEOUT)

        val error = viewModel.proceed()
        advanceUntilIdle()

        assertThat(error).isEqualTo("连接超时，请检查地址或网络")
        verify(exactly = 0) { addressStore.setActive(any()) }
    }

    @Test
    fun proceed_successActivatesAndRecordsHistory() = runTest {
        // 默认走 https（DEFAULT_HTTPS = true）
        viewModel.onInput("192.168.10.19:8080")
        advanceUntilIdle()
        coEvery { publicApi.getDirect(any(), any()) } returns HttpResult.Success("{}")

        val error = viewModel.proceed()
        advanceUntilIdle()

        assertThat(error).isNull()
        verify { addressStore.setActive("https://192.168.10.19:8080") }
        verify { addressStore.addHistory("https://192.168.10.19:8080") }
        verify { publicApi.recordSuccessBase("https://192.168.10.19:8080") }
    }

    @Test
    fun proceed_httpsToggleSwitchesScheme() = runTest {
        viewModel.onInput("badger.example.com")
        advanceUntilIdle()
        viewModel.onHttpsChange(false)
        advanceUntilIdle()
        coEvery { publicApi.getDirect(any(), any()) } returns HttpResult.Success("{}")

        viewModel.proceed()
        advanceUntilIdle()

        verify { addressStore.setActive("http://badger.example.com:51026") }
    }

    @Test
    fun history_updatesFromStore() = runTest {
        historyFlow.value = listOf("http://first", "http://second")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.history).containsExactly("http://first", "http://second").inOrder()

        viewModel.removeHistory("http://first")
        verify { addressStore.removeHistory("http://first") }
    }
}
