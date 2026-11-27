package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ScrollingPageChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun titleAndActionsStayPinnedAlignedAndClickableWhileTheFullViewportListScrolls() {
        var searches = 0
        compose.setContent {
            MiuixTheme {
                Scaffold(modifier = Modifier.fillMaxSize().testTag("screen"), progressiveTopBlur = true,
                    topBar = { PageActionBar(title = "待办标题") {
                        IconButton(onClick = { searches++ }) { Icon(Icons.Default.Search, "搜索待办") }
                    } }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().scrollTopFade().testTag("list"), contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
                        items(40) { Text("待办条目 $it", Modifier.height(80.dp)) }
                    }
                }
            }
        }
        val screen = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("list")
        assertThat(list.fetchSemanticsNode().boundsInRoot).isEqualTo(screen)
        val button = compose.onNodeWithContentDescription("搜索待办")
        val actionBounds = button.fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("待办标题")
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        assertThat(titleBounds.center.y).isWithin(1f).of(actionBounds.center.y)
        title.assertIsDisplayed()
        list.performTouchInput { swipeUp() }
        title.assertIsDisplayed()
        assertThat(title.fetchSemanticsNode().boundsInRoot).isEqualTo(titleBounds)
        assertThat(button.fetchSemanticsNode().boundsInRoot).isEqualTo(actionBounds)
        button.assertIsDisplayed().performTouchInput { click() }
        assertThat(searches).isEqualTo(1)
    }

    @Test
    fun optInSubtitleSitsOnTheTitleBottomEdgeWhileTheDefaultStaysCentred() {
        compose.setContent {
            MiuixTheme {
                Column {
                    PageActionBar(title = "待办", subtitle = "7条未完成", subtitleBottomAligned = true)
                    PageActionBar(title = "联系人", subtitle = "共 7 位联系人")
                }
            }
        }
        // Robolectric collapses both text heights, so only the small lift off the shared bottom edge is
        // observable here; the on-device bounds check covers the taller title1 line.
        val alignedTitle = compose.onNodeWithText("待办").fetchSemanticsNode().boundsInRoot
        val alignedSubtitle = compose.onNodeWithText("7条未完成").fetchSemanticsNode().boundsInRoot
        assertThat(alignedTitle.bottom - alignedSubtitle.bottom).isAtLeast(1f)
        assertThat(alignedTitle.bottom - alignedSubtitle.bottom).isAtMost(3f)
        // Pages that do not opt in keep the original centre-line pairing.
        val centredTitle = compose.onNodeWithText("联系人").fetchSemanticsNode().boundsInRoot
        val centredSubtitle = compose.onNodeWithText("共 7 位联系人").fetchSemanticsNode().boundsInRoot
        assertThat(centredSubtitle.center.y).isWithin(1f).of(centredTitle.center.y)
    }

    @Test
    fun pagerHostAddsNoRegionAndNestedPageOwnsTheOnlyFadeAndBlurBounds() {
        var hostHasRegion = true
        var pageRegion: TopEffectRegion? = null
        var bottomPx = 0f
        compose.setContent {
            MiuixTheme {
                bottomPx = with(LocalDensity.current) { BadgerSize.topEffectBottom.toPx() }
                Scaffold(progressiveTopBlur = false) {
                    hostHasRegion = LocalTopEffectRegion.current != null
                    Scaffold(topBar = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("nestedBar")) }) {
                        pageRegion = LocalTopEffectRegion.current?.value
                        LazyColumn(Modifier.fillMaxSize().scrollTopFade()) {
                            items(40) { Text("嵌套条目 $it", Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertThat(hostHasRegion).isFalse()
            assertThat(pageRegion).isNotNull()
            assertThat(checkNotNull(pageRegion).bottomScreenY).isWithin(1f).of(bottomPx)
        }
    }

    @Test
    fun insetAndTallerActionRowDefineFadeBottomWithoutBlockingTouches() {
        var searches = 0
        var contentClicks = 0
        var contentTopPaddingPx = 0f
        compose.setContent {
            MiuixTheme {
                val density = LocalDensity.current
                Scaffold(modifier = Modifier.fillMaxSize(), progressiveTopBlur = true, topBar = {
                    Box(Modifier.testTag("actionContainer").windowInsetsPadding(WindowInsets(top = 24.dp))) {
                        PageActionBar(actions = {
                            IconButton(onClick = { searches++ }, minHeight = 72.dp) {
                                Icon(Icons.Default.Search, "高操作行搜索")
                            }
                        })
                    }
                }) { padding ->
                    contentTopPaddingPx = with(density) { padding.calculateTopPadding().toPx() }
                    Box(Modifier.fillMaxSize()) {
                        Text("顶栏下方内容", Modifier.padding(top = padding.calculateTopPadding())
                            .fillMaxWidth().height(80.dp).clickable { contentClicks++ })
                    }
                }
            }
        }
        val container = compose.onNodeWithTag("actionContainer").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithContentDescription("高操作行搜索")
        val action = button.fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithText("顶栏下方内容")
        assertThat(action.top).isGreaterThan(container.top)
        assertThat(action.bottom).isWithin(0.001f).of(container.bottom)
        assertThat(content.fetchSemanticsNode().boundsInRoot.top).isWithin(0.001f).of(container.bottom)
        compose.runOnIdle { assertThat(contentTopPaddingPx).isWithin(0.001f).of(container.height) }
        button.performTouchInput { click() }
        content.performTouchInput { click() }
        compose.runOnIdle {
            assertThat(searches).isEqualTo(1)
            assertThat(contentClicks).isEqualTo(1)
        }
    }
}
