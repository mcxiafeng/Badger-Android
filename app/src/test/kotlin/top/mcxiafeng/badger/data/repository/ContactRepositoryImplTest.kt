package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.snapshot.ContactSnapshotter
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import top.mcxiafeng.badger.testutil.TestDataProvider
import top.mcxiafeng.badger.utils.HttpUtil

class ContactRepositoryImplTest {

    private lateinit var contactCacheDao: ContactCacheDao
    private lateinit var contactFieldCacheDao: ContactFieldCacheDao
    private lateinit var contactFieldValueCacheDao: ContactFieldValueCacheDao
    private lateinit var contactPlatformCacheDao: ContactPlatformCacheDao
    private lateinit var contactTagCacheDao: ContactTagCacheDao
    private lateinit var cardCollectionCacheDao: CardCollectionCacheDao
    private lateinit var contactSnapshotter: ContactSnapshotter
    private lateinit var pendingDao: PendingUploadDao
    private lateinit var historyDao: OperationHistoryDao
    private lateinit var pendingUploadScheduler: PendingUploadScheduler
    private lateinit var deviceIdProvider: DeviceIdProvider
    private lateinit var serverApi: ServerApi
    private lateinit var repository: ContactRepositoryImpl
    private lateinit var context: Context

    @Before
    fun setup() {
        contactCacheDao = mockk(relaxed = true)
        contactFieldCacheDao = mockk(relaxed = true)
        contactFieldValueCacheDao = mockk(relaxed = true)
        contactPlatformCacheDao = mockk(relaxed = true)
        contactTagCacheDao = mockk(relaxed = true)
        cardCollectionCacheDao = mockk(relaxed = true)
        contactSnapshotter = mockk(relaxed = true)
        pendingDao = mockk(relaxed = true)
        historyDao = mockk(relaxed = true)
        pendingUploadScheduler = mockk(relaxed = true)
        deviceIdProvider = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)
        every { deviceIdProvider.deviceId() } returns "test-device"
        // 默认 snapshotter 返回空 JSON,需要时各测试自行 stub
        runBlocking { coEvery { contactSnapshotter.toJsonFromCache(any(), any()) } returns "{}" }
        repository = ContactRepositoryImpl(
            contactCacheDao,
            contactFieldCacheDao,
            contactFieldValueCacheDao,
            contactPlatformCacheDao,
            contactTagCacheDao,
            cardCollectionCacheDao,
            contactSnapshotter,
            pendingDao,
            historyDao,
            pendingUploadScheduler,
            deviceIdProvider,
            serverApi,
        )
        context = mockk(relaxed = true)
    }

    private fun runBlocking(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }

    @After
    fun tearDown() {
        // 恢复默认头像下载器，避免泄漏到其它测试
        ContactRepositoryImpl.avatarDownloader = { HttpUtil.downloadBitmap(it) }
    }

    // 默认头像下载返回 null（跳过实际网络）
    private fun stubAvatarDownloader(returnBmp: Bitmap? = null) {
        ContactRepositoryImpl.avatarDownloader = { _ -> returnBmp }
    }

    // ========== checkDuplicate ==========

    @Test
    fun checkDuplicate_emptyFieldValues_returnsNotDuplicate() = runTest {
        coEvery { contactCacheDao.getContactsByName(any()) } returns emptyList()
        every { contactCacheDao.searchContactsByName(any()) } returns flowOf(emptyList())
        val result = repository.checkDuplicate("张三", emptyMap(), emptyMap())
        assertThat(result.isDuplicate).isFalse()
        assertThat(result.similarityScore).isEqualTo(0f)
    }

    // ========== getContactWithFieldsById ==========

    @Test
    fun getContactWithFieldsById_filtersDisabledFields() = runTest {
        val contact = TestDataProvider.testContact(id = 1, name = "张三")
        coEvery { contactCacheDao.getContactById(1L) } returns contact
        coEvery { contactFieldValueCacheDao.getFieldValuesByContactOnce(1L) } returns listOf(
            TestDataProvider.testFieldValue(fieldId = 1L, value = "13800138000"),
            TestDataProvider.testFieldValue(fieldId = 100L, value = "disabled_value")
        )
        coEvery { contactFieldCacheDao.getFieldsByIds(listOf(1L, 100L)) } returns listOf(
            TestDataProvider.testContactField(id = 1, fieldKey = "phone", isEnabled = true),
            TestDataProvider.testContactField(id = 100, fieldKey = "disabled_field", isEnabled = false)
        )
        val result = repository.getContactWithFieldsById(1L)
        assertThat(result).isNotNull()
        assertThat(result!!.fieldValues).hasSize(1)
        assertThat(result.fieldValues[0].fieldKey).isEqualTo("phone")
    }

    // ========== getAllContactsWithFields ==========

    @Test
    fun getAllContactsWithFields_returnsEmptyFieldValues() = runTest {
        every { contactCacheDao.getAllContacts() } returns flowOf(listOf(TestDataProvider.testContact(name = "张三")))
        val result = repository.getAllContactsWithFields().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].fieldValues).isEmpty()
    }

    // ========== QAuxv 导入 ==========

    @Test
    fun findExistingQQContacts_emptyEntries_returnsEmpty() = runTest {
        val result = repository.findExistingQQContacts(emptyList())
        assertThat(result).isEmpty()
        coVerify(exactly = 0) { contactPlatformCacheDao.getPlatformsByKeyAndValues(any(), any()) }
    }

    @Test
    fun findExistingQQContacts_threeEntries_oneMatches_returnsMap() = runTest {
        val entries = listOf(
            QAuxvFriendEntry(10001L, "A", "A", "a", 4),
            QAuxvFriendEntry(10002L, "B", "B", "b", 4),
            QAuxvFriendEntry(10003L, "C", "C", "c", 4),
        )
        // 10002 已存在 contactId 99
        coEvery { contactPlatformCacheDao.getPlatformsByKeyAndValues("qq", listOf("10001", "10002", "10003")) } returns listOf(
            ContactPlatformCacheEntity(contactId = 99L, platformKey = "qq", value = "10002")
        )
        val result = repository.findExistingQQContacts(entries)
        assertThat(result).hasSize(1)
        assertThat(result[10002L]).isEqualTo(99L)
    }

    @Test
    fun findExistingQQContacts_callsPlatformKeyQq() = runTest {
        val entries = listOf(QAuxvFriendEntry(1L, "A", "A", "a", 4))
        coEvery { contactPlatformCacheDao.getPlatformsByKeyAndValues(any(), any()) } returns emptyList()
        repository.findExistingQQContacts(entries)
        coVerify { contactPlatformCacheDao.getPlatformsByKeyAndValues("qq", listOf("1")) }
    }

    @Test
    fun importQAuxvFriends_insertAnyway_3New_inserts3() = runTest {
        stubAvatarDownloader(returnBmp = null)
        coEvery { contactCacheDao.insertContact(any()) } returnsMany listOf(10L, 11L, 12L)
        coEvery { contactCacheDao.bumpContact(any()) } returns Unit
        coEvery { contactPlatformCacheDao.insertPlatform(any()) } returns 1L
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "A", "A", "a", 4), null, QAuxvConflictAction.InsertAnyway),
            Triple(QAuxvFriendEntry(2L, "B", "B", "b", 4), null, QAuxvConflictAction.InsertAnyway),
            Triple(QAuxvFriendEntry(3L, "C", "C", "c", 4), null, QAuxvConflictAction.InsertAnyway),
        )
        val result = repository.importQAuxvFriends(decisions, context)
        assertThat(result.inserted).isEqualTo(3)
        assertThat(result.replaced).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(0)
        coVerify(exactly = 3) { contactCacheDao.insertContact(any()) }
        coVerify(exactly = 3) { contactCacheDao.bumpContact(any()) }
        coVerify(exactly = 3) { contactPlatformCacheDao.insertPlatform(any()) }
    }

    @Test
    fun importQAuxvFriends_skip_1_doesNotWrite() = runTest {
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "A", "A", "a", 4), 99L, QAuxvConflictAction.Skip),
        )
        val result = repository.importQAuxvFriends(decisions, context)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.inserted).isEqualTo(0)
        assertThat(result.replaced).isEqualTo(0)
        coVerify(exactly = 0) { contactCacheDao.insertContact(any()) }
    }

    @Test
    fun importQAuxvFriends_replace_existingValid_updatesContact() = runTest {
        coEvery { contactCacheDao.getContactById(99L) } returns TestDataProvider.testContact(id = 99, name = "OldName")
        coEvery { contactCacheDao.updateContact(any()) } returns Unit
        coEvery { contactCacheDao.bumpContact(99L) } returns Unit
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "NewName", "NewName", "New", 4), 99L, QAuxvConflictAction.Replace),
        )
        val result = repository.importQAuxvFriends(decisions, context)
        assertThat(result.replaced).isEqualTo(1)
        assertThat(result.inserted).isEqualTo(0)
        coVerify(exactly = 1) { contactCacheDao.updateContact(any()) }
        coVerify(exactly = 1) { contactCacheDao.bumpContact(99L) }
        coVerify(exactly = 1) { contactPlatformCacheDao.insertPlatform(any()) }
    }

    @Test
    fun importQAuxvFriends_replace_existingInvalid_fallbackToInsert() = runTest {
        stubAvatarDownloader(returnBmp = null)
        coEvery { contactCacheDao.insertContact(any()) } returns 50L
        coEvery { contactCacheDao.bumpContact(any()) } returns Unit
        // existingId = -1L 视为无效（Impl 用 takeIf { it > 0L }）
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "Name", "Name", "n", 4), -1L, QAuxvConflictAction.Replace),
        )
        val result = repository.importQAuxvFriends(decisions, context)
        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.replaced).isEqualTo(0)
        coVerify(exactly = 1) { contactCacheDao.insertContact(any()) }
        coVerify(exactly = 1) { contactCacheDao.bumpContact(any()) }
    }

    @Test
    fun importQAuxvFriends_mixed_threeActions_correctSummary() = runTest {
        coEvery { contactCacheDao.getContactById(10L) } returns TestDataProvider.testContact(id = 10, name = "Old")
        coEvery { contactCacheDao.updateContact(any()) } returns Unit
        coEvery { contactCacheDao.bumpContact(any()) } returns Unit
        coEvery { contactCacheDao.insertContact(any()) } returns 20L
        coEvery { contactPlatformCacheDao.insertPlatform(any()) } returns 1L
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "Insert", null, null, 4), null, QAuxvConflictAction.InsertAnyway),
            Triple(QAuxvFriendEntry(2L, "Replace", null, null, 4), 10L, QAuxvConflictAction.Replace),
            Triple(QAuxvFriendEntry(3L, "Skip", null, null, 4), 10L, QAuxvConflictAction.Skip),
        )
        val result = repository.importQAuxvFriends(decisions, context)
        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.replaced).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    fun importQAuxvFriends_insertAnyway_writesQqPlatformKey() = runTest {
        stubAvatarDownloader(returnBmp = null)
        coEvery { contactCacheDao.insertContact(any()) } returns 1L
        coEvery { contactCacheDao.bumpContact(any()) } returns Unit
        val capturedPlatform = mutableListOf<ContactPlatformCacheEntity>()
        coEvery { contactPlatformCacheDao.insertPlatform(capture(capturedPlatform)) } answers {
            capturedPlatform.size.toLong()
        }
        repository.importQAuxvFriends(
            listOf(Triple(QAuxvFriendEntry(12345L, "x", null, null, 4), null, QAuxvConflictAction.InsertAnyway)),
            context,
        )
        assertThat(capturedPlatform).hasSize(1)
        val cp = capturedPlatform[0]
        assertThat(cp.platformKey).isEqualTo("qq")
        assertThat(cp.value).isEqualTo("12345")
        assertThat(cp.displayName).isEqualTo("x")
        assertThat(cp.jumpLink).startsWith("https://tool.gljlw.com/qq/?qq=")
        // 头像 URL 写入 ContactPlatformCacheEntity
        assertThat(cp.avatarUrl).isEqualTo("https://q1.qlogo.cn/g?b=qq&nk=12345&s=100")
    }

    // ========== 头像导入 ==========

    @Test
    fun importQAuxvFriends_insertAnyway_avatarDownloadNull_writesRemoteUrlOnly() = runTest {
        // 头像下载失败时 avatarPath = null，但 ContactPlatformCacheEntity.avatarUrl 仍是远程 URL
        stubAvatarDownloader(returnBmp = null)
        coEvery { contactCacheDao.insertContact(any()) } returns 1L
        coEvery { contactCacheDao.bumpContact(any()) } returns Unit
        val capturedContact = mutableListOf<ContactCacheEntity>()
        coEvery { contactCacheDao.insertContact(capture(capturedContact)) } answers { capturedContact.size.toLong() }
        repository.importQAuxvFriends(
            listOf(Triple(QAuxvFriendEntry(12345L, "x", null, null, 4), null, QAuxvConflictAction.InsertAnyway)),
            context,
        )
        assertThat(capturedContact).hasSize(1)
        assertThat(capturedContact[0].avatarPath).isNull()
        assertThat(capturedContact[0].avatarUrl).isEqualTo("https://q1.qlogo.cn/g?b=qq&nk=12345&s=100")
        // [修复防御]: pinyinInitial 现在由 Impl 自动填，不再写空字符串
        assertThat(capturedContact[0].pinyinInitial).isEqualTo("X")
    }

    @Test
    fun importQAuxvFriends_avatarDownloadInvokesDownloaderWithQqUrl() = runTest {
        // 头像下载器收到的 URL 应当是 q1.qlogo.cn 模板
        var capturedUrl: String? = null
        ContactRepositoryImpl.avatarDownloader = { url ->
            capturedUrl = url
            null
        }
        coEvery { contactCacheDao.insertContact(any()) } returns 1L
        coEvery { contactCacheDao.bumpContact(any()) } returns Unit
        repository.importQAuxvFriends(
            listOf(Triple(QAuxvFriendEntry(777L, "x", null, null, 4), null, QAuxvConflictAction.InsertAnyway)),
            context,
        )
        assertThat(capturedUrl).isEqualTo("https://q1.qlogo.cn/g?b=qq&nk=777&s=100")
    }

    @Test
    fun importQAuxvFriends_avatarDownloadSuccess_passesAvatarPathToInsert() = runTest {
        // 用 mockk 提供非 null Bitmap，避免依赖真实 Android graphics 栈
        val bmp = mockk<Bitmap>(relaxed = true)
        stubAvatarDownloader(returnBmp = bmp)
        // 使用临时目录代替真实 filesDir
        val tmpDir = kotlin.io.path.createTempDirectory("avatar-test").toFile()
        every { context.filesDir } returns tmpDir
        coEvery { contactCacheDao.insertContact(any()) } returns 1L
        coEvery { contactCacheDao.bumpContact(any()) } returns Unit
        val capturedContact = mutableListOf<ContactCacheEntity>()
        coEvery { contactCacheDao.insertContact(capture(capturedContact)) } answers { capturedContact.size.toLong() }

        repository.importQAuxvFriends(
            listOf(Triple(QAuxvFriendEntry(999L, "x", null, null, 4), null, QAuxvConflictAction.InsertAnyway)),
            context,
        )
        assertThat(capturedContact).hasSize(1)
        val avatarPath = capturedContact[0].avatarPath
        assertThat(avatarPath).isNotNull()
        assertThat(avatarPath!!).endsWith("contact_qq_999_avatar.webp")
        // [修复防御]: pinyinInitial 现在由 Impl 自动填，不再写空字符串
        assertThat(capturedContact[0].pinyinInitial).isEqualTo("X")
        // 清理临时目录
        tmpDir.deleteRecursively()
    }

    @Test
    fun importQAuxvFriends_skip_doesNotInvokeAvatarDownloader() = runTest {
        var downloadCalls = 0
        ContactRepositoryImpl.avatarDownloader = {
            downloadCalls++
            null
        }
        repository.importQAuxvFriends(
            listOf(Triple(QAuxvFriendEntry(1L, "x", null, null, 4), null, QAuxvConflictAction.Skip)),
            context,
        )
        assertThat(downloadCalls).isEqualTo(0)
    }

    @Test
    fun importQAuxvFriends_replace_oldAvatarDeleted_whenNewDownloaded() = runTest {
        val bmp = mockk<Bitmap>(relaxed = true)
        stubAvatarDownloader(returnBmp = bmp)
        val tmpDir = kotlin.io.path.createTempDirectory("avatar-replace").toFile()
        every { context.filesDir } returns tmpDir

        val oldAvatar = java.io.File(tmpDir, "old_avatar.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        coEvery { contactCacheDao.getContactById(99L) } returns TestDataProvider.testContact(
            id = 99, name = "Old", avatarPath = oldAvatar.absolutePath
        )
        coEvery { contactCacheDao.updateContact(any()) } returns Unit
        coEvery { contactCacheDao.bumpContact(99L) } returns Unit

        repository.importQAuxvFriends(
            listOf(Triple(QAuxvFriendEntry(1L, "NewName", "NewName", "New", 4), 99L, QAuxvConflictAction.Replace)),
            context,
        )
        assertThat(oldAvatar.exists()).isFalse()  // 旧头像被删
        tmpDir.deleteRecursively()
    }

    @Test
    fun importQAuxvFriends_onProgress_receivesAllUpdates() = runTest {
        stubAvatarDownloader(returnBmp = null)
        coEvery { contactCacheDao.insertContact(any()) } returnsMany listOf(1L, 2L, 3L)
        val progresses = mutableListOf<QAuxvImportProgress>()
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "A", null, null, 4), null, QAuxvConflictAction.InsertAnyway),
            Triple(QAuxvFriendEntry(2L, "B", null, null, 4), null, QAuxvConflictAction.InsertAnyway),
        )
        repository.importQAuxvFriends(decisions, context) { progresses.add(it) }
        // 必须出现的里程碑：下载开始 0/2、下载结束 2/2、写入开始 0/2、写入结束 2/2
        val downloadStarts = progresses.filter {
            it.phase == QAuxvImportProgress.Phase.AvatarDownloading && it.current == 0
        }
        val downloadEnds = progresses.filter {
            it.phase == QAuxvImportProgress.Phase.AvatarDownloading && it.current == 2
        }
        val writeStarts = progresses.filter {
            it.phase == QAuxvImportProgress.Phase.Writing && it.current == 0
        }
        val writeEnds = progresses.filter {
            it.phase == QAuxvImportProgress.Phase.Writing && it.current == 2
        }
        assertThat(downloadStarts).hasSize(1)
        assertThat(downloadEnds).hasSize(1)
        assertThat(writeStarts).hasSize(1)
        assertThat(writeEnds).hasSize(1)
    }
}
