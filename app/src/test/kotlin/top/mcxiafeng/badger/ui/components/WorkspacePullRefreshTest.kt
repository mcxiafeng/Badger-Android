package top.mcxiafeng.badger.ui.components

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WorkspacePullRefreshTest {
    @get:Rule val compose = createComposeRule()

    private var refreshCount = 0

    @Test
    fun subFrameRefreshingPulseStillRetractsTheHeader() {
        val refreshing = MutableStateFlow(false)
        mount(refreshing) {
            // 亚帧脉冲：true→false 同步完成，重组可能永远观察不到 true。
            refreshing.value = true
            refreshing.value = false
        }
        pullAndAssertRowReturnsToRest()
    }

    @Test
    fun noopRefreshCallbackStillRetractsTheHeader() {
        // 回调被丢弃（如账号任务 busy）：refreshing 从未置起也必须收尾。
        mount(MutableStateFlow(false)) {}
        pullAndAssertRowReturnsToRest()
    }


    private fun mount(refreshing: MutableStateFlow<Boolean>, onRefresh: () -> Unit) {
        refreshCount = 0
        compose.setContent {
            MiuixTheme {
                WorkspacePullRefresh(refreshing, {
                    refreshCount++
                    onRefresh()
                }, Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items((0 until 40).toList()) { index -> Text("第 $index 行") }
                    }
                }
            }
        }
    }

    private fun pullAndAssertRowReturnsToRest() {
        val restTop = compose.onNodeWithText("第 0 行").fetchSemanticsNode().boundsInRoot.top
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.waitUntil(5_000L) { rowBackAtRest(restTop) }
        // 手势必须真实触发过刷新，防止“从未拉起”的空转通过。
        assertThat(refreshCount).isAtLeast(1)
    }

    private fun rowBackAtRest(restTop: Float): Boolean =
        compose.onNodeWithText("第 0 行").fetchSemanticsNode().boundsInRoot.top <= restTop + 1f
}
