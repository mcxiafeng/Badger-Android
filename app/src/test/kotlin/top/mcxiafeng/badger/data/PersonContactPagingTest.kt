package top.mcxiafeng.badger.data

import android.database.sqlite.SQLiteException
import androidx.paging.PagingSource
import androidx.paging.Pager
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingDataEvent
import androidx.paging.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancelAndJoin
import top.mcxiafeng.badger.pages.person.PersonViewModel
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.pages.person.bookmark
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonContactPagingTest {
    private val handles = mutableListOf<AppDatabase>()
    private val queries = CopyOnWriteArrayList<String>()

    @After fun close() { handles.forEach { it.close() } }

    private fun database(index: Boolean = true): AppDatabase {
        val builder = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().setQueryCallback({ sql, _ -> queries.add(sql) }, { it.run() })
        if (index) builder.addCallback(ContactSearchIndex)
        return builder.build().also { handles.add(it) }
    }

    @Test fun mainPageHasStableTieOrderExcludesMyCardAndCountsLegacyInitials() = runBlocking {
        val db = database()
        db.contactDao().insertContact(Contact(id = -1, name = "Me", pinyinInitial = "A"))
        db.contactDao().insertContact(Contact(id = 3, name = "Same", pinyinInitial = "A"))
        db.contactDao().insertContact(Contact(id = 2, name = "Same", pinyinInitial = "A"))
        db.contactDao().insertContact(Contact(id = 1, name = "Legacy"))
        val source = db.contactDao().personPagingSource(PersonContactQueries.page(""))
        val page = load(source)
        assertThat(source.jumpingSupported).isTrue()
        assertThat(page.data.map { it.contact.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(db.contactDao().getLetterIndex().first()).containsExactly(LetterCount("#", 1), LetterCount("A", 2)).inOrder()
    }

    @Test fun searchKeepsTextAndTagGroupsExcludesTextHitsButAllowsCrossTagDuplicates() = runBlocking {
        val db = database()
        val text = db.contactDao().insertContact(Contact(name = "秋秋", pinyinInitial = "Q"))
        val first = db.contactDao().insertContact(Contact(name = "Ann", pinyinInitial = "A"))
        val second = db.contactDao().insertContact(Contact(name = "Bob", pinyinInitial = "B"))
        val tag1 = db.tagDao().insertTag(Tag(name = "秋日", pinyinInitial = "Q"))
        val tag2 = db.tagDao().insertTag(Tag(name = "秋友", pinyinInitial = "Q"))
        db.contactTagDao().insertCrossRefs(listOf(ContactTagCrossRef(text, tag1), ContactTagCrossRef(first, tag1),
            ContactTagCrossRef(first, tag2), ContactTagCrossRef(second, tag2)))
        val page = page(db, "秋")
        assertThat(page.data.filter { it.groupId == 0L }.map { it.contact.id }).containsExactly(text)
        assertThat(page.data.filter { it.groupId == tag1 }.map { it.contact.id }).containsExactly(first)
        assertThat(page.data.filter { it.groupId == tag2 }.map { it.contact.id }).containsExactly(first, second)
        assertThat(page.data.map { it.key }.distinct()).hasSize(4)
        assertThat(db.contactDao().getPersonContactIds(PersonContactQueries.ids("秋"))).containsExactly(text, first, second)
        val sections = db.contactDao().observePersonSections(PersonContactQueries.sections("秋")).first()
        assertThat(sections.sumOf { it.count }).isEqualTo(4)
    }

    @Test fun indexedSearchPreservesFieldPlatformDisplayAndChineseInfixesAndLiteralPunctuation() = runBlocking {
        val db = database()
        val fieldContact = db.contactDao().insertContact(Contact(name = "Field", pinyinInitial = "F"))
        val platformContact = db.contactDao().insertContact(Contact(name = "Platform", pinyinInitial = "P"))
        db.contactFieldValueDao().insertFieldValue(ContactFieldValue(contactId = fieldContact, value = "prefix-12%_'345-suffix"))
        db.contactPlatformDao().insertPlatform(ContactPlatform(contactId = platformContact, platformKey = "qq",
            value = "abCd123456ef", displayName = "杭州秋日同好"))
        assertThat(page(db, "%_'").data.map { it.contact.id }).containsExactly(fieldContact)
        assertThat(page(db, "CD123").data.map { it.contact.id }).containsExactly(platformContact)
        assertThat(page(db, "秋日").data.map { it.contact.id }).containsExactly(platformContact)
        assertThat(page(db, "日同好").data.map { it.contact.id }).containsExactly(platformContact)
        assertThat(page(db, "完全无匹配").data).isEmpty()
        assertThat(queries.filter { it.contains("person_rows") }.none { it.contains("LIKE", ignoreCase = true) }).isTrue()
    }

    @Test fun sourceInvalidatesForPlatformAndTagWritesAndTriggersDeleteStaleTerms() = runBlocking {
        val db = database()
        val id = db.contactDao().insertContact(Contact(name = "Ann", pinyinInitial = "A"))
        val platform = db.contactPlatformDao().insertPlatform(ContactPlatform(contactId = id, platformKey = "qq", value = "abc123"))
        val source = db.contactDao().personPagingSource(PersonContactQueries.page("123"))
        assertThat(load(source).data).hasSize(1)
        db.contactPlatformDao().insertPlatform(ContactPlatform(id = platform, contactId = id, platformKey = "qq", value = "def456"))
        db.invalidationTracker.refreshVersionsSync()
        assertThat(source.invalid).isTrue()
        assertThat(page(db, "123").data).isEmpty()
        assertThat(page(db, "456").data).hasSize(1)
        val tag = db.tagDao().insertTag(Tag(name = "活动"))
        db.contactTagDao().insertCrossRef(ContactTagCrossRef(id, tag))
        assertThat(page(db, "活动").data.map { it.groupId }).containsExactly(tag)
        db.tagDao().renameTag(tag, "展会", "Z")
        assertThat(page(db, "活动").data).isEmpty()
        assertThat(page(db, "展会").data).hasSize(1)
        db.contactDao().deleteByIds(listOf(id))
        assertThat(page(db, "456").data).isEmpty()
        assertThat(page(db, "展会").data).isEmpty()
    }

    @Test fun callbackBackfillsOnceAndRepeatedOpenDoesNotRewriteIndexOrChangeRoomVersion() = runBlocking {
        val db = database(index = false)
        val id = db.contactDao().insertContact(Contact(name = "Legacy", pinyinInitial = "L"))
        db.contactPlatformDao().insertPlatform(ContactPlatform(contactId = id, platformKey = "qq", value = "abc123"))
        val sql = db.openHelper.writableDatabase
        ContactSearchIndex.onOpen(sql)
        assertThat(page(db, "123").data).hasSize(1)
        val before = scalar(db, "SELECT total_changes()")
        ContactSearchIndex.onOpen(sql)
        ContactSearchIndex.onOpen(sql)
        assertThat(scalar(db, "SELECT total_changes()")).isEqualTo(before)
        assertThat(sql.version).isEqualTo(6)
    }

    @Test fun identityRankSurvivesDeleteBeforeAnchorRenameWithSameCountAndDeletedAnchor() = runBlocking {
        val db = database()
        listOf("Ann", "Bob", "Cara", "Dan").forEachIndexed { index, name ->
            db.contactDao().insertContact(Contact(id = index + 1L, name = name, pinyinInitial = "A"))
        }
        val anchor = page(db, "").data[2].bookmark(17)
        db.contactDao().deleteByIds(listOf(1))
        assertThat(rank(db, "", anchor)).isEqualTo(1)
        db.contactDao().updateContact(Contact(id = 3, name = "Zoe", pinyinInitial = "Z"))
        assertThat(rank(db, "", anchor)).isEqualTo(2)
        val renamed = page(db, "").data.last().bookmark(17)
        db.contactDao().deleteByIds(listOf(3))
        assertThat(rank(db, "", renamed)).isEqualTo(2) // UI clamps to final remaining row.
    }

    @Test fun searchAnchorIncludesTagIdentityAndSelectAllSpansUnloadedGroups() = runBlocking {
        val db = database()
        val id = db.contactDao().insertContact(Contact(name = "Ann", pinyinInitial = "A"))
        val first = db.tagDao().insertTag(Tag(name = "活动一"))
        val second = db.tagDao().insertTag(Tag(name = "活动二"))
        db.contactTagDao().insertCrossRefs(listOf(ContactTagCrossRef(id, first), ContactTagCrossRef(id, second)))
        val rows = page(db, "活动").data
        assertThat(rank(db, "活动", rows[1].bookmark(8))).isEqualTo(1)
        db.tagDao().deleteTagById(first)
        assertThat(rank(db, "活动", rows[1].bookmark(8))).isEqualTo(0)
    }

    @Test fun explicitLargeSelectionDeletesInAtomicBatchesAndRollsBackOnFailure() = runBlocking {
        val db = database()
        seedSynthetic(db, 1_001)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER refuse_contact_delete BEFORE DELETE ON contacts WHEN old.id = 950 BEGIN SELECT RAISE(ABORT, 'refuse contact delete'); END")
        try {
            db.contactDao().deleteContactsInBatches((1L..1_001L).toList())
            error("Deletion should have failed")
        } catch (failure: SQLiteException) {
            assertThat(failure.message).contains("refuse contact delete")
        }
        assertThat(scalar(db, "SELECT count(*) FROM contacts")).isEqualTo(1_001L)
        sql.execSQL("DROP TRIGGER refuse_contact_delete")
        db.contactDao().deleteContactsInBatches((1L..1_001L).toList())
        assertThat(scalar(db, "SELECT count(*) FROM contacts")).isEqualTo(0L)
    }

    @Test fun synthetic1000ContactsReadOnlyRequestedNearAndFarPages() = syntheticProbe(1_000)
    @Test fun synthetic10000ContactsReadOnlyRequestedNearAndFarPages() = syntheticProbe(10_000)

    private fun syntheticProbe(size: Int) = runBlocking {
        val db = database(index = false)
        val sql = db.openHelper.writableDatabase
        seedSynthetic(db, size)
        val beforeBytes = scalar(db, "PRAGMA page_count") * scalar(db, "PRAGMA page_size")
        val indexMs = measureTimeMillis { ContactSearchIndex.onOpen(sql) }
        val indexBytes = scalar(db, "PRAGMA page_count") * scalar(db, "PRAGMA page_size") - beforeBytes
        queries.clear()
        lateinit var near: PagingSource.LoadResult.Page<Int, PersonContactRow>
        lateinit var far: PagingSource.LoadResult.Page<Int, PersonContactRow>
        val nearMs = measureTimeMillis { near = page(db, "", 0, 60) }
        val farMs = measureTimeMillis { far = page(db, "", size - 60, 60) }
        val searchMs = measureTimeMillis { assertThat(page(db, "suffix", size - 60, 60).data).hasSize(60) }
        assertThat(near.data).hasSize(60)
        assertThat(near.itemsAfter).isEqualTo(size - 60)
        assertThat(far.data).hasSize(60)
        assertThat(far.itemsBefore).isEqualTo(size - 60)
        assertThat(far.data.first().contact.id).isEqualTo(size - 59L)
        assertThat(queries.count { it.contains("LIMIT", ignoreCase = true) && it.contains("OFFSET", ignoreCase = true) }).isAtLeast(3)
        val gramChars = scalar(db, "SELECT sum(length(grams)) FROM ${ContactSearchIndex.TABLE}")
        val retained = checkPresenterBoundAndJump(db, size)
        println("PERSON_PAGING_PROBE size=$size nearRows=${near.data.size} farRows=${far.data.size} nearMs=$nearMs farMs=$farMs searchPageMs=$searchMs indexBuildMs=$indexMs indexAllocatedBytes=$indexBytes gramChars=$gramChars retainedRows=$retained")
    }

    private fun seedSynthetic(db: AppDatabase, size: Int) {
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            val contact = sql.compileStatement("INSERT INTO contacts(id,name,pinyinInitial,createTime,updateTime) VALUES(?,?,'A',0,0)")
            val platform = sql.compileStatement("INSERT INTO contact_platforms(contactId,platformKey,value,jumpLink) VALUES(?,'qq',?,'')")
            contact.use { c -> platform.use { p -> repeat(size) { index ->
                val id = index + 1L
                c.bindLong(1, id); c.bindString(2, "Contact${id.toString().padStart(5, '0')}"); c.executeInsert()
                p.bindLong(1, id); p.bindString(2, "account$id-suffix"); p.executeInsert()
            } } }
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }

    private suspend fun checkPresenterBoundAndJump(db: AppDatabase, size: Int): Int = withContext(Dispatchers.Default.limitedParallelism(1)) {
        val updates = Channel<Unit>(Channel.CONFLATED)
        val presenter = object : PagingDataPresenter<PersonContactRow>(coroutineContext) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<PersonContactRow>) = Unit
        }
        presenter.addOnPagesUpdatedListener { updates.trySend(Unit) }
        val collection = launch {
            Pager(PersonViewModel.PAGING_CONFIG) { db.contactDao().personPagingSource(PersonContactQueries.page("")) }
                .flow.collectLatest { presenter.collectFrom(it) }
        }
        suspend fun awaitRow(index: Int) {
            withTimeout(PRESENTER_TIMEOUT_MS) {
                while (presenter.size == 0) updates.receive()
                presenter[index]
                while (presenter.peek(index) == null) updates.receive()
                presenter.loadStateFlow.filterNotNull().first { states ->
                    states.refresh !is LoadState.Loading && states.append !is LoadState.Loading && states.prepend !is LoadState.Loading
                }
            }
        }
        try {
            awaitRow(0)
            var largest = presenter.snapshot().items.size
            for (position in 45..345 step 30) {
                awaitRow(position)
                largest = maxOf(largest, presenter.snapshot().items.size)
            }
            assertThat(largest).isAtMost(PersonViewModel.MAX_CACHED_CONTACTS + PersonViewModel.PAGING_CONFIG.initialLoadSize)
            queries.clear()
            awaitRow(size - 30)
            val pageReads = queries.count { it.contains("LIMIT", true) && it.contains("OFFSET", true) }
            assertThat(pageReads).isAtMost(4) // A direct jump, rather than appending all intervening pages.
            assertThat(presenter.peek(size - 30)?.contact?.id).isEqualTo(size - 29L)
            assertThat(presenter.snapshot().items.size).isAtMost(PersonViewModel.MAX_CACHED_CONTACTS)
            largest
        } finally {
            collection.cancelAndJoin()
            updates.close()
        }
    }

    private companion object { const val PRESENTER_TIMEOUT_MS = 15_000L }

    private fun scalar(db: AppDatabase, sql: String): Long = db.openHelper.writableDatabase.query(sql).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
    private suspend fun rank(db: AppDatabase, query: String, anchor: PersonContactAnchor) = db.contactDao().getPersonAnchorPosition(PersonContactQueries.position(query, anchor))
    private suspend fun page(db: AppDatabase, query: String, key: Int = 0, size: Int = 60) = load(db.contactDao().personPagingSource(PersonContactQueries.page(query)), key, size)
    private suspend fun load(source: PagingSource<Int, PersonContactRow>, key: Int = 0, size: Int = 60): PagingSource.LoadResult.Page<Int, PersonContactRow> {
        val result = source.load(PagingSource.LoadParams.Refresh(key, size, true))
        check(result is PagingSource.LoadResult.Page) { "Page load failed: $result" }
        return result
    }
}
