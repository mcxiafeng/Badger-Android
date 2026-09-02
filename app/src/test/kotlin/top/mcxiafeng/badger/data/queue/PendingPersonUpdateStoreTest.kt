package top.mcxiafeng.badger.data.queue

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.ProfileDto

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PendingPersonUpdateStoreTest {
    private lateinit var database: AppDatabase
    private lateinit var store: PendingPersonUpdateStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
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
        // [F4] 两次都带非 null name 时保留最新 name
        assertThat(rows.single().name).isEqualTo("新名字")
        assertThat(rows.single().profile?.description).isEqualTo("new")
    }

    // ============ [F4] 半载 PUT 字段级 merge ============

    @Test
    fun enqueuePartialPutMergesNonNullName(): Unit = runBlocking {
        store.enqueue("person-1", "新名字", null)
        store.enqueue("person-1", null, ProfileDto(description = "P"))

        val rows = store.getReady()

        assertThat(rows).hasSize(1)
        // name=null 的半载 PUT 不得覆盖已排队的改名
        assertThat(rows.single().name).isEqualTo("新名字")
        assertThat(rows.single().profile?.description).isEqualTo("P")
    }

    @Test
    fun enqueueNullProfileKeepsPreviousProfile(): Unit = runBlocking {
        store.enqueue("person-1", "名字", ProfileDto(description = "old-bio"))
        store.enqueue("person-1", null, null)

        val rows = store.getReady()

        assertThat(rows).hasSize(1)
        assertThat(rows.single().name).isEqualTo("名字")
        assertThat(rows.single().profile?.description).isEqualTo("old-bio")
    }

    @Test
    fun enqueueMergeStillRotatesRequestId(): Unit = runBlocking {
        val first = store.enqueue("person-1", "名字A", null)
        val second = store.enqueue("person-1", null, null)

        val rows = store.getReady()

        assertThat(rows).hasSize(1)
        assertThat(rows.single().requestId).isEqualTo(second)
        assertThat(second).isNotEqualTo(first)
        // requestId 换新代后，旧代成功回执无法误删新 payload
        store.deleteIfRequest("person-1", first)
        assertThat(store.getReady()).hasSize(1)
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

        assertThat(store.getReady(now = now + 9_999)).isEmpty()
        assertThat(store.getReady(now = now + 10_000)).hasSize(1)
        // [修复防御] 不用默认 now(真实时钟早于 next_attempt_at),显式传退避到期时间
        assertThat(store.getReady(now = now + 10_000).single().attempts).isEqualTo(1)
        assertThat(store.getReady(now = now + 10_000).single().lastError).contains("offline")
    }
}
