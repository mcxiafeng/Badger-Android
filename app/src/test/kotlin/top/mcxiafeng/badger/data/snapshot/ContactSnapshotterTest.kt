package top.mcxiafeng.badger.data.snapshot

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity

/**
 * [V2-P3] ContactSnapshotter 单元测试。
 *
 * 覆盖规约 docs/BADGER_V2_CLIENT_PLAN.md §5.5.1 + §6.4:
 * 1. toJsonFromCache:contact 主体 + 关联子表(platforms / fields / tags)完整序列化
 * 2. toJson(in-memory):传 List 完整序列化(允许 caller 提供预先读好的子表)
 * 3. fromJson:round-trip 不丢字段
 * 4. fromJson:空 JSON / null / 缺字段 兜底为 notFound,而不是抛错
 * 5. fromJson:含中文 / 特殊字符 / 嵌套结构 不丢字符
 * 6. 内部 Gson 异常(JsonSyntaxException)降级
 *
 * 跑 Robolectric 是为了拿到 Application context,与 P2 测试一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactSnapshotterTest {

    private lateinit var db: AppDatabase
    private lateinit var snapshotter: ContactSnapshotter

    private val contact = ContactCacheEntity(
        id = 42L,
        serverId = "srv-42",
        name = "张三",
        avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=10001&s=100",
        avatarPath = "/data/user/0/top.mcxiafeng.badger/files/avatar42.webp",
        note = "大学同学",
        bio = "前端工程师",
        pinyinInitial = "Z",
        createTime = 1_700_000_000_000L,
        updateTime = 1_700_000_005_000L,
        serverVersion = 7L,
        lastSyncedAt = 1_700_000_010_000L,
        isLocalOnly = false,
        isDeleted = false,
    )

    private val platforms = listOf(
        ContactPlatformCacheEntity(
            contactId = 42L,
            platformKey = "qq",
            value = "10001",
            displayName = "张三",
            jumpLink = "mqq://im/chat?chat_type=wpa&uin=10001",
            originalLink = "https://wpa.qq.com/msgrd?v=3&uin=10001",
            avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=10001&s=100",
        ),
        ContactPlatformCacheEntity(
            contactId = 42L,
            platformKey = "email",
            value = "zhangsan@example.com",
            jumpLink = "mailto:zhangsan@example.com",
            originalLink = null,
            displayName = null,
            avatarUrl = null,
        ),
    )

    private val fieldValues = listOf(
        ContactFieldValueCacheEntity(
            id = 101L,
            contactId = 42L,
            fieldId = 11L,
            customFieldId = null,
            value = "+86 138-0000-0001",
            displayOrder = 1,
            createTime = 1_700_000_000_000L,
            updateTime = 1_700_000_005_000L,
        ),
        ContactFieldValueCacheEntity(
            id = 102L,
            contactId = 42L,
            fieldId = null,
            customFieldId = 22L,
            value = "广州市天河区",
            displayOrder = 2,
            createTime = 1_700_000_000_000L,
            updateTime = 1_700_000_005_000L,
        ),
    )

    private val tag = TagCacheEntity(
        id = 5L,
        name = "大学同学",
        color = 0xFF1976D2L,
        pinyinInitial = "D",
        source = "manual",
        showDot = true,
        createTime = 1_700_000_000_000L,
    )

    @Before
    fun setup() = runBlocking {
        // [§14.2] Robolectric 测试不走 BadgerApplication.onCreate;若 ViewModel/Repository
        // 任何路径触到 KoinComponentBy.get(),必须先 startKoin。
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { RuntimeEnvironment.getApplication() }
                    single { AppDatabase.build(get()) }
                },
            )
        }
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        snapshotter = ContactSnapshotter(
            context = RuntimeEnvironment.getApplication(),
            contactCacheDao = db.contactCacheDao(),
            contactPlatformCacheDao = db.contactPlatformCacheDao(),
            contactFieldValueCacheDao = db.contactFieldValueCacheDao(),
            contactTagCacheDao = db.contactTagCacheDao(),
            tagCacheDao = db.tagCacheDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { GlobalContext.stopKoin() }
    }

    // ============ toJsonFromCache ============

    @Test
    fun toJsonFromCache_serializesContactAndRelatedTables() = runTest {
        db.contactCacheDao().insertContact(contact)
        platforms.forEach { db.contactPlatformCacheDao().insertPlatform(it) }
        db.contactFieldValueCacheDao().insertFieldValue(fieldValues[0])
        db.contactFieldValueCacheDao().insertFieldValue(fieldValues[1])
        db.tagCacheDao().insertTag(tag)
        db.contactTagCacheDao().insertCrossRef(
            ContactTagCacheEntity(
                contactId = 42L,
                tagId = 5L,
                source = "manual",
                confidence = 1.0f,
                createTime = 1_700_000_000_000L,
            )
        )

        val json = snapshotter.toJsonFromCache(42L)
        assertThat(json).contains("\"name\":\"张三\"")
        assertThat(json).contains("\"platforms\"")
        assertThat(json).contains("\"qq\"")
        assertThat(json).contains("\"value\":\"10001\"")
        assertThat(json).contains("\"field_values\"")
        assertThat(json).contains("\"display_order\":1")
        assertThat(json).contains("\"tags\"")
        assertThat(json).contains("\"tag_id\":5")
        assertThat(json).contains("\"name\":\"大学同学\"")
        assertThat(json).contains("\"server_version\":7")
    }

    @Test
    fun toJsonFromCache_fallsBackToEmptyWhenContactMissing() = runTest {
        val json = snapshotter.toJsonFromCache(999L)
        // 无联系人 → snapshot.empty(id=999, ...) 必须仍可序列化
        val parsed = com.google.gson.Gson().fromJson(json, ContactSnapshot::class.java)
        assertThat(parsed.contactId).isEqualTo(999L)
        assertThat(parsed.platforms).isEmpty()
        assertThat(parsed.fieldValues).isEmpty()
        assertThat(parsed.tags).isEmpty()
    }

    // ============ toJson (in-memory) ============

    @Test
    fun toJson_acceptsPreReadPlatformsAndFields() = runTest {
        val json = snapshotter.toJson(
            contact = contact,
            platforms = platforms,
            fieldValues = fieldValues,
        )
        assertThat(json).contains("\"name\":\"张三\"")
        assertThat(json).contains("\"qq\"")
        assertThat(json).contains("\"field_values\"")
        // 不应在 cache 现场补查(caller 没有传 tags,这次 toJson 也不会从 tagCacheDao 读)
        assertThat(json).contains("\"tags\":[]")
    }

    @Test
    fun toJson_withEmptyListsSerializesEmptyArrays() = runTest {
        val json = snapshotter.toJson(
            contact = contact.copy(id = 123L),
            platforms = emptyList(),
            fieldValues = emptyList(),
        )
        assertThat(json).contains("\"platforms\":{}")
        assertThat(json).contains("\"field_values\":[]")
        assertThat(json).contains("\"tags\":[]")
        assertThat(json).contains("\"contact_id\":123")
    }

    // ============ fromJson 还原(round-trip)============

    @Test
    fun fromJson_recoversContactEntity() = runTest {
        db.contactCacheDao().insertContact(contact)
        platforms.forEach { db.contactPlatformCacheDao().insertPlatform(it) }
        db.contactFieldValueCacheDao().insertFieldValue(fieldValues[0])
        db.contactFieldValueCacheDao().insertFieldValue(fieldValues[1])
        db.tagCacheDao().insertTag(tag)
        db.contactTagCacheDao().insertCrossRef(
            ContactTagCacheEntity(
                contactId = 42L,
                tagId = 5L,
                source = "manual",
                confidence = 1.0f,
                createTime = 1_700_000_000_000L,
            )
        )

        val json = snapshotter.toJsonFromCache(42L)
        val restored = snapshotter.fromJson(json, contactId = 42L)

        assertThat(restored.contact.id).isEqualTo(42L)
        assertThat(restored.contact.name).isEqualTo("张三")
        assertThat(restored.contact.bio).isEqualTo("前端工程师")
        assertThat(restored.contact.serverVersion).isEqualTo(7L)
        assertThat(restored.platforms).hasSize(2)
        assertThat(restored.platforms.map { it.platformKey }).containsExactly("qq", "email")
        assertThat(restored.fieldValues).hasSize(2)
        assertThat(restored.tags).hasSize(1)
        assertThat(restored.tags[0].name).isEqualTo("大学同学")
    }

    // ============ fromJson 容错 ============

    @Test
    fun fromJson_emptyOrNullJson_returnsNotFound() = runTest {
        val r1 = snapshotter.fromJson(null, contactId = 42L)
        val r2 = snapshotter.fromJson("", contactId = 42L)
        val r3 = snapshotter.fromJson("null", contactId = 42L)
        assertThat(r1.contact.id).isEqualTo(42L)
        assertThat(r1.platforms).isEmpty()
        assertThat(r2.contact.id).isEqualTo(42L)
        assertThat(r3.contact.id).isEqualTo(42L)
    }

    @Test
    fun fromJson_malformedJson_returnsNotFound() = runTest {
        val restored = snapshotter.fromJson("{not json", contactId = 42L)
        assertThat(restored.contact.id).isEqualTo(42L)
        assertThat(restored.platforms).isEmpty()
        // [修复防御]:不该抛异常,只能 notFound 兜底
    }

    @Test
    fun fromJson_jsonMissingOptionalFields_usesDefaults() = runTest {
        val partialJson = """{"version":1,"captured_at":0,"contact_id":42,"name":""}"""
        val restored = snapshotter.fromJson(partialJson, contactId = 42L)
        assertThat(restored.contact.id).isEqualTo(42L)
        assertThat(restored.contact.name).isEqualTo("")
        assertThat(restored.contact.note).isNull()
        assertThat(restored.contact.bio).isNull()
        assertThat(restored.platforms).isEmpty()
        assertThat(restored.fieldValues).isEmpty()
        assertThat(restored.tags).isEmpty()
    }

    // ============ 字符安全 ============

    @Test
    fun toJsonFromCache_preservesUnicodeAndSpecialChars() = runTest {
        val odd = contact.copy(
            id = 7L,
            name = "𝕊𝕒𝕞𝕡𝕝𝕖 名字\"with\\escape",
            note = "Line1\nLine2\tTabbed",
            bio = "emoji 🎉🚀 与双引号 \"",
        )
        db.contactCacheDao().insertContact(odd)
        val json = snapshotter.toJsonFromCache(7L)
        val restored = snapshotter.fromJson(json, contactId = 7L)
        assertThat(restored.contact.name).isEqualTo(odd.name)
        assertThat(restored.contact.note).isEqualTo(odd.note)
        assertThat(restored.contact.bio).isEqualTo(odd.bio)
    }

    // ============ 内部容错:tag 已被删,但 crossRef 行还在 ============

    @Test
    fun buildSnapshot_usesFallbackTagNameWhenTagDeleted() = runTest {
        db.contactCacheDao().insertContact(contact)
        // 只插 crossRef,不插 tagCache 行(模拟"tag 已被删"的关联残留)
        db.contactTagCacheDao().insertCrossRef(
            ContactTagCacheEntity(
                contactId = 42L,
                tagId = 999L,
                source = "manual",
                confidence = 1.0f,
                createTime = 1_700_000_000_000L,
            )
        )

        val json = snapshotter.toJsonFromCache(42L)
        assertThat(json).contains("\"tag_id\":999")
        // 改用 round-trip 校验(Gson 默认转义 < 为 <,直接断言子串非常脆弱)
        val parsed = com.google.gson.Gson().fromJson(json, ContactSnapshot::class.java)
        assertThat(parsed.tags).hasSize(1)
        assertThat(parsed.tags[0].tagId).isEqualTo(999L)
        assertThat(parsed.tags[0].name).isEqualTo("<deleted:tagId=999>")
    }
}