package top.mcxiafeng.badger.ui.glass

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Flat mode must hand the panel to Miuix's own dialog and list popup instead of the retained glass window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FlatModeNativePopupTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun flatMenuPanelLiftsOntoTheElevatedContainerInsteadOfTheTransparentHost() {
        assertMenuSurface(flat = true)
    }

    @Test
    fun glassMenuPanelStillClearsItsHostSurface() {
        assertMenuSurface(flat = false)
    }

    private fun assertMenuSurface(flat: Boolean) {
        val panelColor = mutableMapOf<String, Color>()
        compose.setContent {
            MiuixTheme {
                val pageColor = MiuixTheme.colorScheme.surfaceContainer
                val elevatedColor = MiuixTheme.colorScheme.surfaceContainerHigh
                CompositionLocalProvider(
                    LocalGlassStyle provides GlassStyle(enabled = true, materialEnabled = !flat),
                ) {
                    Scaffold {
                        Box(Modifier.fillMaxSize()) {
                            OverlayListPopup(show = true, onDismissRequest = {}) {
                                val menuColor = MiuixTheme.colorScheme.surfaceContainer
                                SideEffect { panelColor["menu"] = menuColor }
                                ListPopupColumn { Text("扁平菜单项") }
                            }
                            SideEffect {
                                panelColor["page"] = pageColor
                                panelColor["elevated"] = elevatedColor
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.flushPopupUi()
        Log.d(LOG_TAG, "Menu panel colors; flat=$flat; page=${panelColor["page"]}; elevated=${panelColor["elevated"]}; panel=${panelColor["menu"]}")
        compose.onNodeWithText("扁平菜单项").assertIsDisplayed()
        compose.runOnIdle {
            if (flat) assertThat(panelColor["menu"]).isEqualTo(panelColor["elevated"])
            else assertThat(panelColor["menu"]).isEqualTo(Color.Transparent)
            assertThat(panelColor["page"]).isNotEqualTo(Color.Transparent)
        }
    }

    @Test
    fun flatDialogRendersThroughMiuixNativeWindowDialog() {
        var dismissed = false
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = true, materialEnabled = false)) {
                    WindowDialog(show = true, title = "扁平弹窗", onDismissRequest = { dismissed = true }) {
                        Text("扁平弹窗内容")
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.flushPopupUi()
        compose.onNodeWithText("扁平弹窗内容").assertIsDisplayed()
        compose.runOnIdle {
            assertThat(dismissed).isFalse()
            Log.d(LOG_TAG, "Flat dialog content displayed; dismissed=$dismissed")
        }
    }

    @Test
    fun flatDialogKeepsTheRetainedExitHandoffWhenItsCallerUnmounts() {
        var show by mutableStateOf(true)
        compose.setContent {
            RetainedPopupHost {
                MiuixTheme {
                    CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = true, materialEnabled = false)) {
                        if (show) {
                            WindowDialog(show = true, title = "扁平弹窗") { Text("扁平弹窗内容") }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.flushPopupUi()
        compose.onNodeWithText("扁平弹窗内容").assertIsDisplayed()
        show = false
        Log.d(LOG_TAG, "Flat dialog caller unmounted; retained native exit hand-off expected")
        compose.mainClock.autoAdvance = false
        repeat(EXIT_HANDOFF_FRAMES) { compose.advancePopupFrame() }
        compose.flushPopupUi()
        compose.runOnIdle { compose.onAllNodesWithText("扁平弹窗内容").assertCountEquals(0) }
    }

    private companion object {
        const val LOG_TAG = "FlatModeNativePopupTester"
        const val EXIT_HANDOFF_FRAMES = 16
    }
}
