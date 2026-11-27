package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassMaterialTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allFourConfigurationsKeepStyledButtonsAndMaterialAloneSelectsTheFill() {
        var primary = Color.Unspecified
        val fills = mutableMapOf<Pair<Boolean, Boolean>, Color>()
        compose.setContent {
            MiuixTheme {
                val accent = MiuixTheme.colorScheme.primary
                val configured = buildMap {
                    listOf(false, true).forEach { floating ->
                        listOf(false, true).forEach { material ->
                            val style = GlassStyle(enabled = true, materialEnabled = material, floatingNavigation = floating)
                            assertThat(style.buttonsEnabled).isTrue()
                            assertThat(style.opticsEnabled).isEqualTo(material)
                            assertThat(style.copy(glassButtons = false).opticsEnabled).isEqualTo(material)
                            put(floating to material, style.buttonFill())
                        }
                    }
                }
                SideEffect { primary = accent; fills.putAll(configured) }
            }
        }
        compose.runOnIdle {
            listOf(false, true).forEach { floating ->
                assertThat(fills[floating to false]).isEqualTo(primary.copy(alpha = 1f))
                assertThat(fills[floating to true]).isEqualTo(primary.copy(alpha = BUTTON_TINT_ALPHA))
            }
        }
    }

    @Test
    fun flatFloatingChromeFlattensOntoTheCardSurfaceWhileGlassKeepsTheAccentTint() {
        var primary = Color.Unspecified
        var card = Color.Unspecified
        var cardVariant = Color.Unspecified
        val fills = mutableMapOf<Pair<Boolean, Boolean>, Color>()
        compose.setContent {
            MiuixTheme {
                val colors = MiuixTheme.colorScheme
                val configured = buildMap {
                    listOf(true, false).forEach { material ->
                        listOf(true, false).forEach { ready ->
                            val style = GlassStyle(enabled = true, materialEnabled = material)
                            put(material to ready, style.floatingFill(ready))
                        }
                    }
                }
                SideEffect {
                    primary = colors.primary
                    card = colors.surfaceContainer
                    cardVariant = colors.surfaceVariant
                    fills.putAll(configured)
                }
            }
        }
        compose.runOnIdle {
            assertThat(fills[true to true]).isEqualTo(primary.copy(alpha = BUTTON_TINT_ALPHA))
            // 亮色中性 tint 走 8-bit sRGB 量化，两侧对称比较。
            assertThat(fills[true to false]).isEqualTo(Color.Black.copy(alpha = BUTTON_TINT_ALPHA))
            assertThat(fills[false to true]).isEqualTo(card)
            assertThat(fills[false to false]).isEqualTo(cardVariant)
        }
    }

    @Test
    fun disablingMaterialKeepsTheIconTargetPressScaleAndCancelRecovery() {
        var clicks = 0
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = true, materialEnabled = false)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        IconButton({ clicks++ }, Modifier.testTag("solid_action")) { Text("操作") }
                    }
                }
            }
        }
        val button = compose.onNodeWithTag("solid_action")
        val original = button.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(BadgerMotion.DURATION_FAST + 48L)
        assertThat(button.fetchSemanticsNode().boundsInRoot.width / original.width)
            .isWithin(0.001f).of(BadgerTouchFeedback.PRESSED_SCALE)
        button.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(BadgerMotion.DURATION_FAST + 64L)
        compose.runOnIdle {
            assertThat(clicks).isEqualTo(0)
            assertThat(button.fetchSemanticsNode().boundsInRoot).isEqualTo(original)
        }
    }
}
