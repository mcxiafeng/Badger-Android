package top.mcxiafeng.badger.data.repository

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.Assert.assertThrows

/**
 * [GaodeRegionRepository.parseDistricts] 解析逻辑回归测试。
 *
 * 覆盖高德行政区域查询接口的返回结构:
 * status 校验、children 映射、districts 为空 / JsonNull、name 空节点过滤。
 * 不经过网络,直接调用 internal 解析方法。
 */
class GaodeRegionRepositoryTest {

    private val repo = GaodeRegionRepository(mockk<GaodeConfig>(), OkHttpClient())

    @Test
    fun parse_success_withChildren() {
        val json = """
            {
              "status": "1",
              "info": "OK",
              "infocode": "10000",
              "districts": [
                {
                  "citycode": "",
                  "adcode": "100000",
                  "name": "中国",
                  "center": "116.369,39.915",
                  "level": "country",
                  "districts": [
                    {
                      "citycode": "010",
                      "adcode": "110000",
                      "name": "北京市",
                      "level": "province"
                    },
                    {
                      "citycode": "021",
                      "adcode": "310000",
                      "name": "上海市",
                      "level": "province"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = repo.parseDistricts(json)

        assertThat(result.top?.name).isEqualTo("中国")
        assertThat(result.top?.level).isEqualTo("country")
        assertThat(result.top?.adcode).isEqualTo("100000")
        assertThat(result.children.map { it.name })
            .containsExactly("北京市", "上海市").inOrder()
        assertThat(result.children.map { it.level })
            .containsExactly("province", "province")
    }

    @Test
    fun parse_success_leafNoChildren() {
        val json = """
            {
              "status": "1",
              "info": "OK",
              "districts": [
                {
                  "adcode": "110101",
                  "name": "东城区",
                  "level": "district"
                }
              ]
            }
        """.trimIndent()

        val result = repo.parseDistricts(json)

        assertThat(result.top?.name).isEqualTo("东城区")
        assertThat(result.top?.level).isEqualTo("district")
        assertThat(result.children).isEmpty()
    }

    @Test
    fun parse_error_statusZero_throws() {
        val json = """
            {
              "status": "0",
              "info": "INVALID_USER_KEY",
              "infocode": "10001"
            }
        """.trimIndent()

        try {
            repo.parseDistricts(json)
            throw AssertionError("应抛出异常")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("高德 Key 无效")
        }
    }

    @Test
    fun parse_emptyDistricts_returnsNullTop() {
        val json = """
            {
              "status": "1",
              "info": "OK",
              "districts": []
            }
        """.trimIndent()

        val result = repo.parseDistricts(json)

        assertThat(result.top).isNull()
        assertThat(result.children).isEmpty()
    }

    @Test
    fun parse_nullDistricts_handledGracefully() {
        // 高德个别情况下 districts 可能为 JSON null,不应崩溃
        val json = """
            {
              "status": "1",
              "info": "OK",
              "districts": null
            }
        """.trimIndent()

        val result = repo.parseDistricts(json)

        assertThat(result.top).isNull()
        assertThat(result.children).isEmpty()
    }

    @Test
    fun parse_blankNameNodes_filteredOut() {
        val json = """
            {
              "status": "1",
              "info": "OK",
              "districts": [
                {
                  "adcode": "100000",
                  "name": "中国",
                  "level": "country",
                  "districts": [
                    { "adcode": "110000", "name": "", "level": "province" },
                    { "adcode": "310000", "name": "上海市", "level": "province" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = repo.parseDistricts(json)

        assertThat(result.top?.name).isEqualTo("中国")
        assertThat(result.children.map { it.name }).containsExactly("上海市")
    }

    @Test
    fun parse_statusEmptyArray_throwsClearError() {
        // 高德异常响应可能把 status 返回为空数组。旧实现直接 asString 会抛 Gson 内部异常
        // "Array must have size 1, but has size 0",用户看到无意义报错;应转为明确错误。
        val json = """
            { "status": [] }
        """.trimIndent()

        try {
            repo.parseDistricts(json)
            throw AssertionError("应抛出异常")
        } catch (e: IllegalStateException) {
            assertThat(e.message).isNotEqualTo("Array must have size 1, but has size 0")
            assertThat(e.message).contains("格式异常")
        }
    }

    @Test
    fun parse_statusMissing_throwsClearError() {
        // status 缺失时不应抛 Gson 内部异常,应给出明确错误。
        val json = """
            { "info": "OK" }
        """.trimIndent()

        try {
            repo.parseDistricts(json)
            throw AssertionError("应抛出异常")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("格式异常")
        }
    }

    @Test
    fun parse_nonObjectRoot_throwsClearError() {
        // 响应体不是 JSON 对象(如空数组)时,应给出明确错误而非 Gson 内部异常。
        val json = "[]"

        try {
            repo.parseDistricts(json)
            throw AssertionError("应抛出异常")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("格式异常")
        }
    }

    @Test
    fun parse_invalidRootNode_failsWithReadableMessage() {
        // 无效根节点不能当作正常的空区划。
        val json = """
            {
              "status": "1",
              "info": "OK",
              "districts": [
                { "name": [], "adcode": "100000", "level": "country" }
              ]
            }
        """.trimIndent()

        assertThat(assertThrows(GaodeException::class.java) { repo.parseDistricts(json) }.message)
            .contains("格式异常")
    }

    @Test
    fun parse_globalDistrict_nineDigitAdcode() {
        // 全球接口:adcode 为 9 位,1-3 国家、4-5 省、6-7 市、8-9 区
        val json = """
            {
              "status": "1",
              "info": "OK",
              "infocode": "10000",
              "districts": [
                {
                  "adcode": "840000000",
                  "name": "United States",
                  "level": "country",
                  "districts": [
                    { "adcode": "840010000", "name": "New York", "level": "province" },
                    { "adcode": "840020000", "name": "California", "level": "province" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = repo.parseDistricts(json)

        assertThat(result.top?.name).isEqualTo("United States")
        assertThat(result.top?.adcode).isEqualTo("840000000")
        assertThat(result.top?.level).isEqualTo("country")
        assertThat(result.children.map { it.name }).containsExactly("New York", "California").inOrder()
        // 9 位 adcode 必须完整保留,不能被当作 6 位截断
        assertThat(result.children.map { it.adcode }).containsExactly("840010000", "840020000").inOrder()
    }

    @Test
    fun parse_globalDistrictsEmptyArray_returnsNullTop() {
        // 全球接口无该国家数据时可能返回空 districts
        val json = """{"status":"1","info":"OK","districts":[]}"""
        val result = repo.parseDistricts(json)
        assertThat(result.top).isNull()
        assertThat(result.children).isEmpty()
    }

    @Test
    fun parse_globalError_throwsWithSafeMessage() {
        // 未开通海外权限时返回 INVALID_USER_KEY
        val json = """{"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}"""
        try {
            repo.parseDistricts(json)
            throw AssertionError("应抛出异常")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("高德 Key 无效")
        }
    }
}
