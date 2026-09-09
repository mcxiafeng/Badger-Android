package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    // ============ mergeServerPlatforms 合并规则（能力归服务端，呈现归客户端）============

    @Test
    fun merge_knownPlatforms_useLocalOrderAndDisplayName_unknownAppended() {
        val result = mergeServerPlatforms(
            listOf(sp("qq", "QQ"), sp("customX", "自定义X", custom = true), sp("github", "GitHub"))
        )
        // 已知平台按本地方序（qq 在 github 前），服务端独有/自定义平台追加在尾部
        assertThat(result.map { it.fieldKey }).containsExactly("qq", "github", "customX").inOrder()
        // 已知平台复用本地 def：displayName 本地所有（在线/离线网格不再漂移）
        assertThat(result[0].displayName).isEqualTo(FIELD_DEF_MAP["qq"]!!.displayName)
        assertThat(result[0].inputHint).isEqualTo(FIELD_DEF_MAP["qq"]!!.inputHint)
        // 服务端独有/自定义平台 → 动态 def：默认 icon + ContactType.None + 服务端 displayName
        val dynamic = result[2]
        assertThat(dynamic.fieldKey).isEqualTo("customX")
        assertThat(dynamic.displayName).isEqualTo("自定义X")
        assertThat(dynamic.contactType).isEqualTo(ContactType.None)
        assertThat(dynamic.iconName).isEqualTo("ic_website")
    }

    @Test
    fun merge_localDisplayNameWins_overServer() {
        // 服务端 displayName 不再覆盖本地中文文案（旧契约在线会把"B站"漂成"Bilibili"）
        val result = mergeServerPlatforms(listOf(sp("qq", "QQ 新版")))
        assertThat(result.single().displayName).isEqualTo(FIELD_DEF_MAP["qq"]!!.displayName)
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
    fun merge_groupPlatforms_included_inLocalOrder() {
        // 服务端全量显示：qqGroup/telegramGroup 出现在可添加网格，顺序随本地清单
        val result = mergeServerPlatforms(
            listOf(sp("qqGroup", "QQ群"), sp("qq", "QQ"), sp("telegramGroup", "Telegram群"))
        )
        assertThat(result.map { it.fieldKey }).containsExactly("qq", "telegramGroup", "qqGroup").inOrder()
    }

    // ============ serverDetectableKinds（自动同步能力集）============

    @Test
    fun detectableKinds_enabledAndHasDetect_only_lowercase() {
        val kinds = serverDetectableKinds(
            listOf(
                sp("github", "GitHub").copy(hasDetect = true),
                sp("qqNapcat", "QQ").copy(hasDetect = true),
                sp("bilibili", "B站"), // 无检测能力
                sp("weibo", "微博", enabled = false).copy(hasDetect = true), // 禁用
            )
        )
        assertThat(kinds).containsExactly("github", "qqnapcat")
    }

    @Test
    fun detectableKinds_nullOrEmpty_returnsEmpty() {
        assertThat(serverDetectableKinds(null)).isEmpty()
        assertThat(serverDetectableKinds(emptyList())).isEmpty()
    }

    // ============ ServerPlatform.parse 契约解析 ============

    @Test
    fun parse_fullObject_parsesAllFields() {
        val obj = buildJsonObject {
            put("name", "qq")
            put("displayName", "QQ")
            put("custom", false)
            put("hasDetect", true)
            put("enabled", true)
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
        assertThat(ServerPlatform.parse(buildJsonObject { put("displayName", "无名") })).isNull()
        assertThat(ServerPlatform.parse(null)).isNull()
    }

    @Test
    fun parse_nameOnly_defaultsBooleans() {
        val parsed = ServerPlatform.parse(buildJsonObject { put("name", "customX") })
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.enabled).isTrue()   // 服务端已过滤 enabled，缺省视为启用
        assertThat(parsed.custom).isFalse()
        assertThat(parsed.hasDetect).isFalse()
        assertThat(parsed.displayName).isEmpty()
    }
}
