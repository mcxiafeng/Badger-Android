package top.mcxiafeng.badger.pages.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
        
        val config = ScanMarkerConfig(tagId = null, tagName = "应被忽略", tagColor = 0xFF112233L)
        assertThat(config.tagId).isNull()
        assertThat(config.enabled).isFalse()
    }

    @Test
    fun `default color matches ScanMarkerPickerDialog default`() {
        
        
        val defaultConfig = ScanMarkerConfig()
        assertThat(defaultConfig.tagColor).isEqualTo(0xFF1976D2L)
    }
}
