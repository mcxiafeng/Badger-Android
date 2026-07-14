package top.mcxiafeng.badger.pages.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * ScanMarkerConfig 数据类单测 —— 纯函数,无需 Robolectric。
 *
 * 设计要点:
 * - `enabled` 是派生属性,等价于 `tagId != null`
 * - 默认值(`ScanMarkerConfig()`)应等价于「无」状态,enabled=false
 * - 显式选 Tag 后 enabled=true,且 Tag 信息透传
 *
 * 用途:ResultDialog 顶部「标记扫描」chip 的状态机;保证「无/标签」互斥语义
 * 与数据类派生属性保持一致,避免 UI 状态和数据语义错位。
 */
class ScanMarkerConfigTest {

    @Test
    fun `default config represents NONE state`() {
        val config = ScanMarkerConfig()
        assertThat(config.tagId).isNull()
        assertThat(config.tagName).isEqualTo("")
        assertThat(config.enabled).isFalse()
    }

    @Test
    fun `config with tagId is enabled`() {
        val config = ScanMarkerConfig(tagId = 42L, tagName = "公司路演", tagColor = 0xFF1976D2L)
        assertThat(config.tagId).isEqualTo(42L)
        assertThat(config.tagName).isEqualTo("公司路演")
        assertThat(config.enabled).isTrue()
    }

    @Test
    fun `explicit null tagId disables config even with name set`() {
        // 防御性:tagId 为 null 即视为「无」,name/color 仅展示用,不参与 enabled 判定
        val config = ScanMarkerConfig(tagId = null, tagName = "应被忽略", tagColor = 0xFF112233L)
        assertThat(config.tagId).isNull()
        assertThat(config.enabled).isFalse()
    }

    @Test
    fun `default color matches ScanMarkerPickerDialog default`() {
        // 颜色常量在 ScannerDialogs.kt ScanMarkerConfig 默认值与 ScanMarkerPickerDialog
        // 「无」chip 默认色必须保持一致,否则首次进入 UI 出现色差
        val defaultConfig = ScanMarkerConfig()
        assertThat(defaultConfig.tagColor).isEqualTo(0xFF1976D2L)
    }
}
