package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Round-trips the full-page picker through [TodoContactPickResults] without a real nav stack. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h900dp")
class TodoEditorPickerFlowTest {
    @get:Rule val compose = createComposeRule()
    private val viewModel = mockk<TodoViewModel>(relaxed = true)
    private val owner = TestOwner()
    private var opens = 0
    private var token = ""
    private var preselected: List<Long> = emptyList()
    private val state = MutableStateFlow(
        TodoUiState(
            loaded = true, loading = false,
            items = listOf(LocalTodo(id = "t", title = "跟进", contactIds = listOf(1))),
            contacts = listOf(Contact(id = 1, name = "甲"), Contact(id = 2, name = "乙")),
        )
    )

    @Test fun addContactsOpensThePickerWithTheDraftSelection() {
        mount()
        compose.onNodeWithText("添加联系人").performClick()
        compose.runOnIdle {
            assertThat(opens).isEqualTo(1)
            assertThat(preselected).containsExactly(1L)
            assertThat(token).isNotEmpty()
        }
    }

    @Test fun publishedPicksReplaceTheDraftContactsWhenTheEditorResumes() {
        mount()
        compose.onNodeWithText("添加联系人").performClick()
        compose.runOnIdle {
            TodoContactPickResults.publish(token, listOf(2L))
            // The editor is covered by the picker: it leaves RESUMED and returns on pop.
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitForIdle()
        compose.onNodeWithText("乙").assertIsDisplayed()
        compose.onAllNodesWithText("甲").assertCountEquals(0)
        compose.runOnIdle {
            // The result is consumed exactly once; a second resume must not reapply stale picks.
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitForIdle()
        compose.onNodeWithText("乙").assertIsDisplayed()
        compose.runOnIdle { assertThat(TodoContactPickResults.consume(token)).isNull() }
    }

    @Test fun leavingTheEditorDropsAnUnconfirmedResult() {
        val mounted = mutableStateOf(true)
        mount(live = mounted)
        compose.onNodeWithText("添加联系人").performClick()
        compose.runOnIdle {
            TodoContactPickResults.publish(token, listOf(2L))
            // Tearing the editor down must release its token rather than leave a stale result.
            mounted.value = false
        }
        compose.waitForIdle()
        assertThat(TodoContactPickResults.consume(token)).isNull()
    }

    private fun mount(live: MutableState<Boolean>? = null) {
        every { viewModel.uiState } returns state
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    if (live == null || live.value) {
                        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                            TodoEditorPage(id = "t", onBack = {}, onManageTypes = {}, onContactClick = {},
                                onPickContacts = { pickToken, selected ->
                                    opens++
                                    token = pickToken
                                    preselected = selected
                                }, viewModel = viewModel)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}
