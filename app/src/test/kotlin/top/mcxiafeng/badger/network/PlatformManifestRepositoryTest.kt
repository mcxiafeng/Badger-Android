package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELDS

/**
 * [Phase 4 剩余] 服务端平台清单接入 UI 的纯逻辑测试。
 *
 * 覆盖 [mergeServerPlatforms] 合并规则 + [ServerPlatform.parse] 契约解析。
 * 纯函数，不依赖 Koin/网络（Robolectric 仅为 R/FIELD_DEF_MAP 的 Android 符号安全）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlatformManifestRepositoryTest {

    private fun sp(fieldKey: String, displayName: String, enabled: Boolean = true, custom: Boolean = false) =
        ServerPlatform(fieldKey = fieldKey, displayName = displayName, custom = custom, hasDetect = false, enabled = enabled)

    // ============ mergeServerPlatforms 兜底 ============

    @Test
    fun merge_null_returnsLocalFields() {
        assertThat(mergeServerPlatforms(null)).isEqualTo(PLATFORM_FIELDS)
    }

    @Test
    fun merge_empty_returnsLocalFields() {
        assertThat(mergeServerPlatforms(emptyList())).isEqualTo(PLATFORM_FIELDS)
    }

    // ============ mergeServerPlatforms 合并规则 ============

    @Test
    fun merge_preservesServerOrder_andBuildsDynamicDefForUnknown() {
        val result = mergeServerPlatforms(
            listOf(sp("qq", "QQ"), sp("customX", "自定义X", custom = true), sp("github", "GitHub"))
        )
        // 纯服务端注册表序
        assertThat(result.map { it.fieldKey }).containsExactly("qq", "customX", "github").inOrder()
        // 已知平台复用本地 def（fieldKey 关联），服务端 displayName 生效
        assertThat(result[0].displayName).isEqualTo("QQ")
        assertThat(result[0].inputHint).isEqualTo(FIELD_DEF_MAP["qq"]!!.inputHint)
        // 服务端独有/自定义平台 → 动态 def：默认 icon + ContactType.None + 服务端 displayName
        val dynamic = result[1]
        assertThat(dynamic.fieldKey).isEqualTo("customX")
        assertThat(dynamic.displayName).isEqualTo("自定义X")
        assertThat(dynamic.contactType).isEqualTo(ContactType.None)
        assertThat(dynamic.iconRes).isEqualTo(R.drawable.ic_website)
    }

    @Test
    fun merge_serverDisplayNameOverridesLocal() {
        val result = mergeServerPlatforms(listOf(sp("qq", "QQ 新版")))
        assertThat(result.single().displayName).isEqualTo("QQ 新版")
    }

    @Test
    fun merge_blankServerDisplayName_fallsBackToLocal() {
        val result = mergeServerPlatforms(listOf(sp("qq", "")))
        assertThat(result.single().displayName).isEqualTo(FIELD_DEF_MAP["qq"]!!.displayName)
    }

    @Test
    fun merge_disabledPlatform_filteredOut() {
        val result = mergeServerPlatforms(listOf(sp("qq", "QQ"), sp("bilibili", "B站", enabled = false)))
        assertThat(result.map { it.fieldKey }).containsExactly("qq")
    }

    @Test
    fun merge_groupPlatforms_included_perServerManifest() {
        // 决策拍板：服务端全量显示 —— qqGroup/telegramGroup 也出现在可添加网格
        val result = mergeServerPlatforms(
            listOf(sp("qqGroup", "QQ群"), sp("qq", "QQ"), sp("telegramGroup", "Telegram群"))
        )
        assertThat(result.map { it.fieldKey }).containsExactly("qqGroup", "qq", "telegramGroup").inOrder()
        assertThat(result[0].displayName).isEqualTo("QQ群")
    }

    // ============ ServerPlatform.parse 契约解析 ============

    @Test
    fun parse_fullObject_parsesAllFields() {
        val obj = JsonObject().apply {
            addProperty("name", "qq")
            addProperty("displayName", "QQ")
            addProperty("custom", false)
            addProperty("hasDetect", true)
            addProperty("enabled", true)
        }
        val parsed = ServerPlatform.parse(obj)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.fieldKey).isEqualTo("qq")
        assertThat(parsed.displayName).isEqualTo("QQ")
        assertThat(parsed.custom).isFalse()
        assertThat(parsed.hasDetect).isTrue()
        assertThat(parsed.enabled).isTrue()
    }

    @Test
    fun parse_missingName_returnsNull() {
        assertThat(ServerPlatform.parse(JsonObject().apply { addProperty("displayName", "无名") })).isNull()
        assertThat(ServerPlatform.parse(null)).isNull()
    }

    @Test
    fun parse_nameOnly_defaultsBooleans() {
        val parsed = ServerPlatform.parse(JsonObject().apply { addProperty("name", "customX") })
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.enabled).isTrue()   // 服务端已过滤 enabled，缺省视为启用
        assertThat(parsed.custom).isFalse()
        assertThat(parsed.hasDetect).isFalse()
        assertThat(parsed.displayName).isEmpty()
    }
}
