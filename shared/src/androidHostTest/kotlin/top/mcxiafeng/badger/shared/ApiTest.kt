package top.mcxiafeng.badger.shared

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.network.core.ProfileApi

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiTest {

    @Test
    fun testApi() = runTest {
        println("==================================================================================================")
        println("ApiTest: Starting testApi")
        TestSession.ensureLoggedIn()
        val persons = ProfileApi.getProfile(TestSession.SEED_PROFILE_UUID)
        println("获取到新的值： $persons")
        println("==================================================================================================")
    }
}
