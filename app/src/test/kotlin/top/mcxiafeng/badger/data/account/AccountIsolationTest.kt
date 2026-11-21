package top.mcxiafeng.badger.data.account

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.SyncStateEntity
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.di.DatabaseModule
import top.mcxiafeng.badger.utils.Methods
import java.io.File
import android.graphics.Bitmap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountIsolationTest {
    private lateinit var context: Context
    private lateinit var store: AccountStore
    private lateinit var provider: AccountDatabaseProvider
    private val opened = mutableMapOf<AccountScope, AppDatabase>()

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        store = AccountStore(context)
        provider = AccountDatabaseProvider(context)
    }

    @After
    fun cleanup() {
        opened.forEach { (scope, db) ->
            db.close()
            context.deleteDatabase(scope.databaseName)
        }
    }

    private fun open(scope: AccountScope): AppDatabase = opened.getOrPut(scope) { provider.open(scope) }
    private fun remote(user: String = "A", server: String = "server-1", epoch: String = "epoch-1") =
        store.activateRemote(server, user, epoch, "test user", "https://example.test")

    // Robolectric embeds method names in Windows temp paths; leave room for SHA-256 DB names and WAL sidecars.
    @Test
    fun sameIdsStayInSeparateSpaces() = runBlocking {
        val scopes = listOf(store.localScope(), remote("A"), remote("B"))
        scopes.forEachIndexed { index, scope ->
            val db = open(scope)
            db.contactDao().insertContact(Contact(id = CONTACT_ID, name = "contact-$index"))
            db.contactDao().updateContact(Contact(id = MY_ID, name = "self-$index"))
            db.userProfileDao().saveProfile(UserProfile(id = PROFILE_ID, name = "profile-$index"))
            db.syncStateDao().put(SyncStateEntity("cursor", "cursor-$index"))
        }
        scopes.forEachIndexed { index, scope ->
            val db = open(scope)
            assertThat(db.contactDao().getContactById(CONTACT_ID)?.name).isEqualTo("contact-$index")
            assertThat(db.contactDao().getContactById(MY_ID)?.name).isEqualTo("self-$index")
            assertThat(db.userProfileDao().getProfileOnce()?.name).isEqualTo("profile-$index")
            assertThat(db.syncStateDao().get("cursor")).isEqualTo("cursor-$index")
            assertThat(provider.open(scope)).isSameInstanceAs(db)
        }
        assertThat(store.localScope().databaseName).isEqualTo("badger_database")
    }

    @Test
    fun restartRestoresActiveSpaceAndRandomLocalIdentity() {
        val local = store.localScope()
        val remote = remote()
        val restarted = AccountStore(context)
        assertThat(restarted.current()).isEqualTo(remote)
        assertThat(restarted.scopeFlow.value).isEqualTo(remote)
        assertThat(restarted.localScope()).isEqualTo(local)
        restarted.switchToLocal()
        assertThat(AccountStore(context).current()).isEqualTo(local)
    }

    @Test
    fun serverAndEpochIsolateState() = runBlocking {
        val first = remote()
        val otherServer = remote(server = "server-2")
        val nextEpoch = remote(epoch = "epoch-2")
        open(first).syncStateDao().put(SyncStateEntity("outbox", "original"))
        assertThat(setOf(first.databaseName, otherServer.databaseName, nextEpoch.databaseName)).hasSize(3)
        assertThat(open(otherServer).syncStateDao().getAll()).isEmpty()
        assertThat(open(nextEpoch).syncStateDao().getAll()).isEmpty()
        assertThat(open(first).syncStateDao().get("outbox")).isEqualTo("original")
        assertThat(remote()).isEqualTo(first)
    }

    @Test
    fun delayedOldViewModelWriteStaysInCapturedDatabase() = runBlocking {
        val oldScope = DatabaseModule.provideAccountScope(store)
        val oldDb = open(oldScope)
        val release = CompletableDeferred<Unit>()
        val delayed = async(Dispatchers.IO) {
            release.await()
            oldDb.contactDao().insertContact(Contact(id = CONTACT_ID, name = "late-local-write"))
        }
        val newScope = remote()
        val newDb = open(newScope)
        release.complete(Unit)
        delayed.await()
        assertThat(oldDb.contactDao().getContactById(CONTACT_ID)?.name).isEqualTo("late-local-write")
        assertThat(newDb.contactDao().getContactById(CONTACT_ID)).isNull()
        assertThat(AccountWriteLocks.forDao(oldDb.contactDao()))
            .isSameInstanceAs(AccountWriteLocks.forDao(provider.open(oldScope).contactDao()))
        assertThat(AccountWriteLocks.forDao(oldDb.contactDao()))
            .isNotSameInstanceAs(AccountWriteLocks.forDao(newDb.contactDao()))
    }

    @Test
    fun filesAndBusinessPrefsStayBoundToOriginalScopeWhileDevicePrefsAreShared() {
        val local = AccountContext(context, store.localScope())
        val a = AccountContext(context, remote("A"))
        val b = AccountContext(context, remote("B"))
        val contexts = listOf(local, a, b)
        contexts.forEachIndexed { index, scoped ->
            File(scoped.filesDir, "card_image.webp").writeText("image-$index")
            AccountContext.BUSINESS_PREFERENCES.forEach { pref ->
                scoped.applicationContext.getSharedPreferences(pref, Context.MODE_PRIVATE)
                    .edit().putString("test", "value-$index").commit()
            }
        }
        contexts.forEachIndexed { index, scoped ->
            assertThat(File(scoped.filesDir, "card_image.webp").readText()).isEqualTo("image-$index")
            AccountContext.BUSINESS_PREFERENCES.forEach { pref ->
                assertThat(scoped.getSharedPreferences(pref, Context.MODE_PRIVATE).getString("test", null))
                    .isEqualTo("value-$index")
            }
        }
        assertThat(local.filesDir).isEqualTo(context.filesDir)
        assertThat(a.cacheDir).isNotEqualTo(b.cacheDir)
        a.getSharedPreferences("device_ui", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
        assertThat(b.getSharedPreferences("device_ui", Context.MODE_PRIVATE).getBoolean("enabled", false)).isTrue()
        assertThat(AccountContext.find(a.applicationContext)?.scope).isEqualTo(a.scope)
    }

    @Test
    fun avatarWriterUsesFixedAccountDirectoryWithoutRecyclingDisplayedInput() {
        val a = AccountContext(context, remote("A"))
        val b = AccountContext(context, remote("B"))
        val bitmap = Bitmap.createBitmap(AVATAR_EDGE, AVATAR_EDGE, Bitmap.Config.ARGB_8888)
        try {
            val fileA = Methods.saveBitmapAsAvatar(a, bitmap, "same-avatar.webp")
            val fileB = Methods.saveBitmapAsAvatar(b, bitmap, "same-avatar.webp")
            assertThat(fileA.parentFile).isEqualTo(a.filesDir)
            assertThat(fileB.parentFile).isEqualTo(b.filesDir)
            assertThat(fileA).isNotEqualTo(fileB)
            assertThat(fileA.exists() && fileB.exists()).isTrue()
            assertThat(bitmap.isRecycled).isFalse()
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val CONTACT_ID = 42L
        private const val MY_ID = -1L
        private const val PROFILE_ID = 1L
        private const val AVATAR_EDGE = 32
    }
}
