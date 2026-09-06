package top.mcxiafeng.badger.ui.windowsize

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [KMP K18] 大屏适配纯函数单测：断点阈值与官方 material3-window-size-class 定义一致
 * （width: <600 Compact / <840 Medium / else Expanded；height: <480 / <900 / else）。
 */
class BadgerWindowSizeTest {

    @Test
    fun `width thresholds match material3 spec`() {
        assertEquals(WindowWidthSizeClass.Compact, windowSizeClassFromDp(599.dp, 800.dp).widthSizeClass)
        assertEquals(WindowWidthSizeClass.Medium, windowSizeClassFromDp(600.dp, 800.dp).widthSizeClass)
        assertEquals(WindowWidthSizeClass.Medium, windowSizeClassFromDp(839.dp, 800.dp).widthSizeClass)
        assertEquals(WindowWidthSizeClass.Expanded, windowSizeClassFromDp(840.dp, 800.dp).widthSizeClass)
    }

    @Test
    fun `height thresholds match material3 spec`() {
        assertEquals(WindowHeightSizeClass.Compact, windowSizeClassFromDp(800.dp, 479.dp).heightSizeClass)
        assertEquals(WindowHeightSizeClass.Medium, windowSizeClassFromDp(800.dp, 480.dp).heightSizeClass)
        assertEquals(WindowHeightSizeClass.Medium, windowSizeClassFromDp(800.dp, 899.dp).heightSizeClass)
        assertEquals(WindowHeightSizeClass.Expanded, windowSizeClassFromDp(800.dp, 900.dp).heightSizeClass)
    }

    @Test
    fun `typical phone and tablet sizes map to expected classes`() {
        // 手机 412x915dp → Compact 宽 + Medium 高（现行 UI 依赖宽度档位，高度档位仅登记）
        val phone = windowSizeClassFromDp(412.dp, 915.dp)
        assertEquals(WindowWidthSizeClass.Compact, phone.widthSizeClass)
        // 平板 1280x800dp → Expanded
        val tablet = windowSizeClassFromDp(1280.dp, 800.dp)
        assertEquals(WindowWidthSizeClass.Expanded, tablet.widthSizeClass)
        assertEquals(WindowHeightSizeClass.Medium, tablet.heightSizeClass)
    }

    @Test
    fun `grid columns follow width class`() {
        assertEquals(2, gridColumnsForWidthClass(WindowWidthSizeClass.Compact))
        assertEquals(3, gridColumnsForWidthClass(WindowWidthSizeClass.Medium))
        assertEquals(4, gridColumnsForWidthClass(WindowWidthSizeClass.Expanded))
    }
}
