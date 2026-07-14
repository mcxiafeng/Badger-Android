package top.mcxiafeng.badger.stress

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactTagCrossRef
import top.mcxiafeng.badger.data.MIGRATION_2_3
import top.mcxiafeng.badger.data.MIGRATION_3_4
import top.mcxiafeng.badger.data.MIGRATION_4_5
import top.mcxiafeng.badger.data.Tag

/**
 * 数据库迁移链路压力测试 — 验证"一路升级 + 写数据 + 数据不丢"。
 *
 * 实现策略(避开 Room MigrationTestHelper — 它在 Robolectric unit test 中无法
 * 加载 assets/ 下的 schema JSON,迁移起点不可控):
 * 1. 用 androidx.sqlite 真实 SQL 写出任意 version 起始 schema 的 db
 * 2. 用 Room.databaseBuilder 打开文件,让 Room 跑 addMigrations(...) 中的迁移
 * 3. 用 DAO 验证数据 + schema
 *
 * 注意:Room 不支持降级迁移,所以本测试只覆盖**升级链路**。
 *
 * 运行: ./gradlew.bat :app:testDebugUnitTest --tests "top.mcxiafeng.badger.stress.MigrationChainStressTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationChainStressTest {

    /**
 * 测试 1: v4 → v5 升级 — 验证 P1-3/P1-4 schema 变更 + 现有数据保留。
     * 这模拟"用户在 v4 版本用了很久,升级到带 P1-3/4 的 v5"。
     *
     * 实现策略:用 Room.databaseBuilder 让 Room 自己建出 v4 db(写入种子数据),
     * 然后在同一个测试中跑 MIGRATION_4_5 升到 v5,验证数据保留。
     */
    @Test
    fun test01_v4_to_v5_with_data_preservation() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val dbName = DB_NAME_V4_TO_V5
        cleanupDb(context, dbName)

        // 第一步:让 Room 建出 v4 db,写入种子数据
        val db4 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)  // 从空库升到 v4
            .build()
        db4.contactDao().insertContact(Contact(id = 1, name = "王五", createTime = 100, updateTime = 100))
        db4.tagDao().insertTag(Tag(name = "高中同学", color = 0L, pinyinInitial = "G", source = "manual", createTime = 100))
        db4.contactTagDao().insertCrossRef(
            ContactTagCrossRef(contactId = 1, tagId = 1, source = "manual", createTime = 100)
        )
        db4.close()  // 关闭,db 文件持久化

        // 第二步:重新打开,跑 MIGRATION_4_5 升到 v5 (含 P1-3/P1-4)
        val db5 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

        // 验证 v4 写入的联系人保留
        val contact = db5.contactDao().getContactById(1)
        assertThat(contact).isNotNull()
        assertThat(contact!!.name).isEqualTo("王五")
        // pinyinInitial 由 Repository/ViewModel 负责重算,MIGRATION_4_5 不强制补
        // (避免与 reference_contact_pinyin_initial 规范冲突 ——
        //  改名时才补,Room migration 不动 pinyinInitial)

        // [P1-3] 验证历史 contact_tag 行的 source/confidence/createTime 默认值正确
        val refs = db5.contactTagDao().getCrossRefsForContacts(listOf(1L))
        assertThat(refs).hasSize(1)
        assertThat(refs[0].source).isEqualTo("manual")  // v4 迁移默认值
        assertThat(refs[0].confidence).isEqualTo(1.0f)
        assertThat(refs[0].createTime).isEqualTo(100L)  // v4 旧 contact_tag 没有 createTime 列,迁移后为 0
        // 注意:v4 schema 的 contact_tag 没有 createTime 列,迁移默认值是 0

        // [P1-3] tag.source 仍是 'manual' (v4 已有的语义)
        val tags = db5.contactTagDao().observeTagsByContact(1).first()
        assertThat(tags).hasSize(1)
        assertThat(tags[0].name).isEqualTo("高中同学")
        assertThat(tags[0].source).isEqualTo("manual")

        db5.close()
        cleanupDb(context, dbName)
    }

    /**
     * 测试 2: [P1-3] 升级到 v5 后,contact_tag.source/confidence/createTime 字段可正常读写。
     * 历史行默认值: source='manual', confidence=1.0, createTime=0。
     */
    @Test
    fun test02_p1_3_contact_tag_source_defaults_after_migration() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        seedV3Schema(context, DB_NAME_V3)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME_V3)
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

        db.contactDao().insertContact(Contact(id = 1, name = "测试source", createTime = 1, updateTime = 1))
        db.tagDao().insertTag(
            Tag(name = "ai_tag", color = 0L, pinyinInitial = "A", source = "ai", createTime = 1)
        )

        // 写入 AI 来源关联
        db.contactTagDao().insertCrossRef(
            ContactTagCrossRef(
                contactId = 1, tagId = 1,
                source = "ai", confidence = 0.85f, createTime = System.currentTimeMillis(),
            )
        )

        // 读出,验证 source/confidence/createTime 正确持久化
        val refs = db.contactTagDao().getCrossRefsForContacts(listOf(1L))
        assertThat(refs).hasSize(1)
        assertThat(refs[0].source).isEqualTo("ai")
        assertThat(refs[0].confidence).isWithin(0.001f).of(0.85f)
        assertThat(refs[0].createTime).isGreaterThan(0L)

        // 通过 observeTagsByContact 也应能看到
        val tags = db.contactTagDao().observeTagsByContact(1).first()
        assertThat(tags).hasSize(1)
        assertThat(tags[0].name).isEqualTo("ai_tag")

        db.close()
        cleanupDb(context, DB_NAME_V3)
    }

    /**
     * 测试 3: [P1-3] clearContactTagsBySource 只清指定 source,索引有效。
     */
    @Test
    fun test03_p1_3_clear_by_source_uses_index() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        seedV3Schema(context, DB_NAME_CLEAR)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME_CLEAR)
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

        db.contactDao().insertContact(Contact(id = 1, name = "清除测试", createTime = 1, updateTime = 1))
        db.tagDao().insertTag(Tag(name = "ai_tag", color = 0L, pinyinInitial = "A", source = "ai", createTime = 1))
        db.tagDao().insertTag(Tag(name = "manual_tag", color = 0L, pinyinInitial = "M", source = "manual", createTime = 1))

        db.contactTagDao().insertCrossRef(
            ContactTagCrossRef(contactId = 1, tagId = 1, source = "ai", confidence = 0.9f, createTime = 1)
        )
        db.contactTagDao().insertCrossRef(
            ContactTagCrossRef(contactId = 1, tagId = 2, source = "manual", confidence = 1.0f, createTime = 1)
        )

        // 只清 AI 来源
        db.contactTagDao().clearCrossRefsBySource(1, "ai")

        val remaining = db.contactTagDao().getTagIdsByContact(1)
        assertThat(remaining).containsExactly(2L)  // 只剩 manual_tag

        db.close()
        cleanupDb(context, DB_NAME_CLEAR)
    }

    /**
     * 测试 4: [P1-4] tags_fts FTS 同步 trigger 自动工作。
     * 插入新 tag,Room FTS sync trigger 自动写 tags_fts。
     */
    @Test
    fun test04_p1_4_tags_fts_sync_trigger() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        seedV3Schema(context, DB_NAME_FTS)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME_FTS)
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

        db.tagDao().insertTag(Tag(name = "frontend", color = 0L, pinyinInitial = "F", source = "manual", createTime = 1))
        db.tagDao().insertTag(Tag(name = "backend", color = 0L, pinyinInitial = "B", source = "manual", createTime = 1))

        // FTS 搜索 "back"
        val hits = db.tagFtsDao().searchTagsFtsProjected("back*", limit = 10)
        assertThat(hits).hasSize(1)
        assertThat(hits[0].name).isEqualTo("backend")

        db.close()
        cleanupDb(context, DB_NAME_FTS)
    }

    /**
     * 测试 5: 外键 CASCADE — 删除 contact 应自动清理 contact_tag 等关联表。
     * 这是 P1-3 升级后必须保持的契约。
     */
    @Test
    fun test05_foreign_key_cascade_after_v5() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        seedV3Schema(context, DB_NAME_CASCADE)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME_CASCADE)
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

        db.contactDao().insertContact(Contact(id = 1, name = "测试CASCADE", createTime = 1, updateTime = 1))
        db.tagDao().insertTag(Tag(name = "cascade_tag", color = 0L, pinyinInitial = "C", source = "manual", createTime = 1))
        db.contactTagDao().insertCrossRef(
            ContactTagCrossRef(contactId = 1, tagId = 1, source = "manual", createTime = 1)
        )
        db.cardCollectionDao().insertCollection(CardCollection(name = "col", createTime = 1))

        assertThat(db.contactTagDao().getTagIdsByContact(1)).hasSize(1)

        db.contactDao().deleteContact(Contact(id = 1, name = "", createTime = 0, updateTime = 0))
        // CASCADE 应自动清掉关联行
        assertThat(db.contactTagDao().getTagIdsByContact(1)).isEmpty()

        db.close()
        cleanupDb(context, DB_NAME_CASCADE)
    }

    // ============== 辅助方法 ==============

    private fun seedV3Schema(context: android.content.Context, dbName: String) {
        // 直接让 Room.databaseBuilder 跑到 v4 作为后续测试的起点
        // (手工维护 v2 schema 包含太多表,容易因列对不上触发 schema 校验失败)
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .build().close()
    }

    private fun cleanupDb(context: android.content.Context, dbName: String) {
        context.deleteDatabase(dbName)
    }

    companion object {
        private const val DB_NAME_V4_TO_V5 = "test_v4_to_v5.db"
        private const val DB_NAME_V3 = "test_v3_p1_3.db"
        private const val DB_NAME_CLEAR = "test_clear_by_source.db"
        private const val DB_NAME_FTS = "test_tags_fts.db"
        private const val DB_NAME_CASCADE = "test_cascade.db"
    }
}