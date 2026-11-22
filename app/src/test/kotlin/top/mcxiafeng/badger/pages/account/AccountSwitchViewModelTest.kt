package top.mcxiafeng.badger.pages.account

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.auth.AuthApi
import top.mcxiafeng.badger.network.core.ServerAddressStore
import top.mcxiafeng.badger.pages.auth.AuthViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountSwitchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private lateinit var accounts: AccountStore
    private lateinit var addresses: ServerAddressStore
    private lateinit var source: AccountScope
    private lateinit var target: AccountScope
    private lateinit var auth: AuthApi
    private lateinit var vm: AccountSwitchViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("account_spaces", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("server_config", Context.MODE_PRIVATE).edit().clear().commit()
        accounts = AccountStore(context)
        target = accounts.activateRemote("target-server", "target-user", "epoch", "目标账号", TARGET_URL)
        source = accounts.activateRemote("source-server", "source-user", "epoch", "当前账号", SOURCE_URL)
        addresses = ServerAddressStore(context).apply { setActive(SOURCE_URL) }
        auth = mockk(relaxed = true)
        vm = retain("switch", AccountSwitchViewModel(accounts, auth, addresses))
    }

    @After fun cleanup() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test fun selectionDoesNotActivateOrConnect() {
        vm.select(target.key)
        assertThat(vm.state.value.selected).isEqualTo(target)
        assertThat(accounts.current()).isEqualTo(source)
        assertThat(addresses.activeUrl.value).isEqualTo(SOURCE_URL)
        coVerify(exactly = 0) { auth.login(any(), any(), any(), any()) }
        vm.showAccounts()
        vm.select(source.key)
        assertThat(vm.state.value.selected).isNull()
    }

    @Test fun failureRetainsSpaceAndAddress() = runTest(dispatcher) {
        coEvery { auth.login(target.username, PASSWORD, TARGET_URL, target) } returns failure()
        vm.select(target.key)
        vm.onPassword(PASSWORD)
        vm.signIn()
        vm.signIn()
        advanceUntilIdle()
        assertThat(accounts.current()).isEqualTo(source)
        assertThat(addresses.activeUrl.value).isEqualTo(SOURCE_URL)
        assertThat(vm.state.value.error).contains("密码")
        assertThat(vm.state.value.password).isEqualTo(PASSWORD)
        assertThat(vm.state.value.busy).isFalse()
        coVerify(exactly = 1) { auth.login(target.username, PASSWORD, TARGET_URL, target) }
        coVerify(exactly = 0) { auth.logout(any()) }
    }

    @Test fun exitCancelsAndClearsPassword() = runTest(dispatcher) {
        val response = CompletableDeferred<HttpResult>()
        coEvery { auth.login(any(), any(), any(), target) } coAnswers { response.await() }
        vm.select(target.key)
        vm.onPassword(PASSWORD)
        vm.togglePasswordVisibility()
        vm.signIn()
        runCurrent()
        assertThat(vm.state.value.busy).isTrue()
        vm.onExit()
        response.complete(HttpResult.Success(""))
        advanceUntilIdle()
        assertThat(accounts.current()).isEqualTo(source)
        assertThat(addresses.activeUrl.value).isEqualTo(SOURCE_URL)
        assertThat(vm.state.value.password).isEmpty()
        assertThat(vm.state.value.passwordVisible).isFalse()
        assertThat(vm.state.value.busy).isFalse()
        verify(exactly = 1) { auth.cancelPendingLogin() }
        coVerify(exactly = 0) { auth.logout(any()) }
    }

    @Test fun cancellationBeforeAddressChange() = runTest(dispatcher) {
        vm.select(target.key)
        vm.onPassword(PASSWORD)
        vm.signIn()
        vm.showAccounts()
        advanceUntilIdle()
        assertThat(vm.state.value.selected).isNull()
        assertThat(vm.state.value.password).isEmpty()
        assertThat(accounts.current()).isEqualTo(source)
        assertThat(addresses.activeUrl.value).isEqualTo(SOURCE_URL)
        coVerify(exactly = 0) { auth.login(any(), any(), any(), any()) }
    }

    @Test fun addressObserversRunBeforeLogin() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        every { auth.cancelPendingLogin() } answers { events.add("invalidate"); 0 }
        retain("existing-auth", AuthViewModel(auth, addresses))
        coEvery { auth.login(target.username, PASSWORD, TARGET_URL, target) } coAnswers {
            events.add("login")
            accounts.activateRemote(checkNotNull(target.serverId), checkNotNull(target.userId),
                checkNotNull(target.dataEpoch), target.username, target.baseUrl)
            HttpResult.Success("")
        }
        vm.select(target.key)
        vm.onPassword(PASSWORD)
        vm.signIn()
        advanceUntilIdle()
        assertThat(events).containsExactly("invalidate", "login").inOrder()
        assertThat(accounts.current()).isEqualTo(target)
        assertThat(addresses.activeUrl.value).isEqualTo(TARGET_URL)
        assertThat(vm.state.value.password).isEmpty()
        assertThat(addresses.isUrlVerified.value).isTrue()
    }

    @Test fun localSwitchNeedsConfirmation() = runTest(dispatcher) {
        vm.select(accounts.localScope().key)
        assertThat(vm.state.value.showLocalConfirmation).isTrue()
        assertThat(accounts.current()).isEqualTo(source)
        coVerify(exactly = 0) { auth.logout(any()) }
        vm.dismissLocalConfirmation()
        vm.confirmLocal()
        runCurrent()
        coVerify(exactly = 0) { auth.logout(any()) }
        coEvery { auth.logout(source.key) } coAnswers { accounts.switchToLocal(); HttpResult.Success("") }
        vm.select(accounts.localScope().key)
        vm.confirmLocal()
        vm.confirmLocal()
        advanceUntilIdle()
        assertThat(vm.state.value.showLocalConfirmation).isFalse()
        assertThat(accounts.current()).isEqualTo(accounts.localScope())
        assertThat(accounts.registeredScopes()).containsAtLeast(source, target)
        coVerify(exactly = 1) { auth.logout(source.key) }
    }

    @Test fun stalePageCannotSwitchNewSpace() = runTest(dispatcher) {
        vm.select(accounts.localScope().key)
        accounts.activateRemote(checkNotNull(target.serverId), checkNotNull(target.userId),
            checkNotNull(target.dataEpoch), target.username, target.baseUrl)
        vm.confirmLocal()
        advanceUntilIdle()
        assertThat(vm.state.value.showLocalConfirmation).isFalse()
        assertThat(accounts.current()).isEqualTo(target)
        coVerify(exactly = 0) { auth.logout(any()) }
    }

    @Test fun failedAttemptDoesNotUndoNewAddress() = runTest(dispatcher) {
        val response = CompletableDeferred<HttpResult>()
        coEvery { auth.login(any(), any(), any(), target) } coAnswers { response.await() }
        vm.select(target.key)
        vm.onPassword(PASSWORD)
        vm.signIn()
        runCurrent()
        addresses.setActive("https://new-choice.example")
        response.complete(failure())
        advanceUntilIdle()
        assertThat(addresses.activeUrl.value).isEqualTo("https://new-choice.example")
        assertThat(accounts.current()).isEqualTo(source)
    }

    private fun <T : ViewModel> retain(key: String, model: T): T = model.also { viewModels.put(key, it) }
    private fun failure() = HttpResult.Failure(401, null, null, HttpResult.ErrorType.AUTH)

    companion object {
        private const val SOURCE_URL = "https://source.example"
        private const val TARGET_URL = "https://target.example"
        private const val PASSWORD = "synthetic-password"
    }
}
