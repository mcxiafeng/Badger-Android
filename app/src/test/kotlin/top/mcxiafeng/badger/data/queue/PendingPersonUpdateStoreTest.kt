package top.mcxiafeng.badger.data.queue

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.ProfileDto

class PendingPersonUpdateStoreTest {
    private lateinit var database: AppDatabase
    private lateinit var store: PendingPersonUpdateStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = PendingPersonUpdateStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun enqueueSamePersonKeepsOnlyNewestGeneration(): Unit = runBlocking {
        store.enqueue("person-1", "旧名字", ProfileDto(description = "old"))
        val latestRequest = store.enqueue("person-1", "新名字", ProfileDto(description = "new"))

        val rows = store.getReady()

        assertThat(rows).hasSize(1)
        assertThat(rows.single().requestId).isEqualTo(latestRequest)
        assertThat(rows.single().name).isEqualTo("新名字")
        assertThat(rows.single().profile?.description).isEqualTo("new")
    }

    @Test
    fun staleSuccessCannotDeleteNewerGeneration(): Unit = runBlocking {
        val oldRequest = store.enqueue("person-1", "old", null)
        val newRequest = store.enqueue("person-1", "new", null)

        store.deleteIfRequest("person-1", oldRequest)

        val rows = store.getReady()
        assertThat(rows).hasSize(1)
        assertThat(rows.single().requestId).isEqualTo(newRequest)
        assertThat(rows.single().name).isEqualTo("new")
    }

    @Test
    fun failedUpdateIsHiddenUntilBackoffExpires(): Unit = runBlocking {
        val requestId = store.enqueue("person-1", "name", null)
        val now = System.currentTimeMillis()

        store.recordFailure("person-1", requestId, IllegalStateException("offline"), now)

        assertThat(store.getReady(now + 9_999)).isEmpty()
        assertThat(store.getReady(now + 10_000)).hasSize(1)
        assertThat(store.getReady().single().attempts).isEqualTo(1)
        assertThat(store.getReady().single().lastError).contains("offline")
    }
}
