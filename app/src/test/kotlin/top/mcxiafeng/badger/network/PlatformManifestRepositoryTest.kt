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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlatformManifestRepositoryTest {

    private fun sp(fieldKey: String, displayName: String, enabled: Boolean = true, custom: Boolean = false) =
        ServerPlatform(fieldKey = fieldKey, displayName = displayName, custom = custom, hasDetect = false, enabled = enabled)

    

    @Test
    fun merge_null_returnsLocalFields() {
        assertThat(mergeServerPlatforms(null)).isEqualTo(PLATFORM_FIELDS)
    }

    @Test
    fun merge_empty_returnsLocalFields() {
        assertThat(mergeServerPlatforms(emptyList())).isEqualTo(PLATFORM_FIELDS)
    }

    

    @Test
    fun merge_knownPlatforms_useLocalOrderAndDisplayName_unknownAppended() {
        val result = mergeServerPlatforms(
            listOf(sp("qq", "QQ"), sp("customX", "自定义X", custom = true), sp("github", "GitHub"))
        )
        
        assertThat(result.map { it.fieldKey }).containsExactly("qq", "github", "customX").inOrder()
        
        assertThat(result[0].displayName).isEqualTo(FIELD_DEF_MAP["qq"]!!.displayName)
        assertThat(result[0].inputHint).isEqualTo(FIELD_DEF_MAP["qq"]!!.inputHint)
        
        val dynamic = result[2]
        assertThat(dynamic.fieldKey).isEqualTo("customX")
        assertThat(dynamic.displayName).isEqualTo("自定义X")
        assertThat(dynamic.contactType).isEqualTo(ContactType.None)
        assertThat(dynamic.iconName).isEqualTo("ic_website")
    }

    @Test
    fun merge_localDisplayNameWins_overServer() {
        
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
        
        val result = mergeServerPlatforms(
            listOf(sp("qqGroup", "QQ群"), sp("qq", "QQ"), sp("telegramGroup", "Telegram群"))
        )
        assertThat(result.map { it.fieldKey }).containsExactly("qq", "telegramGroup", "qqGroup").inOrder()
    }

    

    @Test
    fun detectableKinds_enabledAndHasDetect_only_lowercase() {
        val kinds = serverDetectableKinds(
            listOf(
                sp("github", "GitHub").copy(hasDetect = true),
                sp("qqNapcat", "QQ").copy(hasDetect = true),
                sp("bilibili", "B站"), 
                sp("weibo", "微博", enabled = false).copy(hasDetect = true), 
            )
        )
        assertThat(kinds).containsExactly("github", "qqnapcat")
    }

    @Test
    fun detectableKinds_nullOrEmpty_returnsEmpty() {
        assertThat(serverDetectableKinds(null)).isEmpty()
        assertThat(serverDetectableKinds(emptyList())).isEmpty()
    }

    

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
        assertThat(parsed!!.enabled).isTrue()   
        assertThat(parsed.custom).isFalse()
        assertThat(parsed.hasDetect).isFalse()
        assertThat(parsed.displayName).isEmpty()
    }
}
