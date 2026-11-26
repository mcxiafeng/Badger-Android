package top.mcxiafeng.badger.ui.glass

import android.app.Application
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.designsystem.BadgerTouchFeedback
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ButtonTouchInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private lateinit var feedback: ButtonTouchFeedback

    @Test
    fun pressFadesOnlyContentSmoothlyAndReleaseRestoresItWhileClickStillFires() {
        mountTouchTarget()
        val target = compose.onNodeWithTag("target")
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle {
            assertThat(feedback.contentAlpha.value).isGreaterThan(0.5f)
            assertThat(feedback.contentAlpha.value).isLessThan(1f)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertThat(feedback.contentAlpha.value).isEqualTo(0.5f)
            assertThat(feedback.glow.value).isEqualTo(1f)
        }
        target.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertThat(clicks).isEqualTo(1)
            assertThat(feedback.contentAlpha.value).isEqualTo(1f)
            assertThat(feedback.glow.value).isEqualTo(0f)
            assertThat(feedback.follow.translation.value).isEqualTo(0f)
        }
    }

    @Test
    fun dragFollowsWithinFiveDpAndCancellationRestoresWithoutClicking() {
        mountTouchTarget()
        val target = compose.onNodeWithTag("target")
        target.performTouchInput {
            down(Offset(2f, center.y))
            moveTo(Offset(width - 2f, center.y))
        }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle {
            val limit = with(compose.density) { BadgerTouchFeedback.dragFollowLimit.toPx() }
            assertThat(feedback.follow.translation.value).isGreaterThan(0f)
            assertThat(feedback.follow.translation.value).isAtMost(limit)
        }
        target.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertThat(clicks).isEqualTo(0)
            assertThat(feedback.follow.translation.value).isEqualTo(0f)
            assertThat(feedback.contentAlpha.value).isEqualTo(1f)
            assertThat(feedback.scale.scale.value).isEqualTo(1f)
        }
    }

    @Test
    fun sharedGlassButtonsNeverCreateTheNativeMaskAndAllStillRespondToTouch() = verifySharedButtons(material = true)

    @Test
    fun opaqueMaterialKeepsButtonActionsAndDoesNotRestoreTheNativePressMask() = verifySharedButtons(material = false)

    private fun verifySharedButtons(material: Boolean) {
        val nativeMask = CountingIndication()
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = true, materialEnabled = material), LocalIndication provides nativeMask) {
                    Column {
                        IconButton({ clicks++ }, Modifier.testTag("icon")) { Text("图标") }
                        Button({ clicks++ }, Modifier.testTag("button")) { Text("按钮") }
                        TextButton("文字", { clicks++ }, Modifier.testTag("text"))
                        FloatingActionButton({ clicks++ }, Modifier.testTag("fab")) { Text("悬浮") }
                        MaterialIconButton({ clicks++ }, Modifier.testTag("material")) { Text("兼容") }
                    }
                }
            }
        }
        listOf("icon", "button", "text", "fab", "material").forEach { tag ->
            compose.onNodeWithTag(tag).performTouchInput { click() }
        }
        compose.runOnIdle {
            assertThat(nativeMask.created).isEqualTo(0)
            assertThat(clicks).isEqualTo(5)
        }
    }

    private fun mountTouchTarget() {
        compose.setContent {
            feedback = checkNotNull(rememberButtonTouchFeedback(true))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(48.dp).testTag("target")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { clicks++ }
                    .glassPressFeedback(true, 24.dp, feedback)) {
                    Text("操作", Modifier.buttonPressContentAlpha(feedback))
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    private class CountingIndication : IndicationNodeFactory {
        var created = 0
        override fun create(interactionSource: InteractionSource): DelegatableNode {
            created++
            return object : Modifier.Node() {}
        }
        override fun equals(other: Any?): Boolean = other === this
        override fun hashCode(): Int = javaClass.hashCode()
    }
}
