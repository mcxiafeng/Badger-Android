package top.mcxiafeng.badger.pages.todo

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.data.todo.LocalTodoRepository
import top.mcxiafeng.badger.ui.glass.GlassStyle
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SAVE_TIMEOUT_MS = 5_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h900dp")
class TodoEditorSaveTest {
    @get:Rule val compose = createComposeRule()
    private val dao = mockk<ContactDao>()
    private val contacts = mockk<ContactRepository>()
    private val viewModels = ViewModelStore()
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var repository: LocalTodoRepository
    private lateinit var viewModel: TodoViewModel
    private var closed = false
    private var closes = 0
    private var itemsAtClose = emptyList<LocalTodo>()

    @Before
    fun setUp() {
        val base: Context = RuntimeEnvironment.getApplication()
        directory = File(base.cacheDir, "todo-editor-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) { override fun getFilesDir(): File = directory }
        every { dao.getAllContacts() } returns flowOf(emptyList())
        every { contacts.getAllContacts() } returns flowOf(emptyList())
        every { contacts.searchContacts(any()) } returns flowOf(emptyList())
        repository = LocalTodoRepository(context, dao)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        directory.deleteRecursively()
    }

    @Test
    fun glassSaveTouchPersistsNewDraftBeforeClosing() {
        mount(glass = true)
        saveButton(creating = true).assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertThat(closes).isEqualTo(0) }
        title().performTextInput("联系圈友")
        saveButton(creating = true).assertIsEnabled().performTouchInput { click() }
        assertSaved("联系圈友")
    }

    @Test
    fun flatSaveTouchPersistsNewDraftBeforeClosing() {
        mount(glass = false)
        title().performTextInput("安排见面")
        saveButton(creating = true).assertIsEnabled().performTouchInput { click() }
        assertSaved("安排见面")
    }

    @Test
    fun glassSaveTouchUpdatesTheOpenedTaskWithoutDuplicatingIt() {
        val original = LocalTodo(id = "existing", title = "原待办", note = "保留备注")
        runBlocking { repository.save(original) }
        mount(glass = true, id = original.id)
        compose.waitUntil { viewModel.uiState.value.items.isNotEmpty() }
        title().performTextReplacement("已修改标题")
        saveButton(creating = false).assertIsEnabled().performTouchInput { click() }
        val saved = assertSaved("已修改标题")
        assertThat(saved.id).isEqualTo(original.id)
        assertThat(saved.note).isEqualTo(original.note)
    }

    @Test
    fun validUnchangedTaskCanFinishWithTheHighlightedCheckButton() {
        val original = LocalTodo(id = "unchanged", title = "已有待办")
        runBlocking { repository.save(original) }
        mount(glass = true, id = original.id)
        compose.waitUntil { viewModel.uiState.value.loaded }
        saveButton(creating = false).assertIsEnabled().performTouchInput { click() }
        assertThat(assertSaved(original.title)).isEqualTo(repository.items.value.single())
    }

    @Test
    fun clearingRequiredTitleDisablesSaveAndKeepsTheStoredTask() {
        val original = LocalTodo(id = "required", title = "不可丢失")
        runBlocking { repository.save(original) }
        mount(glass = true, id = original.id)
        compose.waitUntil { viewModel.uiState.value.loaded }
        title().performTextReplacement("")
        saveButton(creating = false).assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertThat(closes).isEqualTo(0) }
        assertThat(reload().single().title).isEqualTo(original.title)
    }

    private fun mount(glass: Boolean, id: String = "") {
        viewModel = TodoViewModel(repository, contacts, mockk<TagRepository>())
        viewModels.put("editor", viewModel)
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalGlassStyle provides GlassStyle(enabled = glass)) {
                    TodoEditorPage(id, onBack = {
                        itemsAtClose = repository.items.value
                        closes++
                        closed = true
                    }, onManageTypes = {}, onContactClick = {}, viewModel = viewModel)
                }
            }
        }
        compose.waitUntil { viewModel.uiState.value.loaded }
        compose.waitForIdle()
    }

    private fun title() = compose.onAllNodes(hasSetTextAction())[0]
    private fun saveButton(creating: Boolean) = compose.onNodeWithContentDescription(
        if (creating) "保存待办" else "保存修改",
    )

    private fun assertSaved(title: String): LocalTodo {
        try {
            compose.waitUntil(timeoutMillis = SAVE_TIMEOUT_MS) {
                // Real file IO resumes its callback on Robolectric's paused Android main looper.
                ShadowLooper.idleMainLooper()
                closed
            }
        } catch (error: AssertionError) {
            val messages = ShadowLog.getLogsForTag("TodoViewModelTester").joinToString { it.msg }
            throw AssertionError("Save did not close: saving=${viewModel.uiState.value.saving}; error=${viewModel.uiState.value.error}; stored=${repository.items.value.size}; logs=$messages", error)
        }
        compose.runOnIdle {
            assertThat(closes).isEqualTo(1)
            assertThat(itemsAtClose.single().title).isEqualTo(title)
        }
        return reload().single().also { assertThat(it.title).isEqualTo(title) }
    }

    private fun reload(): List<LocalTodo> = runBlocking {
        LocalTodoRepository(context, dao).also { it.load() }.items.value
    }
}
