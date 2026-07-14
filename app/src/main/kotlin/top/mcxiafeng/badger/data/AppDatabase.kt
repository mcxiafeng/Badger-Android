package top.mcxiafeng.badger.data

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE card_collections ADD COLUMN backgroundImagePath TEXT")
        db.execSQL("ALTER TABLE card_collections ADD COLUMN dominantColor INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create contact_platforms table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_platforms (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                contactId INTEGER NOT NULL,
                platformKey TEXT NOT NULL,
                value TEXT,
                displayName TEXT,
                jumpLink TEXT NOT NULL DEFAULT '',
                originalLink TEXT,
                avatarUrl TEXT,
                FOREIGN KEY (contactId) REFERENCES contacts(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX index_contact_platforms_contactId ON contact_platforms(contactId)")
        db.execSQL("CREATE INDEX index_contact_platforms_platformKey ON contact_platforms(platformKey)")
        db.execSQL("CREATE UNIQUE INDEX index_contact_platforms_contactId_platformKey ON contact_platforms(contactId, platformKey)")

        // 2. Add pinyinInitial column to contacts
        db.execSQL("ALTER TABLE contacts ADD COLUMN pinyinInitial TEXT NOT NULL DEFAULT ''")

        // 3. Create FTS4 virtual table (drop first to ensure Room schema format matches)
        db.execSQL("DROP TABLE IF EXISTS contacts_fts")
        db.execSQL("CREATE VIRTUAL TABLE contacts_fts USING fts4(name, note, content=`contacts`)")

        // 4. Populate FTS index
        db.execSQL("INSERT INTO contacts_fts(rowid, name, note) SELECT id, name, note FROM contacts")

        // 5. Drop legacy FTS sync triggers (Room auto-generates its own via @Fts4 contentEntity).
        //    Manually creating them here conflicts with Room's triggers and causes SQLITE_ERROR on DELETE.
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ad")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_au")
        Log.d("DatabaseModule", "MIGRATION_2_3: dropped legacy FTS sync triggers")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. tags：标签定义。name 唯一索引，保证同标签自动复用。
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL DEFAULT -14847833,
                pinyinInitial TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'manual',
                createTime INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags(name)")

        // 2. contact_tag：联系人 ↔ 标签 多对多关联。两端 CASCADE。
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_tag (
                contactId INTEGER NOT NULL,
                tagId INTEGER NOT NULL,
                PRIMARY KEY(contactId, tagId),
                FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE,
                FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX index_contact_tag_tagId ON contact_tag(tagId)")

        // 3. 触发 PagingSource/Flow 失效：UPDATE 不改值但让 Room InvalidationTracker 推下游
        //    （与 ContactDao.bumpContact 同模式，详见 ContactRepositoryImpl.insertOne 注释）。
        db.execSQL("UPDATE contacts SET updateTime = updateTime WHERE id > 0")

        Log.d("DatabaseModule", "MIGRATION_3_4: created tags/contact_tag, bumped contacts")
    }
}

/**
 * v5 schema 迁移:
 * 1. contacts 添加 bio 列（个人介绍）
 * 2. tags 添加 showDot 列（列表项色点开关）
 * 3. scan_results 删除 styleColor 列（"样式"被 Tag 取代）
 *    SQLite 不支持 DROP COLUMN,需 create new table + copy + drop + rename
 * 4. 把老 scan_results.styleColor 自动转 Tag(source='legacy'),尽量保留用户视觉
 *    pinyinInitial 留空,由 App 启动时 LegacyTagFixup 一次性补
 * 5. ContactFts contentEntity 增加 bio 列,需重建 FTS4 表与索引
 *    [修复防御]: DROP Room 自动生成的 4 个 FTS 触发器,否则 IF NOT EXISTS 会让
 *    bio 列永远写不进 FTS 索引（v4 触发器 body 不含 bio）。
 * 6. 末尾 UPDATE contacts SET updateTime = updateTime 触发 InvalidationTracker
 *
 * [修复防御-步骤顺序]: 第 2 步 INSERT INTO tags (... showDot ...) 必须放在
 * "ALTER TABLE tags ADD COLUMN showDot" 之后。原版 0 步先 INSERT 后 ALTER,
 * v4→v5 升级会因列不存在而崩溃。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // 1. 先把列建好（v4 schema 不存在 bio 和 showDot）
        db.execSQL("ALTER TABLE contacts ADD COLUMN bio TEXT")
        db.execSQL("ALTER TABLE tags ADD COLUMN showDot INTEGER NOT NULL DEFAULT 1")
        Log.d("DatabaseModule", "MIGRATION_4_5: added bio/showDot columns")

        // 2. 收集遗留样式 → 转 Tag + 关联 contact_tag
        //    pinyinInitial 留空,由 LegacyTagFixup 启动时一次性补齐
        db.execSQL(
            "INSERT OR IGNORE INTO tags (name, color, pinyinInitial, source, showDot, createTime) " +
            "SELECT DISTINCT " +
            "  '遗留样式_' || printf('%06X', sr.styleColor) || '_' || c.name AS name, " +
            "  sr.styleColor AS color, " +
            "  '' AS pinyinInitial, " +
            "  'legacy' AS source, " +
            "  1 AS showDot, " +
            "  $now AS createTime " +
            "FROM scan_results sr " +
            "INNER JOIN contacts c ON c.id = sr.contactId " +
            "WHERE sr.styleColor IS NOT NULL"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO contact_tag (contactId, tagId) " +
            "SELECT DISTINCT sr.contactId, t.id " +
            "FROM scan_results sr " +
            "INNER JOIN tags t ON t.name = '遗留样式_' || printf('%06X', sr.styleColor) || '_' || c.name " +
            "INNER JOIN contacts c ON c.id = sr.contactId " +
            "WHERE sr.styleColor IS NOT NULL"
        )
        Log.d("DatabaseModule", "MIGRATION_4_5: migrated legacy styleColor to tags/contact_tag")

        // 3. scan_results 重建表(去掉 styleColor)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS scan_results_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                contactId INTEGER NOT NULL,
                collectionId INTEGER NOT NULL,
                scannedTime INTEGER NOT NULL,
                sourceType TEXT NOT NULL,
                rawData TEXT,
                ocrText TEXT,
                qrCodeContent TEXT,
                confidence REAL NOT NULL,
                FOREIGN KEY(contactId) REFERENCES contacts(id) ON DELETE CASCADE,
                FOREIGN KEY(collectionId) REFERENCES card_collections(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("""
            INSERT INTO scan_results_new (id, contactId, collectionId, scannedTime, sourceType, rawData, ocrText, qrCodeContent, confidence)
            SELECT id, contactId, collectionId, scannedTime, sourceType, rawData, ocrText, qrCodeContent, confidence FROM scan_results
        """)
        db.execSQL("DROP TABLE scan_results")
        db.execSQL("ALTER TABLE scan_results_new RENAME TO scan_results")
        db.execSQL("CREATE INDEX index_scan_results_contactId_collectionId ON scan_results(contactId, collectionId)")
        db.execSQL("CREATE INDEX index_scan_results_collectionId ON scan_results(collectionId)")
        db.execSQL("CREATE INDEX index_scan_results_contactId ON scan_results(contactId)")
        // [修复防御-序列恢复]: DROP TABLE 会顺带删 sqlite_sequence 里的 seq 记录,
        // 必须基于已迁移的最大 id 重建,否则下次 INSERT 会撞 UNIQUE 约束 (id=1 已被历史占用)
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'scan_results'")
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES ('scan_results', (SELECT MAX(id) FROM scan_results))")
        Log.d("DatabaseModule", "MIGRATION_4_5: rebuilt scan_results without styleColor, restored sqlite_sequence")

        // 4. ContactFts 重建(因 contentEntity 增加 bio 列)
        db.execSQL("DROP TABLE IF EXISTS contacts_fts")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ad")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_au")
        // [修复防御-FTS 触发器]: Room 在每次 onOpen 用 CREATE TRIGGER IF NOT EXISTS
        // 重建触发器,如果旧触发器存在就跳过——v4 的触发器 body 只同步 name+note,
        // bio 列永远不进 FTS 索引。这里显式 DROP 4 个 Room 自动生成的触发器,
        // 让 Room 在 onOpen 重新建出含 bio 的新版本
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_AFTER_INSERT")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS contacts_fts USING fts4(name, note, bio, content=`contacts`)")
        db.execSQL("INSERT INTO contacts_fts(rowid, name, note, bio) SELECT id, name, note, bio FROM contacts")
        Log.d("DatabaseModule", "MIGRATION_4_5: rebuilt FTS with bio column, dropped legacy FTS triggers")

        // 5. 触发 PagingSource/Flow 重发
        db.execSQL("UPDATE contacts SET updateTime = updateTime WHERE id > 0")

        // 6. [P1-3] contact_tag 新增 source / confidence / createTime
        //    历史行的 source 默认 'manual' / confidence 默认 1.0 / createTime 默认 0
        db.execSQL("ALTER TABLE contact_tag ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
        db.execSQL("ALTER TABLE contact_tag ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE contact_tag ADD COLUMN createTime INTEGER NOT NULL DEFAULT 0")
        // [P1-3] 复合索引 (contactId, source),用于"清空某联系人的 AI 标签"等查询
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_tag_contactId_source ON contact_tag(contactId, source)")
        Log.d("DatabaseModule", "MIGRATION_4_5: contact_tag +source/confidence/createTime + (contactId,source) index")

        // 7. [P1-4] tags_fts 重建（与同文件 contacts_fts 的同模式）
        //    显式 DROP Room 自动触发器,避免 IF NOT EXISTS 跳过老触发器导致新列错位
        db.execSQL("DROP TABLE IF EXISTS tags_fts")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_AFTER_INSERT")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS tags_fts USING fts4(name, pinyinInitial, content=`tags`)")
        db.execSQL("INSERT INTO tags_fts(rowid, name, pinyinInitial) SELECT id, name, pinyinInitial FROM tags")
        Log.d("DatabaseModule", "MIGRATION_4_5: tags_fts created")

        // 8. 触发 PagingSource/Flow 重发（与步骤 5 同模式）
        db.execSQL("UPDATE contacts SET updateTime = updateTime WHERE id > 0")

        Log.d("DatabaseModule", "MIGRATION_4_5: done — bio/showDot added, styleColor removed, legacy tags migrated, FTS rebuilt with bio, sqlite_sequence restored, contact_tag +source/confidence/createTime, tags_fts created")
    }
}

@Database(
    entities = [
        Contact::class,
        ContactField::class,
        CustomField::class,
        ContactFieldValue::class,
        CardCollection::class,
        ScanResult::class,
        UserProfile::class,
        ContactPlatform::class,
        ContactFts::class,
        Tag::class,
        ContactTagCrossRef::class,
        TagFts::class,
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun contactFieldDao(): ContactFieldDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun contactFieldValueDao(): ContactFieldValueDao
    abstract fun cardCollectionDao(): CardCollectionDao
    abstract fun scanResultDao(): ScanResultDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun contactPlatformDao(): ContactPlatformDao
    abstract fun contactFtsDao(): ContactFtsDao
    abstract fun tagDao(): TagDao
    abstract fun contactTagDao(): ContactTagDao
    abstract fun tagFtsDao(): TagFtsDao
}
