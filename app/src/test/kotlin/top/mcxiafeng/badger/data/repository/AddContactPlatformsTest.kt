package top.mcxiafeng.badger.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AddContactPlatformsTest {
    @get:Rule val database = InMemoryDatabaseRule(ApplicationProvider.getApplicationContext<Context>())
    private lateinit var repository: ContactRepositoryImpl

    @Before
    fun setUp() {
        val db = database.db
        repository = ContactRepositoryImpl(db.contactDao(), db.contactFieldDao(), db.customFieldDao(),
            db.contactFieldValueDao(), db.scanResultDao(), db.contactPlatformDao(), db.contactFtsDao())
    }

    @Test
    fun savePreservesAllEntryFieldsAndWritesOnlyToTheTargetContact() = runTest {
        database.db.contactDao().insertContact(Contact(id = CONTACT_ID, name = "联系人", pinyinInitial = "L"))
        database.db.contactDao().insertContact(Contact(id = MY_CONTACT_ID, name = "我的名片", pinyinInitial = "W"))
        val entry = PlatformEntry("昵称", "https://github.com/octocat", "https://github.com/octocat?from=share",
            "octocat", "https://example.org/avatar.png")

        assertThat(repository.addContactPlatforms(CONTACT_ID, mapOf("github" to entry))).isEqualTo(AddPlatformsResult.Saved)
        val stored = repository.getContactPlatforms(CONTACT_ID).single()
        assertThat(stored.displayName).isEqualTo(entry.displayName)
        assertThat(stored.jumpLink).isEqualTo(entry.jumpLink)
        assertThat(stored.originalLink).isEqualTo(entry.originalLink)
        assertThat(stored.value).isEqualTo(entry.value)
        assertThat(stored.avatarUrl).isEqualTo(entry.avatarUrl)
        assertThat(repository.getContactPlatforms(MY_CONTACT_ID)).isEmpty()
    }

    @Test
    fun existingLegacyAliasAbortsTheWholeBatchWithoutOverwriting() = runTest {
        insertTarget()
        database.db.contactPlatformDao().insertPlatform(ContactPlatform(contactId = CONTACT_ID, platformKey = "QQ", value = "old"))
        val result = repository.addContactPlatforms(CONTACT_ID, mapOf(
            "qq" to PlatformEntry(value = "new"), "bilibili" to PlatformEntry(value = "42")))

        assertThat(result).isEqualTo(AddPlatformsResult.Duplicate(setOf("qq")))
        val stored = repository.getContactPlatforms(CONTACT_ID)
        assertThat(stored).hasSize(1)
        assertThat(stored.single().value).isEqualTo("old")
    }

    @Test
    fun simultaneousAdditionsOfTheSamePlatformHaveOneWinner() = runTest {
        insertTarget()
        val start = CompletableDeferred<Unit>()
        val requests = listOf("first", "second").map { value ->
            async(Dispatchers.Default) {
                start.await()
                repository.addContactPlatforms(CONTACT_ID, mapOf("qq" to PlatformEntry(value = value)))
            }
        }
        start.complete(Unit)
        val results = requests.awaitAll()

        assertThat(results.count { it == AddPlatformsResult.Saved }).isEqualTo(1)
        assertThat(results.count { it is AddPlatformsResult.Duplicate }).isEqualTo(1)
        assertThat(repository.getContactPlatforms(CONTACT_ID)).hasSize(1)
    }

    @Test
    fun failureOnTheSecondInsertRollsBackTheFirstInsert() = runTest {
        insertTarget()
        database.db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_second_platform BEFORE INSERT ON contact_platforms
            WHEN NEW.value = '42'
            BEGIN SELECT RAISE(ABORT, 'injected platform write failure'); END
        """.trimIndent())
        val result = runCatching {
            repository.addContactPlatforms(CONTACT_ID, linkedMapOf(
                "qq" to PlatformEntry(value = "123456"), "bilibili" to PlatformEntry(value = "42")))
        }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("injected platform write failure")
        assertThat(repository.getContactPlatforms(CONTACT_ID)).isEmpty()
    }

    @Test
    fun deletedContactDoesNotProduceAnOrphanPlatformOrReportSuccess() = runTest {
        val result = repository.addContactPlatforms(CONTACT_ID, mapOf("qq" to PlatformEntry(value = "123456")))
        assertThat(result).isEqualTo(AddPlatformsResult.ContactMissing)
        assertThat(repository.getContactPlatforms(CONTACT_ID)).isEmpty()
    }

    private suspend fun insertTarget() {
        database.db.contactDao().insertContact(Contact(id = CONTACT_ID, name = "联系人", pinyinInitial = "L"))
    }

    private companion object { const val CONTACT_ID = 42L }
}
