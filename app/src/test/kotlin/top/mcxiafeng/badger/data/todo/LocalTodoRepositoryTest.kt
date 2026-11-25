package top.mcxiafeng.badger.data.todo

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountScope

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LocalTodoRepositoryTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var file: File
    private val contacts = MutableStateFlow(listOf(Contact(id = 1, name = "甲"), Contact(id = 2, name = "乙")))
    private val dao = mockk<ContactDao>()

    @Before fun setUp() {
        val base: Context = RuntimeEnvironment.getApplication()
        directory = File(base.cacheDir, "todo-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) { override fun getFilesDir(): File = directory }
        file = File(directory, TodoFileStore.FILE_NAME)
        every { dao.getAllContacts() } returns contacts
    }

    @After fun tearDown() { directory.deleteRecursively() }

    @Test fun completeCrudRetainsAllFieldsAndOtherRecordsAfterReload() = runTest {
        val repo = LocalTodoRepository(context, dao)
        val first = LocalTodo(title = " 跟进 ", note = "带资料", date = "2026-12-25", place = "图书馆",
            contactIds = listOf(1, 2, 1, -1, 999))
        val second = LocalTodo(title = "第二条")
        repo.save(first)
        repo.save(second)
        val reloaded = LocalTodoRepository(context, dao)
        reloaded.load()
        val original = reloaded.items.value.first { it.id == first.id }
        assertEquals(listOf(1L, 2L), original.contactIds)
        assertEquals("跟进", original.title)
        assertEquals("带资料", original.note)
        assertEquals("2026-12-25", original.date)
        assertEquals("图书馆", original.place)
        reloaded.save(original.copy(title = "新标题"), original)
        reloaded.toggle(first.id)
        assertTrue(reloaded.items.value.first { it.id == first.id }.completed)
        reloaded.toggle(first.id)
        reloaded.delete(second.id)
        repo.load()
        assertEquals("新标题", repo.items.value.single().title)
        assertFalse(repo.items.value.single().completed)
    }

    @Test fun concurrentWritersAndTogglesDoNotLoseUpdates() = runTest {
        val first = LocalTodoRepository(context, dao)
        val second = LocalTodoRepository(context, dao)
        (1..CONCURRENT_RECORDS).map { index -> async {
            val repo = if (index % 2 == 0) first else second
            repo.save(LocalTodo(id = "task-$index", title = "待办$index"))
        } }.awaitAll()
        first.load()
        assertEquals(CONCURRENT_RECORDS, first.items.value.size)
        listOf(async { first.toggle("task-1") }, async { second.toggle("task-1") }).awaitAll()
        assertFalse(second.items.value.first { it.id == "task-1" }.completed)
        assertEquals(first.items.value, second.items.value)
    }

    @Test fun staleEditorsCannotOverwriteCompletionOrResurrectDeletedTasks() = runTest {
        val repo = LocalTodoRepository(context, dao)
        repo.save(LocalTodo(title = "跟进"))
        val original = repo.items.value.single()
        repo.toggle(original.id)
        expectConflict { repo.save(original.copy(note = "旧编辑"), original) }
        assertTrue(repo.items.value.single().completed)
        repo.delete(original.id)
        expectConflict { repo.save(original.copy(note = "不能复活"), original) }
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun deletedContactsAreRemovedPersistentlyAndCannotBeRestoredByAnOpenEditor() = runTest {
        val repo = LocalTodoRepository(context, dao)
        repo.save(LocalTodo(title = "跟进", contactIds = listOf(1, 2)))
        val original = repo.items.value.single()
        contacts.value = contacts.value.filterNot { it.id == 1L }
        repo.load()
        assertEquals(listOf(2L), repo.items.value.single().contactIds)
        repo.save(original.copy(note = "保留编辑"), original)
        LocalTodoRepository(context, dao).load()
        assertEquals(listOf(2L), repo.items.value.single().contactIds)
        assertEquals("保留编辑", repo.items.value.single().note)
        assertFalse(file.readText().contains("\"contactIds\":[1"))
    }

    @Test fun fixedAccountContextsKeepTasksAndInFlightWritesInTheirOriginalSpace() = runTest {
        val local = AccountContext(context, AccountScope.local("todo-test"))
        val a = AccountContext(context, AccountScope.remote("server", "a", "epoch", "甲", "https://example.test"))
        val nextEpoch = AccountContext(context, AccountScope.remote("server", "a", "next", "甲", "https://example.test"))
        val oldRepo = LocalTodoRepository(local, dao)
        oldRepo.save(LocalTodo(id = "same-id", title = "本地"))
        val remote = LocalTodoRepository(a, dao)
        remote.save(LocalTodo(id = "same-id", title = "账号甲"))
        oldRepo.save(LocalTodo(title = "切换后旧操作"))
        val newGeneration = LocalTodoRepository(nextEpoch, dao)
        newGeneration.load()
        assertTrue(newGeneration.items.value.isEmpty())
        assertEquals(2, oldRepo.items.value.size)
        assertEquals("账号甲", remote.items.value.single().title)
        assertTrue(File(local.filesDir, TodoFileStore.FILE_NAME).exists())
        assertTrue(File(a.filesDir, TodoFileStore.FILE_NAME).exists())
    }

    @Test fun malformedFilesArePreservedAndNeverPublishedAsEmptySuccess() = runTest {
        val repo = LocalTodoRepository(context, dao)
        repo.save(LocalTodo(title = "已存在"))
        val previous = repo.items.value
        val invalidFiles = listOf("{broken", """[{"id":"x","title":"标题","date":"bad-date"}]""",
            """[{"id":"same","title":"一"},{"id":"same","title":"二"}]""")
        invalidFiles.forEach { malformed ->
            file.writeText(malformed)
            try {
                repo.save(LocalTodo(title = "不可覆盖"))
                fail("Expected invalid file failure")
            } catch (expected: Exception) {
                assertEquals(malformed, file.readText())
                assertEquals(previous, repo.items.value)
            }
        }
    }

    @Test fun writeFailureDoesNotPublishAnUnstoredTask() = runTest {
        val blocked = File(directory, "blocked").apply { writeText("keep") }
        val brokenContext = object : ContextWrapper(context) { override fun getFilesDir(): File = blocked }
        val repo = LocalTodoRepository(brokenContext, dao)
        try {
            repo.save(LocalTodo(title = "保存失败"))
            fail("Expected file write failure")
        } catch (expected: IOException) {
            assertTrue(repo.items.value.isEmpty())
            assertEquals("keep", blocked.readText())
        }
    }

    @Test fun legacyMinimalArraysAndAssociationGenerationRemainReadable() = runTest {
        file.writeText("""[{"id":"legacy","title":"旧待办"}]""")
        val repo = LocalTodoRepository(context, dao)
        repo.load()
        assertEquals("", repo.items.value.single().date)
        File(directory, TodoFileStore.GENERATION_FILE).writeText("current")
        file.writeText("""[{"id":"legacy","title":"旧待办","contactIds":[1],"associationGeneration":"old"}]""")
        repo.load()
        assertTrue(repo.items.value.single().contactIds.isEmpty())
        repo.save(LocalTodo(title = "新关联", contactIds = listOf(2)))
        assertEquals(listOf(2L), repo.items.value.last().contactIds)
    }

    @Test fun extendedFieldsSurviveReloadAndDeletedTypesBecomeUnassigned() = runTest {
        val repo = LocalTodoRepository(context, dao)
        val type = TodoType.custom("返图", TodoType.presets.first().color)
        repo.saveType(type)
        val item = LocalTodo(title = "选片", date = "2026-12-25", time = "14:30",
            reminder = TodoReminder(2, "09:00"), priority = TodoPriority.HIGH, typeId = type.id)
        repo.save(item)
        val reloaded = LocalTodoRepository(context, dao)
        reloaded.load()
        assertEquals(item, reloaded.items.value.single())
        assertEquals(type, reloaded.types.value.last())
        val renamed = type.copy(name = "活动")
        reloaded.saveType(renamed, type)
        assertEquals(renamed, repo.types.value.last())
        reloaded.deleteType(renamed)
        repo.save(item.copy(note = "补充"), item)
        reloaded.load()
        val edited = reloaded.items.value.single()
        assertEquals(item.copy(typeId = "", note = "补充"), edited.copy(updatedAt = null))
        assertTrue(checkNotNull(edited.updatedAt) > item.createdAt)
        assertEquals(TodoType.presets, reloaded.types.value)
    }

    @Test fun stagedBackgroundBecomesPersistentOnlyOnSaveAndRemovalCleansItUp() = runTest {
        val repo = LocalTodoRepository(context, dao)
        val bitmap = Bitmap.createBitmap(108, 23, Bitmap.Config.ARGB_8888)
        val id = try { repo.stageBackground(bitmap) } finally { bitmap.recycle() }
        val staged = checkNotNull(repo.backgroundFile(id, staged = true))
        val saved = checkNotNull(repo.backgroundFile(id))
        assertTrue(staged.isFile)
        assertFalse(saved.exists())
        val item = LocalTodo(title = "背景", backgroundId = id, backgroundScale = TodoBackgroundScale.FIT)
        repo.save(item)
        assertFalse(staged.exists())
        assertTrue(saved.isFile)
        repo.load()
        assertEquals(id, repo.items.value.single().backgroundId)
        assertEquals(TodoBackgroundScale.FIT, repo.items.value.single().backgroundScale)
        repo.save(item.copy(backgroundId = ""), item)
        assertFalse(saved.exists())
    }

    @Test fun savedManualOrderSurvivesEditingAndModeSwitch() = runTest {
        val repo = LocalTodoRepository(context, dao)
        val first = LocalTodo(id = "first", title = "第一条")
        val completed = LocalTodo(id = "completed", title = "已完成", completed = true)
        val second = LocalTodo(id = "second", title = "第二条")
        listOf(first, completed, second).forEach { repo.save(it) }
        val order = listOf(second.id, first.id, completed.id)
        repo.saveManualOrder(order)
        repo.save(second.copy(note = "新备注"), second)
        repo.setSortMode(TodoSortMode.CREATED)
        repo.setSortMode(TodoSortMode.MANUAL)

        val reloaded = LocalTodoRepository(context, dao)
        reloaded.load()
        assertEquals(TodoSortPreference(TodoSortMode.MANUAL, order), reloaded.sort.value)
        val records = reloaded.items.value.inManualOrder(reloaded.sort.value.manualOrder)
        assertEquals(order, records.map { it.id })
        assertEquals(first, records[1])
        assertTrue(records.last().completed)
    }

    @Test fun reminderUsesEarlierLocalCalendarDayAcrossDaylightSavingChange() {
        val zone = ZoneId.of("America/New_York")
        val item = LocalTodo(title = "跟进", date = "2026-03-09", time = "14:00",
            reminder = TodoReminder(2, "09:00"))
        assertEquals(Instant.parse("2026-03-07T14:00:00Z"), item.reminderAt(zone))
        assertEquals(null, item.copy(completed = true).reminderAt(zone))
    }

    private suspend fun expectConflict(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected stale editor rejection")
        } catch (expected: TodoConflictException) {
            assertTrue(expected.message!!.contains("修改或删除"))
        }
    }

    companion object { private const val CONCURRENT_RECORDS = 12 }
}
