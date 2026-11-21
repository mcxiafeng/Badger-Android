package top.mcxiafeng.badger.data.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
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
import top.mcxiafeng.badger.data.ContactTagCrossRef
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.ScanResult
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WorkspaceCodecTest {
    private lateinit var context: Context
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { source.close(); target.close() }

    @Test fun allBusinessFieldsRoundTripWithDuplicateScanRecords() = runBlocking<Unit> {
        seedFull(source)
        val before = capture(source)
        target.withTransaction {
            val ledger = SyncLedger.load(target)
            WorkspaceApplier(ledger).bindExisting(before.values.toList())
            WorkspaceApplier(ledger).apply(before.values.toList())
            before.values.forEach { ledger.shadow(it.copy(version = "1")) }
        }
        val after = capture(target)
        assertThat(after.mapValues { it.value.value }).isEqualTo(before.mapValues { it.value.value })
        assertThat(target.contactDao().getAllContacts().first().map { it.name }).containsExactly("朋友")
        val sql = target.openHelper.writableDatabase
        assertThat(sql.rows("scan_results")).hasSize(3)
        assertThat(sql.rows("contact_tag")).hasSize(2)
        assertThat(sql.rows("contact_platforms").first { it.str("platformKey") == "custom" }.str("value")).isNull()
        assertThat(sql.rows("contact_platforms").first { it.str("platformKey") == "custom" }.str("jumpLink")).isEqualTo("custom-app://person")
        assertThat(target.userProfileDao().getProfileOnce()?.updateTime).isEqualTo(33L)
        assertThat(target.contactDao().getContactById(-1)?.note).isEqualTo("私有自我备注")
        val hits = sql.query("SELECT rowid FROM contacts_fts WHERE contacts_fts MATCH ?", arrayOf("roundtripmarker")).use { it.count }
        assertThat(hits).isEqualTo(1)
    }

    @Test fun failedApplyRollsBackBusinessRowsAndIdentityMappings() = runBlocking<Unit> {
        seedFull(source)
        val roots = capture(source).values.toList()
        target.contactDao().insertContact(Contact(id = 1, name = "原有资料"))
        val baseline = capture(target)
        try {
            target.withTransaction {
                val ledger = SyncLedger.load(target)
                WorkspaceApplier(ledger).apply(roots.filter { it.kind == "person" })
            }
            error("Expected missing definition to reject application")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("字段定义")
        }
        assertThat(capture(target).mapValues { it.value.value }).isEqualTo(baseline.mapValues { it.value.value })
        assertThat(target.contactDao().getAllContacts().first().map { it.name }).containsExactly("原有资料")
    }

    @Test fun initialImportConflictCannotBeClearedByTheNextSnapshot() = runBlocking<Unit> {
        seedSimple(source)
        val local = capture(source)
        val remote = local.values.map { root ->
            root.copy(version = "1", value = root.value?.deepCopy()?.apply { if (root.kind == "person") addProperty("notes", "云端重要备注") })
        }
        source.withTransaction {
            val ledger = SyncLedger.load(source)
            remote.forEach { ledger.shadow(it) }
            ledger.put(SyncReconciler.INITIALIZED, "true")
            val theirs = remote.first { it.kind == "person" }
            val mine = local.getValue(theirs.key)
            ledger.put("conflict/${theirs.key}", SyncJson.encode(StoredConflict(theirs.key, null, mine, theirs, listOf("notes")).json()))
            SyncReconciler(ledger, context).accept(remote, "1")
        }
        assertThat(source.contactDao().getContactById(1)?.note).isEqualTo("本地导入备注")
        assertThat(source.syncStateDao().getAll().count { it.key.startsWith("conflict/") }).isEqualTo(1)
    }

    @Test fun deleteEditConflictKeepsContactAndContainerRelationships() = runBlocking<Unit> {
        seedFull(source)
        val baseline = capture(source)
        source.withTransaction {
            val ledger = SyncLedger.load(source)
            baseline.values.forEach { ledger.shadow(it.copy(version = "1")) }
            ledger.put(SyncReconciler.INITIALIZED, "true")
        }
        val person = requireNotNull(source.contactDao().getContactById(1))
        source.contactDao().updateContact(person.copy(note = "尚未上传的重要修改", updateTime = 40))
        val personId = baseline.values.first { it.kind == "person" }.id
        val remote = baseline.values.map { root ->
            when (root.kind) {
                "person" -> root.copy(version = "2", deleted = true, value = null)
                "collection", "tag" -> root.copy(version = "2", value = root.value?.deepCopy()?.apply {
                    val memberIds = getAsJsonArray("memberIds")
                    memberIds.filter { it.asString == personId }.forEach { memberIds.remove(it) }
                    val details = getAsJsonObject("clientData")
                    val field = if (root.kind == "collection") "records" else "members"
                    val records = details.getAsJsonArray(field)
                    records.filter { it.asJsonObject.str("personId") == personId }.forEach { records.remove(it) }
                })
                else -> root.copy(version = "1")
            }
        }
        source.withTransaction { SyncReconciler(SyncLedger.load(source), context).accept(remote, "2") }
        assertThat(source.contactDao().getContactById(1)?.note).isEqualTo("尚未上传的重要修改")
        assertThat(source.openHelper.writableDatabase.rows("scan_results")).hasSize(3)
        assertThat(source.openHelper.writableDatabase.rows("contact_tag")).hasSize(2)
        assertThat(source.syncStateDao().getAll().count { it.key.startsWith("conflict/") }).isAtLeast(3)
    }

    @Test fun canonicalReceiptBaselineRemainsPendingUntilActuallyAdopted() = runBlocking<Unit> {
        seedSimple(source)
        val local = capture(source)
        val remote = local.values.map { root -> root.copy(version = "1", value = root.value?.deepCopy()?.apply {
            if (root.kind == "person") addProperty("notes", "第一行\n第二行")
        }) }
        source.withTransaction {
            val ledger = SyncLedger.load(source)
            remote.forEach { ledger.shadow(it) }
            val person = remote.single { it.kind == "person" }
            val request = person.copy(value = person.value?.deepCopy()?.apply { addProperty("notes", "第一行\r\n第二行") })
            ledger.shadow(request, verified = false)
            ledger.put("unverified/${person.key}", "1")
            ledger.put(SyncReconciler.INITIALIZED, "true")
            SyncReconciler(ledger, context).accept(remote, "1")
            SyncReconciler(ledger, context).accept(remote, "1")
            assertThat(ledger.get("unverified/${person.key}")).isEqualTo("1")
            assertThat(ledger.entries("conflict/")).hasSize(1)
            ledger.shadow(person)
            assertThat(ledger.get("unverified/${person.key}")).isNull()
        }
        assertThat(source.contactDao().getContactById(1)?.note).isEqualTo("本地导入备注")
    }

    private suspend fun capture(db: AppDatabase): Map<String, SyncRoot> = db.withTransaction {
        WorkspaceCodec(SyncLedger.load(db), context).capture()
    }
    private suspend fun seedSimple(db: AppDatabase) {
        db.contactDao().insertContact(Contact(id = -1, name = "本人", createTime = 1, updateTime = 2))
        db.contactDao().insertContact(Contact(id = 1, name = "朋友", note = "本地导入备注", createTime = 1, updateTime = 2))
        db.userProfileDao().saveProfile(UserProfile(name = "本人", updateTime = 3))
    }
    private suspend fun seedFull(db: AppDatabase) {
        seedSimple(db)
        db.contactDao().updateContact(Contact(id = -1, name = "本人", note = "私有自我备注", bio = "自我介绍", createTime = 11, updateTime = 22))
        db.contactDao().updateContact(Contact(id = 1, name = "朋友", note = "私有备注", bio = "roundtripmarker", createTime = 12, updateTime = 23))
        db.userProfileDao().saveProfile(UserProfile(name = "本人", defaultPlatform = "qq", updateTime = 33))
        db.contactFieldDao().insertField(ContactField(id = 1, fieldName = "手机", fieldKey = "phone", isSystem = true, createTime = 10))
        db.customFieldDao().insertCustomField(CustomField(id = 1, fieldName = "公司", fieldType = "text", options = "[]", sortOrder = 2, createTime = 10))
        db.contactFieldValueDao().insertFieldValue(ContactFieldValue(id = 1, contactId = 1, fieldId = 1, value = "12345", createTime = 12, updateTime = 23))
        db.contactFieldValueDao().insertFieldValue(ContactFieldValue(id = 2, contactId = 1, customFieldId = 1, value = "某公司", createTime = 12, updateTime = 23))
        db.contactPlatformDao().insertPlatform(ContactPlatform(id = 1, contactId = 1, platformKey = "custom", value = null,
            jumpLink = "custom-app://person", originalLink = "https://source.test/person", avatarUrl = "https://source.test/avatar"))
        db.contactPlatformDao().insertPlatform(ContactPlatform(id = 2, contactId = 1, platformKey = "qq", value = "123", jumpLink = "", displayName = "  A  B  "))
        db.cardCollectionDao().insertCollection(CardCollection(id = 1, name = "活动", description = "来源说明", dominantColor = -1L, createTime = 5))
        db.scanResultDao().insertScanResult(ScanResult(id = 1, contactId = 1, collectionId = 1, sourceType = "scan", rawData = "原文1", scannedTime = 7, confidence = 0.8f))
        db.scanResultDao().insertScanResult(ScanResult(id = 2, contactId = 1, collectionId = 1, sourceType = "photo", ocrText = "文字2", qrCodeContent = "二维码2", scannedTime = 8, confidence = 0.7f))
        db.scanResultDao().insertScanResult(ScanResult(id = 3, contactId = -1, collectionId = 1, sourceType = "manual", scannedTime = 9))
        db.tagDao().insertTag(Tag(id = 1, name = "认识", color = -123456L, showDot = false, source = "manual", createTime = 8))
        db.contactTagDao().insertCrossRef(ContactTagCrossRef(contactId = 1, tagId = 1, source = "import", confidence = 0.7f, createTime = 9))
        db.contactTagDao().insertCrossRef(ContactTagCrossRef(contactId = -1, tagId = 1, source = "manual", confidence = 1f, createTime = 10))
    }
}
