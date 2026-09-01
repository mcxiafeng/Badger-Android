package top.mcxiafeng.badger.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiquidGlassNavBarTest {

    @Test
    fun `empty tabs produce no selected index`() {
        assertThat(normalizeNavBarIndex(selectedIndex = 0, tabsCount = 0)).isNull()
        assertThat(normalizeNavBarIndex(selectedIndex = -1, tabsCount = 0)).isNull()
    }

    @Test
    fun `selected index is clamped to tab bounds`() {
        assertThat(normalizeNavBarIndex(selectedIndex = -1, tabsCount = 3)).isEqualTo(0)
        assertThat(normalizeNavBarIndex(selectedIndex = 1, tabsCount = 3)).isEqualTo(1)
        assertThat(normalizeNavBarIndex(selectedIndex = 99, tabsCount = 3)).isEqualTo(2)
    }

    @Test
    fun `one tab always normalizes to zero`() {
        assertThat(normalizeNavBarIndex(selectedIndex = -100, tabsCount = 1)).isEqualTo(0)
        assertThat(normalizeNavBarIndex(selectedIndex = 100, tabsCount = 1)).isEqualTo(0)
    }
}
