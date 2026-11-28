package top.mcxiafeng.badger.ui.glass

import top.mcxiafeng.badger.ui.components.mergePagePadding

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PageScrollInsetsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun defaultExtraAddsOnlyThreeDpAtTheTop() {
        var result: PaddingValues? = null
        compose.setContent {
            result = mergePagePadding(PaddingValues(start = 5.dp, top = 40.dp, end = 7.dp, bottom = 20.dp))
        }
        compose.runOnIdle {
            val padding = checkNotNull(result)
            assertThat(padding.calculateTopPadding()).isEqualTo(43.dp)
            assertThat(padding.calculateBottomPadding()).isEqualTo(20.dp)
            assertThat(padding.calculateStartPadding(LayoutDirection.Ltr)).isEqualTo(5.dp)
            assertThat(padding.calculateEndPadding(LayoutDirection.Ltr)).isEqualTo(7.dp)
        }
    }

    @Test
    fun ltrContentSpacingAndChromeReceiveExactlyOneTopGap() {
        assertMergedPadding(LayoutDirection.Ltr, expectedStart = 16, expectedEnd = 20)
    }

    @Test
    fun rtlPreservesPhysicalChromeSidesAndLogicalContentSpacing() {
        assertMergedPadding(LayoutDirection.Rtl, expectedStart = 18, expectedEnd = 18)
    }

    private fun assertMergedPadding(direction: LayoutDirection, expectedStart: Int, expectedEnd: Int) {
        var result: PaddingValues? = null
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                result = mergePagePadding(
                    chrome = PaddingValues.Absolute(left = 5.dp, top = 40.dp, right = 7.dp, bottom = 20.dp),
                    extra = PaddingValues(start = 11.dp, top = 2.dp, end = 13.dp, bottom = 17.dp),
                )
            }
        }
        compose.runOnIdle {
            val padding = checkNotNull(result)
            assertThat(padding.calculateTopPadding()).isEqualTo(45.dp)
            assertThat(padding.calculateBottomPadding()).isEqualTo(37.dp)
            assertThat(padding.calculateStartPadding(direction)).isEqualTo(expectedStart.dp)
            assertThat(padding.calculateEndPadding(direction)).isEqualTo(expectedEnd.dp)
        }
    }
}
