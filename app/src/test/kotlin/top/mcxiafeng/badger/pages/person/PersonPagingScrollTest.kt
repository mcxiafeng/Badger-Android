package top.mcxiafeng.badger.pages.person

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.LoadState
import org.robolectric.Shadows.shadowOf
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactSearchIndex
import top.mcxiafeng.badger.data.PersonContactQueries
import top.mcxiafeng.badger.data.PersonContactRow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PersonPagingScrollTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var state: LazyListState
    private lateinit var rows: LazyPagingItems<PersonContactRow>
    private lateinit var scope: CoroutineScope
    private lateinit var anchor: PersonScrollAnchor

    @Before fun setup() = runBlocking {
        ShadowLog.clear()
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().addCallback(ContactSearchIndex).build()
        repeat(CONTACT_COUNT) { index -> db.contactDao().insertContact(contact(index + 1L)) }
    }

    @After fun close() { db.close() }

    @Test fun deletionBeforeVisibleIdentityRestoresTheSameContactAndOffset() {
        mountAtAnchor()
        runBlocking { db.contactDao().deleteContactsInBatches((1L..50L).toList()) }
        awaitAnchor(EXPECTED_ANCHOR_ID, 150)
    }

    @Test fun sameCountReorderRestoresIdentityWithoutWaitingForCountChange() {
        mountAtAnchor()
        runBlocking { db.contactDao().updateContact(contact(1).copy(name = "Zoe", pinyinInitial = "Z")) }
        awaitAnchor(EXPECTED_ANCHOR_ID, 199)
    }

    @Test fun deletingTheVisibleAnchorKeepsItsNextNeighborAtTheSameOffset() {
        mountAtAnchor()
        runBlocking { db.contactDao().deleteByIds(listOf(EXPECTED_ANCHOR_ID)) }
        awaitAnchor(EXPECTED_ANCHOR_ID + 1, 200)
    }

    private fun mountAtAnchor() {
        val pager = Pager(PersonViewModel.PAGING_CONFIG) { db.contactDao().personPagingSource(PersonContactQueries.page("")) }
        composeRule.setContent {
            rows = pager.flow.collectAsLazyPagingItems()
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            anchor = rememberPersonScrollAnchor(rows, state, 0) { anchor ->
                db.contactDao().getPersonAnchorPosition(PersonContactQueries.position("", anchor))
            }
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                items(rows.itemCount, key = rows.itemKey { it.key }) { index ->
                    val row = rows[index]
                    Text(row?.contact?.name.orEmpty(), Modifier.height(ROW_HEIGHT))
                }
            }
        }
        try {
            composeRule.waitUntil(WAIT_TIMEOUT_MS) {
                shadowOf(Looper.getMainLooper()).idle()
                if (::rows.isInitialized) (rows.loadState.refresh as? LoadState.Error)?.let { throw it.error }
                ::rows.isInitialized && rows.itemCount == CONTACT_COUNT
            }
        } catch (failure: ComposeTimeoutException) {
            val diagnostic = "PERSON_SCROLL_INITIAL initialized=${::rows.isInitialized} count=${if (::rows.isInitialized) rows.itemCount else -1} states=${if (::rows.isInitialized) rows.loadState else null}"
            println(diagnostic)
            throw AssertionError(diagnostic, failure)
        }
        composeRule.runOnIdle {
            anchor.clear() // Same explicit jump boundary used by ContactLetterIndex.
            scope.launch { state.scrollToItem(200, ANCHOR_OFFSET) }
        }
        awaitAnchor(EXPECTED_ANCHOR_ID, 200)
    }

    private fun awaitAnchor(id: Long, index: Int) {
        val changes = mutableListOf<String>()
        try {
            composeRule.waitUntil(WAIT_TIMEOUT_MS) {
                shadowOf(Looper.getMainLooper()).idle()
                (rows.loadState.refresh as? LoadState.Error)?.let { throw it.error }
                val diagnostic = scrollDiagnostic()
                if (changes.lastOrNull() != diagnostic) changes.add(diagnostic)
                index < rows.itemCount && state.firstVisibleItemIndex == index && rows.peek(index)?.contact?.id == id && state.firstVisibleItemScrollOffset == ANCHOR_OFFSET
            }
        } catch (failure: ComposeTimeoutException) {
            val diagnostic = buildString {
                appendLine("PERSON_SCROLL_EXPECTED id=$id index=$index offset=$ANCHOR_OFFSET")
                changes.takeLast(MAX_DIAGNOSTIC_EVENTS).forEach { appendLine("PERSON_SCROLL_TRACE $it") }
                appendLine("PERSON_SCROLL_FINAL ${scrollDiagnostic()}")
                ShadowLog.getLogsForTag("PersonScrollRestorePolicyTester").forEach { item ->
                    appendLine("PERSON_SCROLL_LOG ${item.msg}")
                    item.throwable?.let { appendLine(it.stackTraceToString()) }
                }
            }
            println(diagnostic)
            throw AssertionError(diagnostic, failure)
        }
        composeRule.runOnIdle { assertThat(rows.peek(state.firstVisibleItemIndex)?.contact?.id).isEqualTo(id) }
    }

    private fun scrollDiagnostic(): String {
        val index = state.firstVisibleItemIndex
        val current = if (index in 0 until rows.itemCount) rows.peek(index) else null
        val snapshot = rows.itemSnapshotList
        return "id=${current?.contact?.id} index=$index offset=${state.firstVisibleItemScrollOffset} " +
            "last=${anchor.last} pending=${anchor.pending} count=${rows.itemCount} " +
            "loaded=${snapshot.placeholdersBefore}+${snapshot.items.size} ids=${snapshot.items.firstOrNull()?.contact?.id}..${snapshot.items.lastOrNull()?.contact?.id} states=${rows.loadState}"
    }

    private fun contact(id: Long) = Contact(id = id, name = "Contact${id.toString().padStart(5, '0')}", pinyinInitial = "A")
    private companion object {
        const val CONTACT_COUNT = 500
        const val EXPECTED_ANCHOR_ID = 201L
        const val ANCHOR_OFFSET = 13
        const val WAIT_TIMEOUT_MS = 15_000L
        const val MAX_DIAGNOSTIC_EVENTS = 20
        val ROW_HEIGHT = 64.dp
    }
}
