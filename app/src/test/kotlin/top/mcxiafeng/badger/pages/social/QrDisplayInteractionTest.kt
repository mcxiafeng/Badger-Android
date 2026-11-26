package top.mcxiafeng.badger.pages.social

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLog
import top.mcxiafeng.badger.ui.glass.GlassStyle
import top.mcxiafeng.badger.ui.glass.LocalAppDarkTheme
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.mcxiafeng.badger.ui.glass.advancePopupFrame
import top.mcxiafeng.badger.ui.glass.flushPopupUi
import top.mcxiafeng.badger.ui.glass.startPopupMotionFrames
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private const val MOTION_DURATION_MS = 239L
private const val EXIT_MIDPOINT_MS = MOTION_DURATION_MS / 2
private const val LAST_MILLISECOND = 1L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "h1000dp-night")
class QrDisplayInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun resetDirection() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("social_prefs", 0)
            .edit().putBoolean("qr_inverted", false).commit()
    }

    @Test fun glassPopupKeepsIdentityAndCallerThemeAcrossDirectionChanges() = verifyPopup(glass = true)
    @Test fun solidPopupKeepsIdentityAndCallerThemeAcrossDirectionChanges() = verifyPopup(glass = false)

    @Test fun opaqueMaterialWithFloatingNavigationKeepsQrAndDirectionChanges() = verifyPopup(true, materialEnabled = false)
    @Test fun opaqueMaterialWithClassicNavigationKeepsQrAndDirectionChanges() = verifyPopup(true, materialEnabled = false, floating = false)

    private fun verifyPopup(glass: Boolean, materialEnabled: Boolean = true, floating: Boolean = true) {
        val visible = mutableStateOf(true)
        val name = mutableStateOf("自设昵称")
        compose.setContent {
            PopupFixture(visible, name.value, glass, materialEnabled, floating)
        }
        repeat(4) {
            compose.onNodeWithTag("qr_display_name", useUnmergedTree = true).assertTextEquals(name.value)
            assertNameUsesCallerLightTheme()
            assertThat(compose.onAllNodesWithText("QQ", useUnmergedTree = true).fetchSemanticsNodes()).hasSize(1)
            compose.onNodeWithText("123456789").assertIsDisplayed()
            compose.onNodeWithTag("qr_display_avatar").performClick()
            compose.waitForIdle()
        }
        compose.runOnIdle { name.value = "新抓取昵称" }
        compose.onNodeWithTag("qr_display_name", useUnmergedTree = true).assertTextEquals("新抓取昵称")
        if (!materialEnabled) compose.runOnIdle {
            val surfaces = ShadowLog.getLogsForTag("GlassSurfaceTester").filter { it.msg.startsWith("Solid material;") }
            assertThat(surfaces).isNotEmpty()
            assertThat(surfaces.all { it.msg.contains("fillAlpha=1.0") && it.msg.contains("backgroundBlur=false") }).isTrue()
        }
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag("qr_display_panel").assertDoesNotExist()
    }

    @Test fun bottomGlassExitSurvivesCallerRemoval() = verifyExit(false, true, removeCaller = true)
    @Test fun topGlassExitSurvivesCallerRemoval() = verifyExit(true, true, removeCaller = true)
    @Test fun bottomOpaqueExitSurvivesCallerRemoval() = verifyExit(false, false, removeCaller = true)
    @Test fun topOpaqueExitSurvivesCallerRemoval() = verifyExit(true, false, removeCaller = true)

    @Test fun bottomGlassExitSurvivesHiddenFlag() = verifyExit(false, true, removeCaller = false)
    @Test fun topGlassExitSurvivesHiddenFlag() = verifyExit(true, true, removeCaller = false)
    @Test fun bottomOpaqueExitSurvivesHiddenFlag() = verifyExit(false, false, removeCaller = false)
    @Test fun topOpaqueExitSurvivesHiddenFlag() = verifyExit(true, false, removeCaller = false)

    private fun verifyExit(inverted: Boolean, materialEnabled: Boolean, removeCaller: Boolean) {
        val visible = mountMotionFixture(inverted, materialEnabled, removeCaller)
        val dialog = compose.runOnIdle { checkNotNull(ShadowDialog.getLatestDialog()) }
        val disposedBefore = panelDisposals()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible.value = false }
        compose.startPopupMotionFrames()
        advanceMotionBy(EXIT_MIDPOINT_MS)
        compose.runOnIdle {
            assertThat(dialog.isShowing).isTrue()
            assertThat(panelDisposals()).isEqualTo(disposedBefore)
        }
        // Closing content is still drawn, but the shared window hides its interaction semantics.
        compose.onNodeWithText("自设昵称").assertDoesNotExist()
        advanceMotionBy(MOTION_DURATION_MS - LAST_MILLISECOND - EXIT_MIDPOINT_MS)
        compose.runOnIdle {
            assertThat(dialog.isShowing).isTrue()
            assertThat(panelDisposals()).isEqualTo(disposedBefore)
        }
        finishMotionFrames()
        compose.runOnIdle {
            assertThat(dialog.isShowing).isFalse()
            assertThat(panelDisposals()).isEqualTo(disposedBefore + 1)
        }
        compose.onNodeWithTag("qr_display_panel").assertDoesNotExist()
    }

    @Test fun glassDirectionChangesRetainOutgoingPanelFor239ms() = verifyDirectionMotion(materialEnabled = true)
    @Test fun opaqueDirectionChangesRetainOutgoingPanelFor239ms() = verifyDirectionMotion(materialEnabled = false)

    private fun verifyDirectionMotion(materialEnabled: Boolean) {
        mountMotionFixture(inverted = false, materialEnabled = materialEnabled, removeCaller = false)
        compose.mainClock.autoAdvance = false
        repeat(2) { index ->
            val disposedBefore = panelDisposals()
            compose.onNodeWithTag("qr_display_avatar").performClick()
            // AnimatedContent starts on the first transition frame. The retained host's
            // two-frame Animatable setup would already consume one frame of this animation.
            compose.advancePopupFrame()
            val directionStartTime = compose.mainClock.currentTime
            advanceMotionBy(EXIT_MIDPOINT_MS)
            compose.runOnIdle { assertThat(panelDisposals()).isEqualTo(disposedBefore) }
            advanceMotionBy(MOTION_DURATION_MS - LAST_MILLISECOND - EXIT_MIDPOINT_MS)
            compose.runOnIdle {
                assertThat(compose.mainClock.currentTime - directionStartTime).isEqualTo(MOTION_DURATION_MS - LAST_MILLISECOND)
                assertThat(panelDisposals()).isEqualTo(disposedBefore)
            }
            finishMotionFrames()
            compose.runOnIdle {
                assertThat(panelDisposals()).isEqualTo(disposedBefore + 1)
                val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("social_prefs", 0)
                assertThat(preferences.getBoolean("qr_inverted", false)).isEqualTo(index == 0)
            }
            compose.onNodeWithTag("qr_display_name", useUnmergedTree = true).assertTextEquals("自设昵称")
            compose.onNodeWithTag("qr_display_image", useUnmergedTree = true).assertIsDisplayed()
            assertNameUsesCallerLightTheme()
        }
    }

    private fun mountMotionFixture(inverted: Boolean, materialEnabled: Boolean, removeCaller: Boolean): MutableState<Boolean> {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("social_prefs", 0)
            .edit().putBoolean("qr_inverted", inverted).commit()
        val visible = mutableStateOf(true)
        compose.setContent { PopupFixture(visible, "自设昵称", materialEnabled = materialEnabled, removeCaller = removeCaller) }
        compose.onNodeWithTag("qr_display_image", useUnmergedTree = true).assertIsDisplayed()
        assertNameUsesCallerLightTheme()
        return visible
    }

    private fun panelDisposals(): Int = ShadowLog.getLogsForTag(QR_DISPLAY_LOG_TAG)
        .count { it.msg.startsWith("二维码面板已卸载；") }

    private fun advanceMotionBy(durationMs: Long) {
        compose.mainClock.advanceTimeBy(durationMs, ignoreFrameDuration = true)
        compose.flushPopupUi()
    }

    private fun finishMotionFrames() {
        advanceMotionBy(LAST_MILLISECOND)
        // 239ms ends between vsyncs: sample completion, then commit native-window disposal.
        repeat(2) { compose.advancePopupFrame() }
        compose.waitForIdle()
    }

    @Composable
    private fun PopupFixture(
        visible: MutableState<Boolean>, name: String, glass: Boolean = true,
        materialEnabled: Boolean = true, floating: Boolean = true, removeCaller: Boolean = false,
    ) {
        val rootTheme = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark) }
        val callerTheme = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Light) }
        // Keep this host outside the caller's if branch to exercise real retained disposal.
        RetainedPopupHost {
            MiuixTheme(controller = rootTheme) {
                Scaffold {
                    if (!removeCaller || visible.value) {
                        MiuixTheme(controller = callerTheme) {
                            CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = glass,
                                materialEnabled = materialEnabled, floatingNavigation = floating), LocalAppDarkTheme provides false) {
                                QrCodeExpandedView("https://example.com/test", visible.value, { visible.value = false },
                                    name, "QQ", "123456789", null)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun assertNameUsesCallerLightTheme() {
        compose.onNodeWithTag("qr_display_name", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                assertThat(action(layouts)).isTrue()
                val text = layouts.single().layoutInput.style.color
                assertThat(text.red + text.green + text.blue).isLessThan(1.5f)
            }
    }
}
