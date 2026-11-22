package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.utils.HttpUtil

class QqProfileLookupTest {
    @Before
    fun setUp() {
        mockkObject(HttpUtil)
        coEvery { HttpUtil.get(any()) } returns PROFILE_RESPONSE
    }

    @After
    fun tearDown() {
        unmockkObject(HttpUtil)
    }

    @Test
    fun scannedQqLink_usesProfileNameDescriptionAndHttpsAvatar() = runTest {
        val result = checkNotNull(ContactNetworkResolver.getResultInfo(QQ_LINK))

        assertThat(result.nickname).isEqualTo("测试\"昵称\"")
        assertThat(result.description).isEqualTo("空间简介")
        assertThat(result.avatarUrl).isEqualTo("https://q2.qlogo.cn/headimg_dl?dst_uin=$QQ&spec=640")
        assertThat(result.contactMap).containsExactly("qq", QQ)
        coVerify(exactly = 1) { HttpUtil.get(PROFILE_URL) }
    }

    @Test
    fun addingQqLink_usesProfileNameAndAvatar() = runTest {
        val result = LinkResolver.resolve("qq", QQ_LINK)

        assertThat(result.value).isEqualTo(QQ)
        assertThat(result.displayName).isEqualTo("测试\"昵称\"")
        assertThat(result.avatarUrl).isEqualTo("https://q2.qlogo.cn/headimg_dl?dst_uin=$QQ&spec=640")
        assertThat(result.errorMessage).isNull()
        coVerify(exactly = 1) { HttpUtil.get(PROFILE_URL) }
    }

    @Test
    fun unavailableProfile_scanRetainsQqAndFallbackAvatar() = runTest {
        val responses = listOf(null, PROFILE_RESPONSE.replace("\"code\": 200", "\"code\": 503"), "{", """{"code":200,"data":{"user_info":null}}""")
        for (response in responses) {
            coEvery { HttpUtil.get(any()) } returns response

            val result = checkNotNull(ContactNetworkResolver.getResultInfo(QQ_LINK))

            assertThat(result.contactMap).containsExactly("qq", QQ)
            assertThat(result.nickname).isEqualTo("QQ用户$QQ")
            assertThat(result.description).isEmpty()
            assertThat(result.avatarUrl).isEqualTo(FALLBACK_AVATAR)
        }
    }

    @Test
    fun unsafeAvatar_keepsAvailableProfileFieldsAndUsesFallback() = runTest {
        coEvery { HttpUtil.get(any()) } returns PROFILE_RESPONSE.replace("q2.qlogo.cn", "q2.qlogo.cn.example.com")

        val result = checkNotNull(ContactNetworkResolver.getResultInfo(QQ_LINK))

        assertThat(result.nickname).isEqualTo("测试\"昵称\"")
        assertThat(result.description).isEqualTo("空间简介")
        assertThat(result.avatarUrl).isEqualTo(FALLBACK_AVATAR)
    }

    private companion object {
        const val QQ = "123456789"
        const val QQ_LINK = "https://qzone.qq.com/$QQ"
        const val PROFILE_URL = "https://xiaoapi.cn/v1/qzone_info.php?qq=$QQ"
        const val FALLBACK_AVATAR = "https://q1.qlogo.cn/g?b=qq&nk=$QQ&s=100"
        val PROFILE_RESPONSE = """
            {
                "code": 200,
                "data": {
                    "user_info": {
                        "qq": "$QQ",
                        "name": "测试\"昵称\"",
                        "head_url": "http://q2.qlogo.cn/headimg_dl?dst_uin=$QQ&spec=640",
                        "qzone_desc": "空间简介"
                    },
                    "qzone_info": { "pic_count": 30 },
                    "vip_info": { "level": 7 }
                }
            }
        """.trimIndent()
    }
}
