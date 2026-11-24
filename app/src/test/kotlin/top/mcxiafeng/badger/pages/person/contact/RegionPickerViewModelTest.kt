package top.mcxiafeng.badger.pages.person.contact

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.repository.GaodeDistrict
import top.mcxiafeng.badger.data.repository.GaodeException
import top.mcxiafeng.badger.data.repository.GaodeQueryResult
import top.mcxiafeng.badger.data.repository.GaodeRegionRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RegionPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<GaodeRegionRepository>()
    private lateinit var viewModel: RegionPickerViewModel

    private val china = GaodeDistrict("中国", "100000", "country")
    private val beijing = GaodeDistrict("北京市", "110000", "province")
    private val jilin = GaodeDistrict("吉林省", "220000", "province")
    private val chaoyang = GaodeDistrict("朝阳区", "110105", "district")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = RegionPickerViewModel(repo)
        coEvery { repo.queryCountry("中国") } returns GaodeQueryResult(china, listOf(beijing, jilin))
    }

    @After
    fun tearDown() {
        viewModel.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun rootCannotBeConfirmed_andBreadcrumbRestoresChildrenWithoutFetching() = runTest(dispatcher) {
        coEvery { repo.queryChildren(beijing) } returns listOf(chaoyang)
        coEvery { repo.queryChildren(chaoyang) } returns emptyList()
        viewModel.loadInitial("中国")
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedRegion).isNull()

        viewModel.pickChild(beijing)
        advanceUntilIdle()
        viewModel.pickChild(chaoyang)
        advanceUntilIdle()
        assertThat(viewModel.state.value.path).containsExactly(china, beijing, chaoyang).inOrder()
        assertThat(viewModel.state.value.selectedRegion).isEqualTo("朝阳区")

        viewModel.goBackTo(1)
        assertThat(viewModel.state.value.path).containsExactly(china, beijing).inOrder()
        assertThat(viewModel.state.value.children).containsExactly(chaoyang)
        viewModel.goBackTo(0)
        assertThat(viewModel.state.value.children).containsExactly(beijing, jilin)
        assertThat(viewModel.state.value.selectedRegion).isNull()
        coVerify(exactly = 1) { repo.queryCountry("中国") }
        coVerify(exactly = 1) { repo.queryChildren(beijing) }
    }

    @Test
    fun childFailureDisablesConfirmation_andRetryLoadsThatSameChild() = runTest(dispatcher) {
        coEvery { repo.queryChildren(beijing) } returns listOf(chaoyang)
        coEvery { repo.queryChildren(chaoyang) } throws GaodeException("网络错误") andThen emptyList()
        viewModel.loadInitial("中国")
        advanceUntilIdle()
        viewModel.pickChild(beijing)
        advanceUntilIdle()
        viewModel.pickChild(chaoyang)
        assertThat(viewModel.state.value.selectedRegion).isNull()
        advanceUntilIdle()
        assertThat(viewModel.state.value.errorMsg).isEqualTo("网络错误")
        assertThat(viewModel.state.value.path).containsExactly(china, beijing).inOrder()
        assertThat(viewModel.state.value.selectedRegion).isNull()

        viewModel.retry()
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedRegion).isEqualTo("朝阳区")
        coVerify(exactly = 2) { repo.queryChildren(chaoyang) }
    }

    @Test
    fun selectingCurrentBreadcrumbAfterChildFailureRestoresItsList() = runTest(dispatcher) {
        coEvery { repo.queryChildren(beijing) } throws GaodeException("网络错误")
        viewModel.loadInitial("中国")
        advanceUntilIdle()
        viewModel.pickChild(beijing)
        advanceUntilIdle()
        assertThat(viewModel.state.value.errorMsg).isNotNull()

        viewModel.goBackTo(0)

        assertThat(viewModel.state.value.errorMsg).isNull()
        assertThat(viewModel.state.value.children).containsExactly(beijing, jilin)
        coVerify(exactly = 1) { repo.queryCountry("中国") }
    }

    @Test
    fun rootFailureCanBeRetried() = runTest(dispatcher) {
        coEvery { repo.queryCountry("中国") } throws GaodeException("网络错误") andThen
            GaodeQueryResult(china, listOf(beijing))
        viewModel.loadInitial("中国")
        advanceUntilIdle()
        assertThat(viewModel.state.value.errorMsg).isEqualTo("网络错误")

        viewModel.retry()
        advanceUntilIdle()
        assertThat(viewModel.state.value.errorMsg).isNull()
        assertThat(viewModel.state.value.children).containsExactly(beijing)
    }

    @Test
    fun responseFromClosedPickerCannotReplaceNewCountry() = runTest(dispatcher) {
        val oldRequestStarted = CompletableDeferred<Unit>()
        val oldResponse = CompletableDeferred<GaodeQueryResult>()
        coEvery { repo.queryCountry("中国") } coAnswers {
            oldRequestStarted.complete(Unit)
            withContext(NonCancellable) { oldResponse.await() }
        }
        val japan = GaodeDistrict("日本", "392000000", "country")
        val tokyo = GaodeDistrict("东京都", "392010000", "province")
        coEvery { repo.queryCountry("日本") } returns GaodeQueryResult(japan, listOf(tokyo))

        viewModel.loadInitial("中国")
        runCurrent()
        assertThat(oldRequestStarted.isCompleted).isTrue()
        viewModel.reset()
        viewModel.loadInitial("日本")
        advanceUntilIdle()
        oldResponse.complete(GaodeQueryResult(china, listOf(beijing)))
        advanceUntilIdle()

        assertThat(viewModel.state.value.path).containsExactly(japan)
        assertThat(viewModel.state.value.children).containsExactly(tokyo)
        assertThat(viewModel.state.value.loading).isFalse()
    }
}
