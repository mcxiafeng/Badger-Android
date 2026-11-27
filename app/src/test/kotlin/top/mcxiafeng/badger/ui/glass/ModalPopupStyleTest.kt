package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ModalPopupStyleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun modalFillUsesWhiteForGlassAndOpaqueCardColorForFlatThemes() {
        val card = Color(0xFFE4E7EF)
        listOf(false, true).forEach { material ->
            listOf(false, true).forEach { dark ->
                val style = GlassStyle(enabled = true, materialEnabled = material, dark = dark)
                val expected = if (material) Color.White.copy(alpha = 0.09f) else card
                assertThat(modalPopupFill(style, card)).isEqualTo(expected)
            }
        }
    }

    @Test
    fun flatDarkFillUsesFullyOpaqueCardColorWithoutOpticalMaterial() {
        val style = GlassStyle(enabled = true, materialEnabled = false, dark = true)
        val card = Color(0xFF24282E)
        val fill = modalPopupFill(style, card.copy(alpha = 0.4f))
        assertThat(style.opticsEnabled).isFalse()
        assertThat(fill).isEqualTo(card)
        assertThat(fill.alpha).isEqualTo(1f)
    }

    @Test
    fun modalPanelBodyUsesTheElevatedContainerInGlassModeAndTheCardInFlatMode() {
        val glass = GlassStyle(enabled = true, materialEnabled = true)
        val flat = GlassStyle(enabled = true, materialEnabled = false)
        val card = Color(0xFFFFFFFF)
        val elevated = Color(0xFFE8E8E8)
        assertThat(modalPanelBase(glass, card, elevated)).isEqualTo(elevated)
        assertThat(modalPanelBase(flat, card, elevated)).isEqualTo(card)
    }

    @Test
    fun modalDimUsesFifteenPercentBlackAndClampsReboundOvershoot() {
        assertThat(modalPopupDimAlpha(-0.1f)).isEqualTo(0f)
        assertThat(modalPopupDimAlpha(0f)).isEqualTo(0f)
        assertThat(modalPopupDimAlpha(0.5f)).isWithin(0.0001f).of(0.075f)
        assertThat(modalPopupDimAlpha(1f)).isEqualTo(0.15f)
        assertThat(modalPopupDimAlpha(1.1f)).isEqualTo(0.15f)
    }

    @Test
    fun enteringAndExitingEachHaveTheirOwn239MillisecondSegment() {
        assertThat(popupMotionSpec(entering = true).durationMillis).isEqualTo(239)
        assertThat(popupMotionSpec(entering = false).durationMillis).isEqualTo(239)
    }

    @Test
    fun nestedButtonDropdownDoesNotInheritTheModalWindowDimmingPolicy() {
        var modalSeen: Boolean? = null
        var dropdownSeen: Boolean? = null
        compose.setContent {
            MiuixTheme {
                val modal = remember { PopupMotionState() }
                PopupWindow(modal, onDismissRequest = {}) {
                    val modalContext = LocalModalPopupWindow.current
                    SideEffect { modalSeen = modalContext }
                    val dropdown = remember { PopupMotionState() }
                    PopupWindow(dropdown, onDismissRequest = {}, dropdown = true) {
                        val dropdownContext = LocalModalPopupWindow.current
                        SideEffect { dropdownSeen = dropdownContext }
                        Text("按钮菜单")
                    }
                }
            }
        }
        compose.runOnIdle {
            assertThat(modalSeen).isTrue()
            assertThat(dropdownSeen).isFalse()
        }
    }
}
