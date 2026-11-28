package top.mcxiafeng.badger.pages.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.Card
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.components.mergePagePadding
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Layout and touch evidence only; no screen capture or claims about GPU-rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
class SettingsScrollEffectsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shortSettingsListOwnsTheWholeViewportIncludingTheBlankLowerHalf() {
        compose.setContent {
            MiuixTheme {
                Box(Modifier.fillMaxSize().testTag(SCREEN)) { SettingsPage({}, {}, {}) }
            }
        }
        val screen = compose.onNodeWithTag(SCREEN).fetchSemanticsNode().boundsInRoot
        val list = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        assertThat(list).isEqualTo(screen)
        assertThat(compose.onNodeWithText("UI 设置").fetchSemanticsNode().boundsInRoot.bottom)
            .isLessThan(list.bottom)
    }

    // 弹簧回弹（SpringOverscroll）断言随液态界面留在分支；master 的原生 overscroll 拉伸
    // 发生在绘制层，语义树测不到位移，故不再断言空白区拖动平移。

    @Test
    fun settingsAddsFiveDpToContentWithoutMovingChromeOrDoublingTheGlobalGap() {
        val settings = mutableStateOf(false)
        var expectedOffsetPx = 0f
        compose.setContent {
            MiuixTheme {
                expectedOffsetPx = with(LocalDensity.current) { 5.dp.toPx() }
                if (settings.value) SettingsPage({}, {}, {}) else {
                    // Same chrome and first real row, with only the shared page padding applied.
                    Scaffold(topBar = {
                        TopAppBar(title = "设置", navigationIcon = {
                            IconButton(onClick = {}, modifier = Modifier.size(BadgerSize.minimumTouchTarget)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                            }
                        })
                    }) { padding ->
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = mergePagePadding(padding,
                            PaddingValues(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.sm))) {
                            item {
                                Card(insideMargin = PaddingValues()) {
                                    ArrowPreference(title = "连接地址", summary = "尚未配置服务器", onClick = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        val baselineTop = compose.onNodeWithText("连接地址").fetchSemanticsNode().boundsInRoot.top
        val baselineBack = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { settings.value = true }
        assertThat(compose.onNodeWithText("连接地址").fetchSemanticsNode().boundsInRoot.top - baselineTop)
            .isWithin(POSITION_TOLERANCE_PX).of(expectedOffsetPx)
        assertThat(compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot)
            .isEqualTo(baselineBack)
    }

    private companion object {
        const val SCREEN = "settings_screen"
        const val LOWER_AREA_START = 0.75f
        const val PULL_STEP = 0.08f
        const val POSITION_TOLERANCE_PX = 1f
    }
}
