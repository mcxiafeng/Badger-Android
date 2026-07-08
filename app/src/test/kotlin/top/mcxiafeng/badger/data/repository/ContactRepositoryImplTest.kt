package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.testutil.TestDataProvider
import top.mcxiafeng.badger.utils.HttpUtil

class ContactRepositoryImplTest {

    private lateinit var contactDao: ContactDao
    private lateinit var contactFieldDao: ContactFieldDao
    private lateinit var customFieldDao: CustomFieldDao
    private lateinit var contactFieldValueDao: ContactFieldValueDao
    private lateinit var scanResultDao: ScanResultDao
    private lateinit var contactPlatformDao: ContactPlatformDao
    private lateinit var contactFtsDao: ContactFtsDao
    private lateinit var repository: ContactRepositoryImpl
    private lateinit var context: Context

    @Before
    fun setup() {
        contactDao = mockk(relaxed = true)
        contactFieldDao = mockk(relaxed = true)
        customFieldDao = mockk(relaxed = true)
        contactFieldValueDao = mockk(relaxed = true)
        scanResultDao = mockk(relaxed = true)
        contactPlatformDao = mockk(relaxed = true)
        contactFtsDao = mockk(relaxed = true)
        repository = ContactRepositoryImpl(
            contactDao, contactFieldDao, customFieldDao,
            contactFieldValueDao, scanResultDao, contactPlatformDao, contactFtsDao
        )
        context = mockk(relaxed = true)
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

    // ========== calculateNameSimilarity (via reflection) ==========

    @Test
    fun calculateNameSimilarity_identicalNames_returns1() {
        val method = ContactRepositoryImpl::class.java.getDeclaredMethod(
            "calculateNameSimilarity", String::class.java, String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(repository, "张三", "张三") as Float
        assertThat(result).isEqualTo(1.0f)
    }

    @Test
    fun calculateNameSimilarity_completelyDifferent_returns0() {
        val method = ContactRepositoryImpl::class.java.getDeclaredMethod(
            "calculateNameSimilarity", String::class.java, String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(repository, "ABC", "XYZ") as Float
        assertThat(result).isEqualTo(0.0f)
    }

    @Test
    fun calculateNameSimilarity_partialOverlap_returnsBetween0And1() {
        val method = ContactRepositoryImpl::class.java.getDeclaredMethod(
            "calculateNameSimilarity", String::class.java, String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(repository, "AB", "BC") as Float
        assertThat(result).isGreaterThan(0f)
        assertThat(result).isLessThan(1f)
    }

    @Test
    fun calculateNameSimilarity_caseInsensitive_returns1() {
        val method = ContactRepositoryImpl::class.java.getDeclaredMethod(
            "calculateNameSimilarity", String::class.java, String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(repository, "test", "TEST") as Float
        assertThat(result).isEqualTo(1.0f)
    }

    // ========== checkDuplicate ==========

    @Test
    fun checkDuplicate_emptyFieldValues_returnsNotDuplicate() = runTest {
        coEvery { contactDao.getContactsByName(any()) } returns emptyList()
        every { contactDao.searchContactsByName(any()) } returns flowOf(emptyList())
        val result = repository.checkDuplicate("张三", emptyMap(), emptyMap())
        assertThat(result.isDuplicate).isFalse()
        assertThat(result.similarityScore).isEqualTo(0f)
    }

    // ========== getContactWithFieldsById ==========

    @Test
    fun getContactWithFieldsById_filtersDisabledFields() = runTest {
        val contact = TestDataProvider.testContact(id = 1, name = "张三")
        coEvery { contactDao.getContactById(1L) } returns contact
        coEvery { contactFieldValueDao.getFieldValuesByContactOnce(1L) } returns listOf(
            TestDataProvider.testFieldValue(fieldId = 1L, value = "13800138000"),
            TestDataProvider.testFieldValue(fieldId = 100L, value = "disabled_value")
        )
        coEvery { contactFieldDao.getFieldsByIds(listOf(1L, 100L)) } returns listOf(
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
        every { contactDao.getAllContacts() } returns flowOf(listOf(TestDataProvider.testContact(name = "张三")))
        val result = repository.getAllContactsWithFields().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].fieldValues).isEmpty()
    }

    // ========== QAuxv 导入 ==========

    @Test
    fun findExistingQQContacts_emptyEntries_returnsEmpty() = runTest {
        val result = repository.findExistingQQContacts(emptyList())
        assertThat(result).isEmpty()
        coVerify(exactly = 0) { contactPlatformDao.getPlatformsByKeyAndValues(any(), any()) }
    }

    @Test
    fun findExistingQQContacts_threeEntries_oneMatches_returnsMap() = runTest {
        val entries = listOf(
            QAuxvFriendEntry(10001L, "A", "A", "a", 4),
            QAuxvFriendEntry(10002L, "B", "B", "b", 4),
            QAuxvFriendEntry(10003L, "C", "C", "c", 4),
        )
        // 10002 已存在 contactId 99
        coEvery { contactPlatformDao.getPlatformsByKeyAndValues("qq", listOf("10001", "10002", "10003")) } returns listOf(
            ContactPlatform(contactId = 99L, platformKey = "qq", value = "10002")
        )
        val result = repository.findExistingQQContacts(entries)
        assertThat(result).hasSize(1)
        assertThat(result[10002L]).isEqualTo(99L)
    }

    @Test
    fun findExistingQQContacts_callsPlatformKeyQq() = runTest {
        val entries = listOf(QAuxvFriendEntry(1L, "A", "A", "a", 4))
        coEvery { contactPlatformDao.getPlatformsByKeyAndValues(any(), any()) } returns emptyList()
        repository.findExistingQQContacts(entries)
        coVerify { contactPlatformDao.getPlatformsByKeyAndValues("qq", listOf("1")) }
    }

    @Test
    fun importQAuxvFriends_insertAnyway_3New_inserts3() = runTest {
        stubAvatarDownloader(returnBmp = null)
        coEvery { contactDao.insertContact(any()) } returnsMany listOf(10L, 11L, 12L)
        coEvery { contactDao.bumpContact(any()) } returns Unit
        coEvery { contactPlatformDao.insertPlatform(any()) } returns 1L
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "A", "A", "a", 4), null, QAuxvConflictAction.InsertAnyway),
            Triple(QAuxvFriendEntry(2L, "B", "B", "b", 4), null, QAuxvConflictAction.InsertAnyway),
            Triple(QAuxvFriendEntry(3L, "C", "C", "c", 4), null, QAuxvConflictAction.InsertAnyway),
        )
        val result = repository.importQAuxvFriends(decisions, context)
        assertThat(result.inserted).isEqualTo(3)
        assertThat(result.replaced).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(0)
        coVerify(exactly = 3) { contactDao.insertContact(any()) }
        coVerify(exactly = 3) { contactDao.bumpContact(any()) }
        coVerify(exactly = 3) { contactPlatformDao.insertPlatform(any()) }
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
        coVerify(exactly = 0) { contactDao.insertContact(any()) }
    }

    @Test
    fun importQAuxvFriends_replace_existingValid_updatesContact() = runTest {
        coEvery { contactDao.getContactById(99L) } returns TestDataProvider.testContact(id = 99, name = "OldName")
        coEvery { contactDao.updateContact(any()) } returns Unit
        coEvery { contactDao.bumpContact(99L) } returns Unit
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "NewName", "NewName", "New", 4), 99L, QAuxvConflictAction.Replace),
        )
        val result = repository.importQAuxvFriends(decisions, context)
        assertThat(result.replaced).isEqualTo(1)
        assertThat(result.inserted).isEqualTo(0)
        coVerify(exactly = 1) { contactDao.updateContact(any()) }
        coVerify(exactly = 1) { contactDao.bumpContact(99L) }
        coVerify(exactly = 1) { contactPlatformDao.insertPlatform(any()) }
    }

    @Test
    fun importQAuxvFriends_replace_existingInvalid_fallbackToInsert() = runTest {
        stubAvatarDownloader(returnBmp = null)
        coEvery { contactDao.insertContact(any()) } returns 50L
        coEvery { contactDao.bumpContact(any()) } returns Unit
        // existingId = -1L 视为无效（Impl 用 takeIf { it > 0L }）
        val decisions = listOf(
            Triple(QAuxvFriendEntry(1L, "Name", "Name", "n", 4), -1L, QAuxvConflictAction.Replace),
        )
        val result = repository.importQAuxvFriends(decisions, context)
        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.replaced).isEqualTo(0)
        coVerify(exactly = 1) { contactDao.insertContact(any()) }
        coVerify(exactly = 1) { contactDao.bumpContact(any()) }
    }

    @Test
    fun importQAuxvFriends_mixed_threeActions_correctSummary() = runTest {
        coEvery { contactDao.getContactById(10L) } returns TestDataProvider.testContact(id = 10, name = "Old")
        coEvery { contactDao.updateContact(any()) } returns Unit
        coEvery { contactDao.bumpContact(any()) } returns Unit
        coEvery { contactDao.insertContact(any()) } returns 20L
        coEvery { contactPlatformDao.insertPlatform(any()) } returns 1L
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
        coEvery { contactDao.insertContact(any()) } returns 1L
        coEvery { contactDao.bumpContact(any()) } returns Unit
        val capturedPlatform = mutableListOf<ContactPlatform>()
        coEvery { contactPlatformDao.insertPlatform(capture(capturedPlatform)) } returns 1L
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
        // 头像 URL 写入 ContactPlatform
        assertThat(cp.avatarUrl).isEqualTo("https://q1.qlogo.cn/g?b=qq&nk=12345&s=100")
    }

    // ========== 头像导入 ==========

    @Test
    fun importQAuxvFriends_insertAnyway_avatarDownloadNull_writesRemoteUrlOnly() = runTest {
        // 头像下载失败时 avatarPath = null，但 ContactPlatform.avatarUrl 仍是远程 URL
        stubAvatarDownloader(returnBmp = null)
        coEvery { contactDao.insertContact(any()) } returns 1L
        coEvery { contactDao.bumpContact(any()) } returns Unit
        val capturedContact = mutableListOf<Contact>()
        coEvery { contactDao.insertContact(capture(capturedContact)) } answers { capturedContact.size.toLong() }
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
        coEvery { contactDao.insertContact(any()) } returns 1L
        coEvery { contactDao.bumpContact(any()) } returns Unit
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
        coEvery { contactDao.insertContact(any()) } returns 1L
        coEvery { contactDao.bumpContact(any()) } returns Unit
        val capturedContact = mutableListOf<Contact>()
        coEvery { contactDao.insertContact(capture(capturedContact)) } answers { capturedContact.size.toLong() }

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
        coEvery { contactDao.getContactById(99L) } returns TestDataProvider.testContact(
            id = 99, name = "Old", avatarPath = oldAvatar.absolutePath
        )
        coEvery { contactDao.updateContact(any()) } returns Unit
        coEvery { contactDao.bumpContact(99L) } returns Unit

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
        coEvery { contactDao.insertContact(any()) } returnsMany listOf(1L, 2L, 3L)
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