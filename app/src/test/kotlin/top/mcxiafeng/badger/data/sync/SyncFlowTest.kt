package top.mcxiafeng.badger.data.sync

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.ScanResult
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.account.AccountDatabaseProvider
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import top.mcxiafeng.badger.network.sync.SyncHttpException
import top.mcxiafeng.badger.ocr.ALL_FIELDS
import top.mcxiafeng.badger.network.sync.SyncTransport
import java.util.UUID
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SyncFlowTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var localDb: AppDatabase
    private lateinit var accounts: AccountStore
    private lateinit var scope: AccountScope
    private lateinit var sessions: AuthSessionStore
    private lateinit var session: AuthSession
    private lateinit var transport: SyncTransport
    private lateinit var coordinator: SyncCoordinator
    private lateinit var server: SyncServerFixture
    private lateinit var client: OkHttpClient

    @Before fun setup() = runBlocking<Unit> {
        val base: Context = RuntimeEnvironment.getApplication()
        val context = object : ContextWrapper(base) { override fun getFilesDir(): File = directory.root }
        accounts = AccountStore(context)
        scope = accounts.activateRemote(SERVER, USER, EPOCH, "测试账号", "https://one.example.test")
        session = AuthSession(USER, "A".repeat(43), "测试账号", serverId = SERVER, dataEpoch = EPOCH,
            baseUrl = scope.baseUrl, expiresAt = "2099-01-01T00:00:00Z")
        sessions = AuthSessionStore(base.getSharedPreferences("sync-test-session", Context.MODE_PRIVATE)).apply { save(session) }
        db = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).allowMainThreadQueries().build()
        localDb = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).allowMainThreadQueries().build()
        db.contactDao().insertContact(Contact(id = -1, name = "用户", createTime = 1, updateTime = 1))
        db.contactFieldDao().insertField(ContactField(id = 1, fieldName = "手机", fieldKey = "phone", isSystem = true, createTime = 1))
        db.userProfileDao().saveProfile(UserProfile(name = "用户", updateTime = 1))
        db.cardCollectionDao().insertCollection(CardCollection(id = 1, name = "默认名片夹", description = "所有新扫描的联系人将添加到此处", createTime = 1))
        val provider = mockk<AccountDatabaseProvider>()
        every { provider.open(any()) } answers { if (firstArg<AccountScope>().isLocal) localDb else db }
        server = SyncServerFixture(SERVER, USER, EPOCH)
        client = OkHttpClient.Builder().addInterceptor(server).build()
        transport = SyncTransport(client, sessions)
        coordinator = SyncCoordinator(context, accounts, provider, sessions, transport)
        coordinator.synchronize(scope)
        assertThat(coordinator.progress.value[scope.key]?.pending).isFalse()
    }

    @After fun close() {
        db.close()
        localDb.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun overlappingRefreshesApplyRemoteChangesWithOneSnapshot() = runBlocking<Unit> {
        val snapshots = server.snapshotRequests
        server.addPeople(1)
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        server.beforeCapabilities = {
            entered.complete(Unit)
            check(release.await(REFRESH_GATE_MS, TimeUnit.MILLISECONDS))
        }
        try {
            val foreground = async { coordinator.refresh(scope) }
            withTimeout(REFRESH_GATE_MS) { entered.await() }
            withContext(Dispatchers.IO) {
                val gesture = async(start = CoroutineStart.UNDISPATCHED) { coordinator.refresh(scope) }
                release.countDown()
                gesture.await()
            }
            foreground.await()
        } finally {
            release.countDown()
            server.beforeCapabilities = null
        }
        assertThat(server.snapshotRequests - snapshots).isEqualTo(1)
        assertThat(db.contactDao().getAllContacts().first().single().name).isEqualTo("SyncCapacity0")
        val requests = server.requestCount
        coordinator.refresh(scope, onlyIfChanged = true)
        assertThat(server.requestCount).isEqualTo(requests)
    }

    @Test fun refreshForSwitchedAccountMakesNoRequests() = runBlocking<Unit> {
        val requests = server.requestCount
        val local = accounts.switchToLocal()
        coordinator.refresh(scope)
        coordinator.refresh(local)
        assertThat(server.requestCount).isEqualTo(requests)
    }

    @Test fun refreshRejectsResolutionForPreviouslyReviewedConflict() = runBlocking<Unit> {
        val id = db.contactDao().insertContact(Contact(name = "联系人", note = "原内容"))
        coordinator.synchronize(scope)
        val local = requireNotNull(db.contactDao().getContactById(id)).copy(note = "本地修改")
        db.contactDao().updateContact(local)
        server.changePersonNote("云端版本一")
        coordinator.refresh(scope, onlyIfChanged = true)
        val conflicts = WorkspaceConflicts(coordinator)
        val reviewed = conflicts.list(scope).single()
        server.changePersonNote("云端版本二")
        coordinator.refresh(scope)

        val error = runCatching { conflicts.resolve(scope, reviewed, preferLocal = true) }.exceptionOrNull()
        assertThat(error).isInstanceOf(ConflictChangedException::class.java)
        assertThat(conflicts.list(scope).single().reviewToken).isNotEqualTo(reviewed.reviewToken)
        assertThat(db.contactDao().getContactById(id)?.note).isEqualTo("本地修改")
        assertThat(server.people().single().value?.str("notes")).isEqualTo("云端版本二")
    }

    // Short names leave room for the fixed SHA-256 account directory on Windows.
    @Test fun lostReply() = runBlocking<Unit> {
        db.contactDao().insertContact(Contact(id = 1, name = "离线联系人", note = "第一次修改", createTime = 2, updateTime = 2))
        server.loseNextPersonReply = true
        server.personAttempts.clear()
        coordinator.synchronize(scope)
        assertThat(server.people()).hasSize(1)
        assertThat(db.syncStateDao().get("pending/current")).isNotNull()
        val original = requireNotNull(db.contactDao().getContactById(1))
        db.contactDao().updateContact(original.copy(note = "发送后又修改", updateTime = 3))
        coordinator.synchronize(scope)
        assertThat(server.people()).hasSize(1)
        assertThat(server.people().single().value?.str("notes")).isEqualTo("发送后又修改")
        assertThat(db.contactDao().getContactById(1)?.note).isEqualTo("发送后又修改")
        assertThat(db.syncStateDao().get("pending/current")).isNull()
        assertThat(server.personAttempts).hasSize(3)
        assertThat(server.personAttempts[0]).isEqualTo(server.personAttempts[1])
        assertThat(server.personAttempts[2]).isNotEqualTo(server.personAttempts[1])
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
    }

    @Test fun badDigest() = runBlocking<Unit> {
        db.contactDao().insertContact(Contact(id = 1, name = "应当保留", note = "本机原文", createTime = 2, updateTime = 2))
        coordinator.synchronize(scope)
        val revision = db.syncStateDao().get(SyncLedger.SNAPSHOT_REVISION)
        server.changePersonNote("恶意或损坏的远端内容")
        server.tamperPage = true
        coordinator.synchronize(scope)
        assertThat(db.contactDao().getContactById(1)?.note).isEqualTo("本机原文")
        assertThat(db.syncStateDao().get(SyncLedger.SNAPSHOT_REVISION)).isEqualTo(revision)
        assertThat(coordinator.progress.value[scope.key]?.pending).isTrue()
    }

    @Test fun pinnedSession() = runBlocking<Unit> {
        val before = server.requestCount
        sessions.save(session.copy(userUuid = OTHER_USER, token = "B".repeat(43), baseUrl = "https://two.example.test"))
        val error = runCatching { transport.json(session, "/api/v1/sync/capabilities") }.exceptionOrNull()
        assertThat(error).isInstanceOf(SyncHttpException::class.java)
        assertThat(server.requestCount).isEqualTo(before)
    }

    @Test fun noRedirect() = runBlocking<Unit> {
        db.contactDao().insertContact(Contact(id = 1, name = "等待上传", createTime = 2, updateTime = 2))
        server.redirectMutation = true
        coordinator.synchronize(scope)
        assertThat(server.people()).isEmpty()
        assertThat(db.syncStateDao().get("pending/current")).isNotNull()
        assertThat(server.hosts).containsExactly("one.example.test")
    }

    @Test fun fieldInUse() = runBlocking<Unit> {
        db.customFieldDao().insertCustomField(CustomField(id = 1, fieldName = "公司", fieldType = "text", options = "[]", createTime = 2))
        coordinator.synchronize(scope)
        db.withTransaction { db.openHelper.writableDatabase.execSQL("DELETE FROM custom_fields WHERE id = 1") }
        server.referenceOnNextFieldDelete = true
        coordinator.synchronize(scope)
        assertThat(db.customFieldDao().getCustomFieldById(1)).isNull()
        assertThat(db.contactDao().getAllContacts().first()).isEmpty()
        val service = WorkspaceConflicts(coordinator)
        val conflict = service.list(scope).single()
        assertThat(conflict.canAdoptLocal).isFalse()
        assertThat(conflict.notice).contains("使用此字段")
        assertThat(db.syncStateDao().get("pending/current")).isNull()
        service.resolve(scope, conflict, preferLocal = false)
        assertThat(db.customFieldDao().getCustomFieldById(1)?.fieldName).isEqualTo("公司")
        val person = db.contactDao().getAllContacts().first().single()
        assertThat(person.name).isEqualTo("云端新增联系人")
        assertThat(db.contactFieldValueDao().getFieldValuesByContactOnce(person.id).single().value).isEqualTo("新增引用")
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
    }

    @Test fun copyLocal() = runBlocking<Unit> {
        val image = seedLocal()
        val originalBytes = image.readBytes().toList()
        val importer = WorkspaceImport(accounts, coordinator)
        assertThat(importer.shouldOffer(scope)).isTrue()
        assertThat(server.people()).isEmpty()
        importer.dismissOffer(scope)
        assertThat(importer.shouldOffer(scope)).isFalse()
        val plan = importer.prepare(scope)
        importer.confirm(scope, plan.id)
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
        val copied = db.contactDao().getAllContacts().first().single()
        assertThat(copied.name).isEqualTo("本地朋友")
        assertThat(localDb.contactDao().getContactById(1)?.avatarPath).isEqualTo(image.path)
        assertThat(image.readBytes().toList()).isEqualTo(originalBytes)
        val avatar = File(requireNotNull(copied.avatarPath))
        val background = File(requireNotNull(db.userProfileDao().getProfileOnce()?.cardImagePath))
        val collectionBackground = File(requireNotNull(db.cardCollectionDao().getAllCollectionsOnce().single().backgroundImagePath))
        assertThat(avatar.canonicalPath).isNotEqualTo(image.canonicalPath)
        assertThat(avatar.canonicalPath).isNotEqualTo(background.canonicalPath)
        assertThat(avatar.canonicalPath).isNotEqualTo(collectionBackground.canonicalPath)
        val duplicate = runCatching { importer.confirm(scope, plan.id) }.exceptionOrNull()
        assertThat(duplicate?.message).contains("预览")
        assertThat(server.people()).hasSize(1)
        assertThat(server.assetDownloads).isEqualTo(0)
        val assetId = requireNotNull(server.people().single().value?.getAsJsonObject("profile")?.str("avatarAssetId"))
        val metadata = File(requireNotNull(db.syncStateDao().get("assetPath/$assetId")))
        assertThat(RemoteAssetReference.read(metadata).id).isEqualTo(assetId)
        assertThat(avatar.delete()).isTrue()
        assertThat(background.isFile && collectionBackground.isFile && image.isFile).isTrue()
    }

    @Test(timeout = CAPACITY_TIMEOUT_MS) fun textSnapshotCapacity() = runBlocking<Unit> {
        var previous = 0
        for (count in listOf(SMALL_CAPACITY, LARGE_CAPACITY)) {
            server.addPeople(count - previous)
            val requestsBefore = server.requestCount
            val started = System.nanoTime()
            coordinator.synchronize(scope)
            val elapsedMs = (System.nanoTime() - started) / NANOS_PER_MS
            val rows = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM contacts WHERE id != -1").use {
                check(it.moveToFirst()); it.getLong(0)
            }
            assertThat(rows).isEqualTo(count.toLong())
            assertThat(server.assetDownloads).isEqualTo(0)
            assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
            println("SYNC_CAPACITY_PROBE environment=Robolectric contacts=$count elapsedMs=$elapsedMs requests=${server.requestCount - requestsBefore} imageGets=${server.assetDownloads}")
            previous = count
        }
    }

    @Test fun reverseLookupTracksMovesDuplicatesAndKinds() = runBlocking<Unit> {
        db.withTransaction {
            val ledger = SyncLedger.load(db)
            val remote = UUID.randomUUID().toString()
            ledger.bind("person", "100", remote)
            ledger.bind("tag", "200", remote)
            assertThat(ledger.local("person", remote)).isEqualTo("100")
            assertThat(ledger.local("tag", remote)).isEqualTo("200")
            val moved = ledger.move("person", "100")
            assertThat(ledger.local("person", remote)).isNull()
            assertThat(ledger.local("person", moved)).isEqualTo("100")
            ledger.put("id/person/2", remote)
            ledger.put("id/person/1", remote)
            assertThat(ledger.local("person", remote)).isEqualTo("2")
            assertThat(SyncLedger.load(db).local("person", remote)).isEqualTo("1")
            assertThat(runCatching { ledger.bind("person", "3", remote) }.isFailure).isTrue()
            ledger.remove("id/person/2")
            assertThat(ledger.local("person", remote)).isEqualTo("1")
            ledger.remove("id/person/1")
            assertThat(ledger.local("person", remote)).isNull()
            assertThat(ledger.local("tag", remote)).isEqualTo("200")
            ledger.bind("fieldDefinition", "custom/700", moved)
            assertThat(ledger.local("fieldDefinition", moved)).isEqualTo("custom/700")
        }
    }

    @Test fun idAllocationSkipsReservedIdsAndKeepsLaterExistingMappings() = runBlocking<Unit> {
        server.addPeople(2)
        val incoming = server.people()
        db.withTransaction {
            val ledger = SyncLedger.load(db)
            val oldRemote = UUID.randomUUID().toString()
            ledger.bind("person", "100", oldRemote)
            WorkspaceApplier(ledger).apply(listOf(incoming[0], incoming[0].copy(id = oldRemote), incoming[1]))
            assertThat(ledger.local("person", incoming[0].id)).isEqualTo("101")
            assertThat(ledger.local("person", oldRemote)).isEqualTo("100")
            assertThat(ledger.local("person", incoming[1].id)).isEqualTo("102")
        }
    }

    @Test fun restoringCloudRowsOnlyStoresImageReferences() = runBlocking<Unit> {
        val original = seedLocal()
        val importer = WorkspaceImport(accounts, coordinator)
        importer.confirm(scope, importer.prepare(scope).id)
        db.clearAllTables()
        coordinator.synchronize(scope)
        val restored = db.contactDao().getAllContacts().first().single()
        assertThat(restored.name).isEqualTo("本地朋友")
        assertThat(RemoteAssetReference.read(File(requireNotNull(restored.avatarPath))).size).isGreaterThan(0L)
        assertThat(server.assetDownloads).isEqualTo(0)
        assertThat(original.isFile).isTrue()
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
    }

    @Test fun lostImageReceiptRetainsCopiesUntilConfirmedReplay() = runBlocking<Unit> {
        val original = seedLocal()
        val accountDirectory = coordinator.scopedContext(scope).filesDir
        val mine = File(accountDirectory, "my-avatar.png").apply { writeBytes(original.readBytes()) }
        db.contactDao().insertContact(Contact(id = 1, name = "有图联系人", avatarPath = mine.path, createTime = 2, updateTime = 2))
        server.loseNextAssetReply = true
        coordinator.synchronize(scope)
        val staging = File(accountDirectory, "sync-assets")
        assertThat(staging.listFiles().orEmpty().any { it.name.endsWith(".source") }).isTrue()
        assertThat(db.syncStateDao().getAll().any { it.key.startsWith("upload/") }).isTrue()
        assertThat(mine.isFile && original.isFile).isTrue()
        coordinator.synchronize(scope)
        assertThat(server.assetAttempts).hasSize(2)
        assertThat(server.assetAttempts[0]).isEqualTo(server.assetAttempts[1])
        assertThat(staging.listFiles().orEmpty().any { it.name.endsWith(".source") || it.name.endsWith(".upload.png") }).isFalse()
        assertThat(mine.readBytes().toList()).isEqualTo(original.readBytes().toList())
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
    }

    @Test fun sameTag() = runBlocking<Unit> {
        val id = server.addTag("同事")
        db.tagDao().insertTag(Tag(id = 1, name = "同事", createTime = 999))
        coordinator.synchronize(scope)
        assertThat(db.tagDao().getAllTagsOnce()).hasSize(1)
        assertThat(server.tags()).hasSize(1)
        assertThat(db.syncStateDao().get("id/tag/1")).isEqualTo(id)
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
    }

    @Test fun tagRename() = runBlocking<Unit> {
        db.tagDao().insertTag(Tag(id = 1, name = "旧标签", createTime = 2))
        coordinator.synchronize(scope)
        db.openHelper.writableDatabase.execSQL("UPDATE tags SET name = '同名标签' WHERE id = 1")
        server.addTag("同名标签")
        coordinator.synchronize(scope)
        assertThat(coordinator.progress.value[scope.key]?.tagNameConflict).isTrue()
        assertThat(db.tagDao().getAllTagsOnce().single().name).isEqualTo("同名标签")
        db.openHelper.writableDatabase.execSQL("UPDATE tags SET name = '本机标签' WHERE id = 1")
        coordinator.synchronize(scope)
        assertThat(db.tagDao().getAllTagsOnce().map { it.name }).containsExactly("本机标签", "同名标签")
        assertThat(server.tags().map { it.value?.str("name") }).containsExactly("本机标签", "同名标签")
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
    }

    @Test fun freshDefaults() = runBlocking<Unit> {
        server.disablePhone()
        db.close()
        val base: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).allowMainThreadQueries().build()
        db.contactDao().insertContact(Contact(id = -1, name = "用户", createTime = 999, updateTime = 999))
        db.userProfileDao().saveProfile(UserProfile(name = "用户", updateTime = 999))
        val index = ALL_FIELDS.indexOfFirst { it.fieldKey == "phone" }
        val definition = ALL_FIELDS[index]
        db.contactFieldDao().insertField(ContactField(id = 1, fieldName = definition.displayName, fieldKey = "phone",
            icon = "phone", sortOrder = index + 1, isSystem = true, isEnabled = true, createTime = 999))
        coordinator.synchronize(scope)
        assertThat(db.contactFieldDao().getAllFieldsOnce().single().isEnabled).isFalse()
        assertThat(coordinator.progress.value[scope.key]?.conflicts).isEqualTo(0)
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
    }

    @Test fun mergeTarget() = runBlocking<Unit> {
        db.contactDao().insertContact(Contact(id = 1, name = "同名联系人", createTime = 2, updateTime = 2))
        coordinator.synchronize(scope)
        localDb.contactDao().insertContact(Contact(id = 1, name = "同名联系人", bio = "简介A", createTime = 2, updateTime = 2))
        localDb.contactDao().insertContact(Contact(id = 2, name = "同名联系人", note = "备注B", createTime = 2, updateTime = 2))
        val importer = WorkspaceImport(accounts, coordinator)
        val plan = importer.prepare(scope)
        for (item in plan.items) importer.select(scope, item.key, item.options.first { it.key != WorkspaceImport.NEW && it.key != WorkspaceImport.SKIP }.key)
        val failure = runCatching { importer.confirm(scope, plan.id) }.exceptionOrNull()
        assertThat(failure?.message).contains("同一个目标")
        assertThat(db.contactDao().getContactById(1)?.bio).isNull()
        assertThat(db.contactDao().getContactById(1)?.note).isNull()
        assertThat(localDb.contactDao().getContactById(1)?.bio).isEqualTo("简介A")
        assertThat(localDb.contactDao().getContactById(2)?.note).isEqualTo("备注B")
    }

    @Test fun twoFields() = runBlocking<Unit> {
        db.customFieldDao().insertCustomField(CustomField(id = 1, fieldName = "公司", fieldType = "text", options = "[]", createTime = 2))
        db.customFieldDao().insertCustomField(CustomField(id = 2, fieldName = "职位", fieldType = "text", options = "[]", createTime = 2))
        coordinator.synchronize(scope)
        db.withTransaction { db.openHelper.writableDatabase.execSQL("DELETE FROM custom_fields") }
        server.referenceCustomFields()
        coordinator.synchronize(scope)
        val service = WorkspaceConflicts(coordinator)
        assertThat(service.list(scope)).hasSize(2)
        service.resolve(scope, service.list(scope).first(), preferLocal = false)
        assertThat(db.openHelper.writableDatabase.rows("custom_fields")).hasSize(1)
        assertThat(db.contactDao().getAllContacts().first()).isEmpty()
        service.resolve(scope, service.list(scope).single(), preferLocal = false)
        assertThat(db.openHelper.writableDatabase.rows("custom_fields")).hasSize(2)
        val person = db.contactDao().getAllContacts().first().single()
        assertThat(db.contactFieldValueDao().getFieldValuesByContactOnce(person.id)).hasSize(2)
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
    }

    @Test fun keepAvatars() = runBlocking<Unit> {
        seedLocal()
        val importer = WorkspaceImport(accounts, coordinator)
        importer.confirm(scope, importer.prepare(scope).id)
        server.enrichPerson()
        coordinator.synchronize(scope)
        val person = db.contactDao().getAllContacts().first().single()
        db.contactDao().updateContact(person.copy(note = "本机修改", updateTime = 4))
        server.changePersonNote("云端修改")
        coordinator.synchronize(scope)
        val service = WorkspaceConflicts(coordinator)
        val conflict = service.list(scope).single()
        assertThat(conflict.canKeepBoth).isTrue()
        service.resolve(scope, conflict, preferLocal = true, keepBoth = true)
        assertThat(coordinator.progress.value[scope.key]?.message).isEqualTo("已同步")
        val people = db.contactDao().getAllContacts().first()
        val mine = File(requireNotNull(people.single { it.note == "本机修改" }.avatarPath))
        val theirs = File(requireNotNull(people.single { it.note == "云端修改" }.avatarPath))
        assertThat(mine.canonicalPath).isNotEqualTo(theirs.canonicalPath)
        assertThat(mine.delete()).isTrue()
        assertThat(theirs.isFile).isTrue()
        assertThat(server.people()).hasSize(2)
        val uploaded = requireNotNull(server.people().single { it.value?.str("notes") == "本机修改" }.value)
        assertThat(uploaded.getAsJsonObject("profile").str("circleName")).isEqualTo("公开名")
        assertThat(uploaded.getAsJsonObject("profile").getAsJsonArray("interestTags").map { it.asString }).containsExactly("主题A")
        assertThat(uploaded.getAsJsonObject("source").str("text")).isEqualTo("联系来源")
    }

    @Test fun rebuildPlan() = runBlocking<Unit> {
        db.contactDao().insertContact(Contact(id = 1, name = "同名联系人", note = "云端初始", createTime = 2, updateTime = 2))
        coordinator.synchronize(scope)
        localDb.contactDao().insertContact(Contact(id = 1, name = "同名联系人", note = "本地新增", createTime = 2, updateTime = 2))
        val importer = WorkspaceImport(accounts, coordinator)
        val plan = importer.prepare(scope)
        val item = plan.items.single()
        importer.select(scope, item.key, item.options.first { it.key != WorkspaceImport.NEW && it.key != WorkspaceImport.SKIP }.key)
        server.changePersonNote("云端更新")
        coordinator.synchronize(scope)
        val failure = runCatching { importer.confirm(scope, plan.id) }.exceptionOrNull()
        assertThat(failure?.message).contains("更新")
        val fresh = importer.rebuild(scope)
        assertThat(fresh.id).isNotEqualTo(plan.id)
        val freshItem = fresh.items.single()
        importer.select(scope, freshItem.key, freshItem.options.first { it.key != WorkspaceImport.NEW && it.key != WorkspaceImport.SKIP }.key)
        importer.confirm(scope, fresh.id)
        assertThat(WorkspaceConflicts(coordinator).list(scope).single().differences.any { it.remote == "云端更新" }).isTrue()
        assertThat(localDb.contactDao().getContactById(1)?.note).isEqualTo("本地新增")
    }

    private suspend fun seedLocal(): File {
        val image = File(directory.root, "local-image.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try { image.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        localDb.contactDao().insertContact(Contact(id = -1, name = "本地本人", note = "我的备注", createTime = 2, updateTime = 3))
        localDb.contactDao().insertContact(Contact(id = 1, name = "本地朋友", note = "本地备注", bio = "本地简介", avatarPath = image.path, createTime = 2, updateTime = 3))
        localDb.userProfileDao().saveProfile(UserProfile(name = "本地本人", cardImagePath = image.path, updateTime = 3))
        localDb.contactFieldDao().insertField(ContactField(id = 1, fieldName = "手机", fieldKey = "phone", isSystem = true, createTime = 2))
        localDb.contactFieldValueDao().insertFieldValue(ContactFieldValue(id = 1, contactId = 1, fieldId = 1, value = "12345", createTime = 2, updateTime = 3))
        localDb.contactPlatformDao().insertPlatform(ContactPlatform(id = 1, contactId = 1, platformKey = "custom", value = null, jumpLink = "my-app://example"))
        localDb.cardCollectionDao().insertCollection(CardCollection(id = 1, name = "本地活动", backgroundImagePath = image.path, createTime = 2))
        localDb.scanResultDao().insertScanResult(ScanResult(id = 1, contactId = 1, collectionId = 1, sourceType = "scan", rawData = "原始内容", scannedTime = 2))
        return image
    }

    companion object {
        private const val REFRESH_GATE_MS = 5_000L
        private const val CAPACITY_TIMEOUT_MS = 300_000L
        private const val SMALL_CAPACITY = 1_000
        private const val LARGE_CAPACITY = 10_000
        private const val NANOS_PER_MS = 1_000_000L
        private const val SERVER = "11111111-1111-4111-8111-111111111111"
        private const val USER = "22222222-2222-4222-8222-222222222222"
        private const val OTHER_USER = "33333333-3333-4333-8333-333333333333"
        private const val EPOCH = "44444444-4444-4444-8444-444444444444"
    }
}
