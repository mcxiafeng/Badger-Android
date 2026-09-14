package top.mcxiafeng.badger.data.queue

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.OutboxEnqueueResult
import top.mcxiafeng.badger.sync.OutboxOpType
import top.mcxiafeng.badger.sync.OutboxStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OutboxStoreTest {
    private lateinit var database: AppDatabase
    private lateinit var store: OutboxStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = OutboxStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    

    @Test
    fun enqueueNewPatch_returnsCreatedWithReadableRow(): Unit = runBlocking {
        val result = store.enqueue(
            EntityKind.PERSON, localId = 7L, remoteId = "p-uuid",
            op = OutboxOpType.PATCH, payload = jsonObject("name" to "新名字"),
        )

        val created = result as OutboxEnqueueResult.Created
        val row = store.getReady().single()
        assertThat(created.outboxId).isEqualTo(row.id)
        assertThat(row.entityKind).isEqualTo(EntityKind.PERSON)
        assertThat(row.localId).isEqualTo(7L)
        assertThat(row.remoteId).isEqualTo("p-uuid")
        assertThat(row.op).isEqualTo(OutboxOpType.PATCH)
        assertThat(row.payload).isEqualTo(jsonObject("name" to "新名字"))
    }

    

    @Test
    fun patchMerge_partialPayload_keepsQueuedName(): Unit = runBlocking {
        
        
        store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH,
            jsonObject("name" to "新名字"),
        )
        val second = store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH,
            jsonObject("profile" to jsonObject("description" to "P")),
        )

        val rows = store.getReady()
        assertThat(rows).hasSize(1)
        assertThat(second).isInstanceOf(OutboxEnqueueResult.MergedIntoExisting::class.java)
        val payload = rows.single().payload
        assertThat(stringOf(payload, "name")).isEqualTo("新名字")
        assertThat(stringOf(payload["profile"] as JsonObject, "description")).isEqualTo("P")
    }

    @Test
    fun patchMerge_fullPayload_keepsNewestValues(): Unit = runBlocking {
        store.enqueue(EntityKind.TAG, 3L, "t-uuid", OutboxOpType.PATCH, jsonObject("name" to "旧名"))
        store.enqueue(EntityKind.TAG, 3L, "t-uuid", OutboxOpType.PATCH, jsonObject("name" to "新名"))

        val rows = store.getReady()
        assertThat(rows).hasSize(1)
        assertThat(stringOf(rows.single().payload, "name")).isEqualTo("新名")
    }

    @Test
    fun patchMerge_supersedesRowId_staleSuccessCannotDelete(): Unit = runBlocking {
        store.enqueue(EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH, jsonObject("name" to "A"))
        val firstId = store.getReady().single().id

        val merged = store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH,
            jsonObject("name" to "B"),
        ) as OutboxEnqueueResult.MergedIntoExisting
        assertThat(merged.outboxId).isNotEqualTo(firstId)

        
        store.markSuccess(firstId)
        val rows = store.getReady()
        assertThat(rows).hasSize(1)
        assertThat(rows.single().id).isEqualTo(merged.outboxId)
        assertThat(stringOf(rows.single().payload, "name")).isEqualTo("B")

        store.markSuccess(merged.outboxId)
        assertThat(store.getReady()).isEmpty()
    }

    

    @Test
    fun createDuplicate_payloadChangeIsIgnored(): Unit = runBlocking {
        val first = store.enqueue(
            EntityKind.TAG, 3L, "client-uuid", OutboxOpType.CREATE,
            jsonObject("name" to "标签A"),
        )
        val second = store.enqueue(
            EntityKind.TAG, 3L, "client-uuid", OutboxOpType.CREATE,
            jsonObject("name" to "标签B"),
        )

        assertThat(first).isInstanceOf(OutboxEnqueueResult.Created::class.java)
        assertThat(second).isEqualTo(OutboxEnqueueResult.IgnoredDuplicateCreate)
        val rows = store.getReady()
        assertThat(rows).hasSize(1)
        
        assertThat(stringOf(rows.single().payload, "name")).isEqualTo("标签A")
    }

    

    @Test
    fun deleteCancelsUnsentCreateAndPatch(): Unit = runBlocking {
        store.enqueue(EntityKind.COLLECTION, 9L, "client-uuid", OutboxOpType.CREATE, jsonObject("name" to "夹"))
        store.enqueue(EntityKind.COLLECTION, 9L, "col-uuid", OutboxOpType.PATCH, jsonObject("name" to "改名"))

        val result = store.enqueue(
            EntityKind.COLLECTION, 9L, "col-uuid", OutboxOpType.DELETE, JsonObject(emptyMap()),
        )

        assertThat(result).isInstanceOf(OutboxEnqueueResult.Created::class.java)
        val rows = store.getReady()
        assertThat(rows).hasSize(1)
        assertThat(rows.single().op).isEqualTo(OutboxOpType.DELETE)
    }

    

    @Test
    fun memberOps_areNotMerged_andReplayFifo(): Unit = runBlocking {
        store.enqueue(EntityKind.TAG, 3L, "t-uuid", OutboxOpType.MEMBER_ADD, jsonObject("personUuid" to "p-1"))
        store.enqueue(EntityKind.TAG, 3L, "t-uuid", OutboxOpType.MEMBER_REMOVE, jsonObject("personUuid" to "p-1"))
        store.enqueue(EntityKind.TAG, 3L, "t-uuid", OutboxOpType.MEMBER_ADD, jsonObject("personUuid" to "p-2"))

        val rows = store.getReady()
        assertThat(rows).hasSize(3)
        assertThat(rows.map { it.op }).containsExactly(
            OutboxOpType.MEMBER_ADD, OutboxOpType.MEMBER_REMOVE, OutboxOpType.MEMBER_ADD,
        ).inOrder()
        assertThat(stringOf(rows[2].payload, "personUuid")).isEqualTo("p-2")
    }

    

    @Test
    fun recordFailure_incrementsAtomicallyUnderContention(): Unit = runBlocking {
        val enqueued = store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH, jsonObject("name" to "n"),
        ) as OutboxEnqueueResult.Created
        val threadCount = 12
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val done = CountDownLatch(threadCount)

        repeat(threadCount) {
            pool.execute {
                ready.countDown()
                ready.await()
                runBlocking { store.recordFailure(enqueued.outboxId, IllegalStateException("offline"), now = NOW) }
                done.countDown()
            }
        }
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()

        
        val row = store.getReady(now = NOW + MAX_BACKOFF_MILLIS).single()
        assertThat(row.attempts).isEqualTo(threadCount)
    }

    @Test
    fun recordFailure_backoffHidesRowUntilNextAttemptAt(): Unit = runBlocking {
        val enqueued = store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH, jsonObject("name" to "n"),
        ) as OutboxEnqueueResult.Created

        runBlocking { store.recordFailure(enqueued.outboxId, IllegalStateException("offline"), now = NOW) }

        assertThat(store.getReady(now = NOW + 9_999)).isEmpty()
        val row = store.getReady(now = NOW + BACKOFF_STEP_MILLIS).single()
        assertThat(row.attempts).isEqualTo(1)
        assertThat(row.lastError).contains("offline")
    }

    @Test
    fun recordFailure_backoffCapsAtSixExponent(): Unit = runBlocking {
        val enqueued = store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH, jsonObject("name" to "n"),
        ) as OutboxEnqueueResult.Created

        repeat(7) { store.recordFailure(enqueued.outboxId, IllegalStateException("down"), now = NOW) }

        val row = store.getReady(now = NOW + MAX_BACKOFF_MILLIS).single()
        assertThat(row.attempts).isEqualTo(7)
        
        assertThat(row.nextAttemptAt - NOW).isEqualTo(MAX_BACKOFF_MILLIS)
    }

    @Test
    fun recordFailure_forSupersededRow_isIgnored(): Unit = runBlocking {
        store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH,
            jsonObject("name" to "A"), now = NOW,
        )
        val staleId = store.getReady(now = NOW).single().id
        val merged = store.enqueue(
            EntityKind.PERSON, 7L, "p-uuid", OutboxOpType.PATCH,
            jsonObject("name" to "B"), now = NOW,
        ) as OutboxEnqueueResult.MergedIntoExisting

        store.recordFailure(staleId, IllegalStateException("stale"), now = NOW)

        
        val row = store.getReady(now = NOW + MAX_BACKOFF_MILLIS).single()
        assertThat(row.id).isEqualTo(merged.outboxId)
        assertThat(row.attempts).isEqualTo(0)
    }

    private fun jsonObject(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is JsonObject -> put(key, value)
            }
        }
    }

    private fun stringOf(obj: JsonObject, key: String): String =
        (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
            ?: throw AssertionError("missing $key in $obj")

    private companion object {
        const val NOW = 1_000_000L
        const val BACKOFF_STEP_MILLIS = 10_000L
        const val MAX_BACKOFF_MILLIS = 640_000L
    }
}
