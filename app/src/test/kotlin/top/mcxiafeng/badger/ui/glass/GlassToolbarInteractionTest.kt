package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassToolbarInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun groupedActionsKeepSeparateTargetsAndMenuRemainsClickable() {
        var searches = 0
        var choices = 0
        compose.setContent { ToolbarFixture(onSearch = { searches++ }, onChoice = { choices++ }) }
        val search = compose.onNodeWithContentDescription("搜索")
        val more = compose.onNodeWithContentDescription("更多")
        search.assertWidthIsAtLeast(BadgerSize.minimumTouchTarget)
            .assertHeightIsAtLeast(BadgerSize.minimumTouchTarget)
        more.assertWidthIsAtLeast(BadgerSize.minimumTouchTarget)
        val left = search.fetchSemanticsNode().boundsInRoot
        val right = more.fetchSemanticsNode().boundsInRoot
        assertThat(left.right).isEqualTo(right.left)
        assertThat(left.center.y).isEqualTo(right.center.y)
        search.performClick()
        assertThat(searches).isEqualTo(1)
        more.performClick()
        compose.onNodeWithText("新建").assertIsDisplayed().performClick()
        compose.onNodeWithText("新建").assertDoesNotExist()
        assertThat(choices).isEqualTo(1)
        assertThat(searches).isEqualTo(1)
    }

    @Test
    fun outsideTapDismissesMenuAndLeavesActionsUsable() {
        var searches = 0
        compose.setContent { ToolbarFixture(onSearch = { searches++ }, onChoice = {}) }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("新建").assertIsDisplayed()
        compose.onNodeWithTag("toolbarPopupWindow").performTouchInput { click(bottomCenter) }
        compose.onNodeWithText("新建").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").performClick()
        assertThat(searches).isEqualTo(1)
    }

    /** 液态关闭时组为不透明卡片色胶囊，子按钮仍可点击。 */
    @Test
    fun flatGroupKeepsSeparateTargetsAndActionsUsable() {
        var searches = 0
        var choices = 0
        compose.setContent { ToolbarFixture(onSearch = { searches++ }, onChoice = { choices++ }, material = false) }
        val search = compose.onNodeWithContentDescription("搜索")
        search.assertWidthIsAtLeast(BadgerSize.minimumTouchTarget)
        search.performClick()
        assertThat(searches).isEqualTo(1)
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("新建").assertIsDisplayed().performClick()
        assertThat(choices).isEqualTo(1)
    }
}

@Composable
private fun ToolbarFixture(
    onSearch: () -> Unit,
    onChoice: () -> Unit,
    material: Boolean = true,
) {
    MiuixTheme {
        CompositionLocalProvider(
            LocalGlassStyle provides GlassStyle(enabled = true, materialEnabled = material),
        ) {
            Scaffold {
                Box(Modifier.fillMaxSize().testTag("toolbarScreen")) {
                    GlassActionGroup {
                        IconButton(onClick = onSearch, minWidth = BadgerSize.minimumTouchTarget,
                            minHeight = BadgerSize.minimumTouchTarget) {
                            Icon(Icons.Default.Search, "搜索")
                        }
                        TestMoreMenu(onChoice)
                    }
                }
            }
        }
    }
}

@Composable
private fun TestMoreMenu(onChoice: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, minWidth = BadgerSize.minimumTouchTarget,
            minHeight = BadgerSize.minimumTouchTarget) {
            Icon(Icons.Default.MoreVert, "更多")
        }
        OverlayListPopup(show = expanded, popupModifier = Modifier.testTag("toolbarPopupWindow"),
            onDismissRequest = { expanded = false }) {
            ListPopupColumn {
                DropdownImpl("新建", optionSize = 1, isSelected = false, index = 0,
                    onSelectedIndexChange = {
                        expanded = false
                        onChoice()
                    })
            }
        }
    }
}
