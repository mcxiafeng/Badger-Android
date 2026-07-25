package top.mcxiafeng.badger.data

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
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

/**
 * v5 → v6 schema 迁移（V2 客户端底层重写）：
 *
 * 1. 新建 8 张 `*_cache` 表，把 v5 老表数据**完整搬运**：
 *    contacts → contacts_cache / contact_fields → contact_fields_cache /
 *    contact_field_values → contact_field_values_cache / contact_platforms → contact_platforms_cache /
 *    tags → tags_cache / contact_tag → contact_tag_cache / card_collections → card_collections_cache /
 *    user_profile → user_profile_cache
 *
 * 2. 新建 2 张空表：pending_uploads / operation_history
 *
 * 3. **不动** scan_results / contacts_fts / tags_fts（FTS 触发器 + 软降级为扫码历史）
 *
 * 老数据**全部标记** `isLocalOnly=1`（V2 §3.4 关键设计）：
 * 我们不知道服务端 version，用户首次启动 → App 检测 isLocalOnly → 主动走
 * /v1/contacts?ids=... 拉服务端权威版本替换（P11 阶段处理）。
 *
 * [修复防御-序列恢复]: contact_tag_cache 没自增 id，复用 contact_tag 的 (contactId, tagId) 复合主键
 * 不存在 seq 恢复问题；其他 cache 表都保留 INSERT...SELECT 的 id，SQLite 自动同步 sqlite_sequence。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: contacts_cache
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contacts_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                serverId TEXT,
                name TEXT NOT NULL,
                avatarUrl TEXT,
                avatarPath TEXT,
                note TEXT,
                bio TEXT,
                pinyinInitial TEXT NOT NULL DEFAULT '',
                platformsJson TEXT NOT NULL DEFAULT '{}',
                createTime INTEGER NOT NULL,
                updateTime INTEGER NOT NULL,
                serverVersion INTEGER NOT NULL DEFAULT 0,
                lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                isLocalOnly INTEGER NOT NULL DEFAULT 1,
                isDeleted INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL(
            "INSERT INTO contacts_cache " +
            "(id, name, avatarUrl, avatarPath, note, bio, pinyinInitial, " +
            "platformsJson, createTime, updateTime, serverVersion, lastSyncedAt, " +
            "isLocalOnly, isDeleted) " +
            "SELECT id, name, avatarUrl, avatarPath, note, bio, pinyinInitial, " +
            "'{}', createTime, updateTime, 0, 0, 1, 0 " +
            "FROM contacts"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_cache_isDeleted ON contacts_cache(isDeleted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_cache_isLocalOnly ON contacts_cache(isLocalOnly)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_cache_serverId ON contacts_cache(serverId)")

        // Step 2: contact_fields_cache
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_fields_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fieldName TEXT NOT NULL,
                fieldKey TEXT NOT NULL,
                icon TEXT,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                isSystem INTEGER NOT NULL DEFAULT 0,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                createTime INTEGER NOT NULL
            )
        """)
        db.execSQL(
            "INSERT INTO contact_fields_cache " +
            "(id, fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) " +
            "SELECT id, fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime " +
            "FROM contact_fields"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contact_fields_cache_fieldKey ON contact_fields_cache(fieldKey)")

        // Step 3: contact_field_values_cache (保留 fieldId/customFieldId 两套)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_field_values_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                contactId INTEGER NOT NULL,
                fieldId INTEGER,
                customFieldId INTEGER,
                value TEXT NOT NULL,
                displayOrder INTEGER NOT NULL DEFAULT 0,
                createTime INTEGER NOT NULL,
                updateTime INTEGER NOT NULL,
                serverVersion INTEGER NOT NULL DEFAULT 0,
                isLocalOnly INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL(
            "INSERT INTO contact_field_values_cache " +
            "(id, contactId, fieldId, customFieldId, value, displayOrder, createTime, " +
            "updateTime, serverVersion, isLocalOnly) " +
            "SELECT id, contactId, fieldId, customFieldId, value, 0, createTime, " +
            "updateTime, 0, 1 " +
            "FROM contact_field_values"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_field_values_cache_contactId ON contact_field_values_cache(contactId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_field_values_cache_contactId_fieldId ON contact_field_values_cache(contactId, fieldId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_field_values_cache_contactId_customFieldId ON contact_field_values_cache(contactId, customFieldId)")

        // Step 4: contact_platforms_cache
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_platforms_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                contactId INTEGER NOT NULL,
                platformKey TEXT NOT NULL,
                value TEXT,
                displayName TEXT,
                jumpLink TEXT NOT NULL DEFAULT '',
                originalLink TEXT,
                avatarUrl TEXT,
                serverVersion INTEGER NOT NULL DEFAULT 0,
                isLocalOnly INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL(
            "INSERT INTO contact_platforms_cache " +
            "(id, contactId, platformKey, value, displayName, jumpLink, originalLink, avatarUrl, " +
            "serverVersion, isLocalOnly) " +
            "SELECT id, contactId, platformKey, value, displayName, jumpLink, originalLink, avatarUrl, " +
            "0, 1 " +
            "FROM contact_platforms"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_platforms_cache_contactId ON contact_platforms_cache(contactId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_platforms_cache_platformKey ON contact_platforms_cache(platformKey)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contact_platforms_cache_contactId_platformKey ON contact_platforms_cache(contactId, platformKey)")

        // Step 5: tags_cache (保留 showDot/source)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tags_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL DEFAULT -14847833,
                pinyinInitial TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'manual',
                showDot INTEGER NOT NULL DEFAULT 1,
                createTime INTEGER NOT NULL,
                serverVersion INTEGER NOT NULL DEFAULT 0,
                isLocalOnly INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL(
            "INSERT INTO tags_cache " +
            "(id, name, color, pinyinInitial, source, showDot, createTime, " +
            "serverVersion, isLocalOnly) " +
            "SELECT id, name, color, pinyinInitial, source, showDot, createTime, " +
            "0, 1 " +
            "FROM tags"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_cache_name ON tags_cache(name)")

        // Step 6: contact_tag_cache (Q1 决策:新增独立多对多表)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_tag_cache (
                contactId INTEGER NOT NULL,
                tagId INTEGER NOT NULL,
                source TEXT NOT NULL DEFAULT 'manual',
                confidence REAL NOT NULL DEFAULT 1.0,
                createTime INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(contactId, tagId)
            )
        """)
        db.execSQL(
            "INSERT INTO contact_tag_cache (contactId, tagId, source, confidence, createTime) " +
            "SELECT contactId, tagId, source, confidence, createTime FROM contact_tag"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_tag_cache_tagId ON contact_tag_cache(tagId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_tag_cache_contactId_source ON contact_tag_cache(contactId, source)")

        // Step 7: card_collections_cache (Q3 决策:保留 backgroundImagePath/dominantColor)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS card_collections_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                backgroundImagePath TEXT,
                dominantColor INTEGER,
                coverAvatarUrl TEXT,
                createTime INTEGER NOT NULL,
                serverVersion INTEGER NOT NULL DEFAULT 0,
                isLocalOnly INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL(
            "INSERT INTO card_collections_cache " +
            "(id, name, description, backgroundImagePath, dominantColor, coverAvatarUrl, " +
            "createTime, serverVersion, isLocalOnly) " +
            "SELECT id, name, description, backgroundImagePath, dominantColor, NULL, " +
            "createTime, 0, 1 " +
            "FROM card_collections"
        )

        // Step 8: user_profile_cache (Q2 决策:保留 avatarPath/defaultPlatform, 丢 cardImagePath)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_profile_cache (
                id INTEGER PRIMARY KEY NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                avatarPath TEXT,
                bio TEXT,
                platformsJson TEXT NOT NULL DEFAULT '{}',
                defaultPlatform TEXT,
                updateTime INTEGER NOT NULL,
                serverVersion INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL(
            "INSERT INTO user_profile_cache " +
            "(id, name, avatarPath, bio, platformsJson, defaultPlatform, updateTime, serverVersion) " +
            "SELECT id, name, avatarPath, bio, '{}', defaultPlatform, updateTime, 0 " +
            "FROM user_profile"
        )

        // Step 9: 新建 pending_uploads / operation_history (空表)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_uploads (
                opId TEXT NOT NULL,
                contactId INTEGER NOT NULL,
                opType TEXT NOT NULL,
                resourceVersion INTEGER NOT NULL,
                payloadJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                maxAttempts INTEGER NOT NULL DEFAULT 8,
                lastError TEXT,
                nextAttemptAt INTEGER NOT NULL,
                lastAttemptAt INTEGER,
                deviceId TEXT NOT NULL,
                PRIMARY KEY(opId)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_uploads_status ON pending_uploads(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_uploads_contactId ON pending_uploads(contactId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_uploads_nextAttemptAt ON pending_uploads(nextAttemptAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS operation_history (
                opId TEXT NOT NULL,
                contactId INTEGER NOT NULL,
                opType TEXT NOT NULL,
                opLabel TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                snapshotBeforeJson TEXT NOT NULL,
                snapshotAfterJson TEXT,
                createdAt INTEGER NOT NULL,
                opStatus TEXT NOT NULL,
                serverVersion INTEGER,
                lastError TEXT,
                attempts INTEGER NOT NULL DEFAULT 0,
                inversePayloadJson TEXT,
                canUndo INTEGER NOT NULL,
                canReplay INTEGER NOT NULL,
                PRIMARY KEY(opId)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_operation_history_createdAt ON operation_history(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_operation_history_opStatus ON operation_history(opStatus)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_operation_history_contactId ON operation_history(contactId)")

        // Step 10: 触发 PagingSource/Flow 重发
        db.execSQL("UPDATE contacts SET updateTime = updateTime WHERE id > 0")

        Log.d("DatabaseModule", "MIGRATION_5_6: 8 cache tables populated (isLocalOnly=1), " +
              "pending_uploads + operation_history empty, FTS untouched")
    }
}

@Database(
    entities = [
        // V1 保留 entity:系统字段 / 自定义字段 / 字段值 / 扫码历史 / 平台兼容垫
        ContactField::class,
        CustomField::class,
        ContactFieldValue::class,
        ScanResult::class,
        ContactPlatform::class,
        // V2 cache 表(主路径)
        ContactCacheEntity::class,
        ContactFieldCacheEntity::class,
        ContactFieldValueCacheEntity::class,
        ContactPlatformCacheEntity::class,
        TagCacheEntity::class,
        CardCollectionCacheEntity::class,
        UserProfileCacheEntity::class,
        ContactTagCacheEntity::class,
        // V2 queue 表
        PendingUploadEntity::class,
        OperationHistoryEntity::class,
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // V1 保留 DAO
    abstract fun contactFieldDao(): ContactFieldDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun contactFieldValueDao(): ContactFieldValueDao
    abstract fun scanResultDao(): ScanResultDao
    abstract fun contactPlatformDao(): ContactPlatformDao

    // [A3] 8 个 V2 cache DAO(主路径)
    abstract fun contactCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
    abstract fun contactFieldCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
    abstract fun contactFieldValueCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
    abstract fun contactPlatformCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
    abstract fun tagCacheDao(): top.mcxiafeng.badger.data.cache.dao.TagCacheDao
    abstract fun cardCollectionCacheDao(): top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
    abstract fun userProfileCacheDao(): top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
    abstract fun contactTagCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
}
