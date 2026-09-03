package top.mcxiafeng.badger.data

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.CollectionMemberCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.CustomFieldCacheEntity
import top.mcxiafeng.badger.data.cache.entity.PersonProfileCacheEntity
import top.mcxiafeng.badger.data.cache.entity.SyncCursorEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OutboxDao
import top.mcxiafeng.badger.data.queue.OutboxEntity
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

        // [修复防御]: v5 → v6 给 V1 保留表 contact_fields 补唯一索引 index_contact_fields_fieldKey。
        // v6 @Entity(indices = [Index(["fieldKey"], unique = true)]) 已声明,迁移链若不补建,
        // Room 启动时 validateMigration 会抛 "Migration didn't properly handle: contact_fields"。
        // 老 v5 实例无该索引,需补建;已存在的索引 CREATE UNIQUE INDEX IF NOT EXISTS 幂等。
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contact_fields_fieldKey ON contact_fields(fieldKey)")

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

/**
 * v6 → v7 schema 迁移（API 迁移 Phase 3：乐观锁退役 + uuid 化 + sync 游标）。
 *
 * 依据 `docs/api-handover-migration-plan.md` §C2/C3：
 * - **删 `serverVersion` 列**：新 Java `/api` 契约无版本号（乐观锁 + If-Match 退役）；
 * - **`serverId` 语义变更为服务端 Person/Collection/Tag 的 uuid**（列保留同名）；
 * - **新增 `colorHash` / `personMembers`**（服务端 Tag/Collection 字段）；
 * - **新增 `sync_cursor` 表**：多端增量同步游标。
 *
 * 受影响的 6 张表全部采用「create new → copy → drop → rename → 重建索引 → 恢复
 * sqlite_sequence」保守迁移（SQLite 旧版不支持 ALTER TABLE DROP COLUMN；minSdk=26）。
 * **禁止 fallbackToDestructiveMigration**——迁移缺失时宁可抛异常也不抹用户数据。
 *
 * [修复防御-表重建]:contacts_cache 无 FTS 关联（FTS 在 V1 `contacts` 表），DROP+RENAME
 * 不会伤及 FTS 触发器；tags_cache 无 FTS 关联；card_collections_cache / contact_platforms_cache /
 * contact_field_values_cache / user_profile_cache 均无外键被引用。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ============ 1. contacts_cache：删 serverVersion ============
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contacts_cache_new (
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
                lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                isLocalOnly INTEGER NOT NULL DEFAULT 1,
                isDeleted INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL(
            "INSERT INTO contacts_cache_new " +
            "(id, serverId, name, avatarUrl, avatarPath, note, bio, pinyinInitial, platformsJson, " +
            "createTime, updateTime, lastSyncedAt, isLocalOnly, isDeleted) " +
            "SELECT id, serverId, name, avatarUrl, avatarPath, note, bio, pinyinInitial, platformsJson, " +
            "createTime, updateTime, lastSyncedAt, isLocalOnly, isDeleted FROM contacts_cache"
        )
        db.execSQL("DROP TABLE contacts_cache")
        db.execSQL("ALTER TABLE contacts_cache_new RENAME TO contacts_cache")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_cache_isDeleted ON contacts_cache(isDeleted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_cache_isLocalOnly ON contacts_cache(isLocalOnly)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_cache_serverId ON contacts_cache(serverId)")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'contacts_cache'")
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES ('contacts_cache', (SELECT MAX(id) FROM contacts_cache))")

        // ============ 2. contact_platforms_cache：删 serverVersion ============
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_platforms_cache_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                contactId INTEGER NOT NULL,
                platformKey TEXT NOT NULL,
                value TEXT,
                displayName TEXT,
                jumpLink TEXT NOT NULL DEFAULT '',
                originalLink TEXT,
                avatarUrl TEXT,
                isLocalOnly INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL(
            "INSERT INTO contact_platforms_cache_new " +
            "(id, contactId, platformKey, value, displayName, jumpLink, originalLink, avatarUrl, isLocalOnly) " +
            "SELECT id, contactId, platformKey, value, displayName, jumpLink, originalLink, avatarUrl, isLocalOnly " +
            "FROM contact_platforms_cache"
        )
        db.execSQL("DROP TABLE contact_platforms_cache")
        db.execSQL("ALTER TABLE contact_platforms_cache_new RENAME TO contact_platforms_cache")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_platforms_cache_contactId ON contact_platforms_cache(contactId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_platforms_cache_platformKey ON contact_platforms_cache(platformKey)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contact_platforms_cache_contactId_platformKey ON contact_platforms_cache(contactId, platformKey)")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'contact_platforms_cache'")
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES ('contact_platforms_cache', (SELECT MAX(id) FROM contact_platforms_cache))")

        // ============ 3. contact_field_values_cache：删 serverVersion ============
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_field_values_cache_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                contactId INTEGER NOT NULL,
                fieldId INTEGER,
                customFieldId INTEGER,
                value TEXT NOT NULL,
                displayOrder INTEGER NOT NULL DEFAULT 0,
                createTime INTEGER NOT NULL,
                updateTime INTEGER NOT NULL,
                isLocalOnly INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL(
            "INSERT INTO contact_field_values_cache_new " +
            "(id, contactId, fieldId, customFieldId, value, displayOrder, createTime, updateTime, isLocalOnly) " +
            "SELECT id, contactId, fieldId, customFieldId, value, displayOrder, createTime, updateTime, isLocalOnly " +
            "FROM contact_field_values_cache"
        )
        db.execSQL("DROP TABLE contact_field_values_cache")
        db.execSQL("ALTER TABLE contact_field_values_cache_new RENAME TO contact_field_values_cache")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_field_values_cache_contactId ON contact_field_values_cache(contactId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_field_values_cache_contactId_fieldId ON contact_field_values_cache(contactId, fieldId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_field_values_cache_contactId_customFieldId ON contact_field_values_cache(contactId, customFieldId)")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'contact_field_values_cache'")
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES ('contact_field_values_cache', (SELECT MAX(id) FROM contact_field_values_cache))")

        // ============ 4. tags_cache：删 serverVersion，增 serverId/colorHash/personMembers ============
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tags_cache_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                serverId TEXT,
                name TEXT NOT NULL,
                color INTEGER NOT NULL DEFAULT -14847833,
                colorHash TEXT,
                personMembers TEXT NOT NULL DEFAULT '[]',
                pinyinInitial TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'manual',
                showDot INTEGER NOT NULL DEFAULT 1,
                createTime INTEGER NOT NULL,
                isLocalOnly INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL(
            "INSERT INTO tags_cache_new " +
            "(id, serverId, name, color, colorHash, personMembers, pinyinInitial, source, showDot, createTime, isLocalOnly) " +
            "SELECT id, NULL, name, color, NULL, '[]', pinyinInitial, source, showDot, createTime, isLocalOnly " +
            "FROM tags_cache"
        )
        db.execSQL("DROP TABLE tags_cache")
        db.execSQL("ALTER TABLE tags_cache_new RENAME TO tags_cache")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_cache_name ON tags_cache(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tags_cache_serverId ON tags_cache(serverId)")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'tags_cache'")
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES ('tags_cache', (SELECT MAX(id) FROM tags_cache))")

        // ============ 5. card_collections_cache：删 serverVersion，增 serverId/personMembers ============
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS card_collections_cache_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                serverId TEXT,
                name TEXT NOT NULL,
                description TEXT,
                backgroundImagePath TEXT,
                dominantColor INTEGER,
                coverAvatarUrl TEXT,
                personMembers TEXT NOT NULL DEFAULT '[]',
                createTime INTEGER NOT NULL,
                isLocalOnly INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL(
            "INSERT INTO card_collections_cache_new " +
            "(id, serverId, name, description, backgroundImagePath, dominantColor, coverAvatarUrl, personMembers, createTime, isLocalOnly) " +
            "SELECT id, NULL, name, description, backgroundImagePath, dominantColor, coverAvatarUrl, '[]', createTime, isLocalOnly " +
            "FROM card_collections_cache"
        )
        db.execSQL("DROP TABLE card_collections_cache")
        db.execSQL("ALTER TABLE card_collections_cache_new RENAME TO card_collections_cache")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'card_collections_cache'")
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES ('card_collections_cache', (SELECT MAX(id) FROM card_collections_cache))")

        // ============ 6. user_profile_cache：删 serverVersion（无 AUTOINCREMENT，无需 seq 恢复） ============
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_profile_cache_new (
                id INTEGER PRIMARY KEY NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                avatarPath TEXT,
                bio TEXT,
                platformsJson TEXT NOT NULL DEFAULT '{}',
                defaultPlatform TEXT,
                updateTime INTEGER NOT NULL
            )
        """)
        db.execSQL(
            "INSERT INTO user_profile_cache_new " +
            "(id, name, avatarPath, bio, platformsJson, defaultPlatform, updateTime) " +
            "SELECT id, name, avatarPath, bio, platformsJson, defaultPlatform, updateTime FROM user_profile_cache"
        )
        db.execSQL("DROP TABLE user_profile_cache")
        db.execSQL("ALTER TABLE user_profile_cache_new RENAME TO user_profile_cache")

        // ============ 7. sync_cursor 新表（单例行，Phase 3 sync 游标） ============
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_cursor (
                id INTEGER PRIMARY KEY NOT NULL,
                lastVersion INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("INSERT OR REPLACE INTO sync_cursor (id, lastVersion, updatedAt) VALUES (1, 0, 0)")

        Log.d("DatabaseModule", "MIGRATION_6_7: dropped serverVersion, uuid 语义化, " +
              "tags/collections +serverId/colorHash/personMembers, sync_cursor created")
    }
}

/**
 * v7 → v8 schema 迁移（Phase 2：Profile 字段完备化）。
 *
 * 给 `user_profile_cache` 加 6 列：sex / country / region / birthday / backgroundURL / extra，
 * 全部 nullable TEXT。旧数据升级后新列为 null，不影响既有读写。
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 2 Task 2.1
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN sex TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN country TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN region TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN birthday TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN backgroundURL TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN extra TEXT")
        Log.d("DatabaseModule", "MIGRATION_7_8: user_profile_cache +sex/country/region/birthday/backgroundURL/extra")
    }
}

/**
 * v8 → v9 schema 迁移（Phase 2：person_profile_cache 子表 + contacts_cache self 列）。
 *
 * 1. 新建 `person_profile_cache` 子表（主键 `contactServerId` = `contacts_cache.serverId`），
 *    存储 `ProfileDto` 中原先未持久化的 sex / country / region / birthday / backgroundURL / extra。
 * 2. 给 `contacts_cache` 加 `self INTEGER`（nullable Boolean），持久化 `PersonDto.self`。
 * 3. `contacts_cache.serverId` 索引升级为 UNIQUE（外键引用要求）。
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 2 Task 2.3 + Task 2.4
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. person_profile_cache 子表（PK = contactServerId，保证 1:1）
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS person_profile_cache (
                contactServerId TEXT NOT NULL PRIMARY KEY,
                sex TEXT,
                country TEXT,
                region TEXT,
                birthday TEXT,
                backgroundURL TEXT,
                extra TEXT,
                FOREIGN KEY (contactServerId) REFERENCES contacts_cache(serverId) ON DELETE CASCADE
            )
        """)

        // 2. contacts_cache 加 self 列
        db.execSQL("ALTER TABLE contacts_cache ADD COLUMN self INTEGER")

        // 3. contacts_cache.serverId 索引升级为 UNIQUE（外键引用要求）
        db.execSQL("DROP INDEX IF EXISTS index_contacts_cache_serverId")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contacts_cache_serverId ON contacts_cache(serverId)")

        Log.d("DatabaseModule", "MIGRATION_8_9: person_profile_cache created (PK=contactServerId), contacts_cache +self, serverId unique index")
    }
}

/**
 * v9 → v10 schema 迁移（Phase 3 Task #30：expand — 新建 custom_fields_cache）。
 *
 * 新建 `custom_fields_cache` V2 cache 表，为删除 V1 `custom_fields` 表做准备。
 * 字段与 V1 `custom_fields` 一一对应，无 V2 新增列。
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 3 Task #30
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS custom_fields_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fieldName TEXT NOT NULL,
                fieldType TEXT NOT NULL,
                options TEXT NOT NULL,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                createTime INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_fields_cache_sortOrder ON custom_fields_cache(sortOrder)")

        Log.d("DatabaseModule", "MIGRATION_9_10: created custom_fields_cache table")
    }
}

/**
 * v10 → v11 schema 迁移（Phase 3 Task #17：contract — 删除 V1 字段表）。
 *
 * 删除 V1 字段表：
 * - `contact_field_values`（字段值关联表）
 * - `custom_fields`（自定义字段定义表）
 * - `contact_fields`（系统字段定义表）
 *
 * 前置条件：
 * - Task #15 双写已上线（FieldRepositoryImpl 同时写 V1 + V2 cache）
 * - Task #16 切读已上线（FieldMigrationConfig.useV2Reads = true）
 * - Task #28 观察期已通过（至少 1 个 commit 周期，无回归）
 * - Task #30 custom_fields_cache 已创建
 *
 * 数据安全：
 * - V2 cache 表（`contact_fields_cache` / `contact_field_values_cache` / `custom_fields_cache`）已包含所有数据
 * - 删除 V1 表前，确保 V2 cache 表数据完整
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 3 Task #17
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 删除 V1 字段值表（依赖 contact_fields 和 custom_fields 的外键）
        db.execSQL("DROP TABLE IF EXISTS contact_field_values")
        // 删除 V1 自定义字段定义表
        db.execSQL("DROP TABLE IF EXISTS custom_fields")
        // 删除 V1 系统字段定义表
        db.execSQL("DROP TABLE IF EXISTS contact_fields")

        Log.d("DatabaseModule", "MIGRATION_10_11: dropped V1 field tables (contact_field_values, custom_fields, contact_fields)")
    }
}

/**
 * v11 → v12 schema 迁移（Phase 4 Task #19：contract — 删除 V1 平台表）。
 *
 * 删除 V1 平台表：
 * - `contact_platforms`（V1 平台兼容垫表）
 *
 * 前置条件：
 * - V2 `contact_platforms_cache` 表已包含所有数据
 * - V1 `ContactPlatformDao` 已退役（Phase 4 Task #19）
 *
 * 数据安全：
 * - V2 `contact_platforms_cache` 表已包含所有数据
 * - 删除 V1 表前，确保 V2 表数据完整
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 4 Task #19
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 删除 V1 平台表
        db.execSQL("DROP TABLE IF EXISTS contact_platforms")

        Log.d("DatabaseModule", "MIGRATION_11_12: dropped V1 platform table (contact_platforms)")
    }
}

/**
 * v12 → v13 schema 迁移（Phase 4 Task #20：contract — 删除 V1 扫码历史表）。
 *
 * 1. 新建 V2 `collection_member_cache` 表（联系人 ↔ 名片夹关联）。
 * 2. 从 `scan_results` 迁移关联数据到 `collection_member_cache`（去重）。
 * 3. 删除 V1 `scan_results` 表。
 *
 * 前置条件：
 * - `CollectionRepositoryImpl` 已迁移至使用 `CollectionMemberCacheDao`
 * - `ContactCacheDao.getContactsByCollection` 已迁移至使用 `collection_member_cache`
 * - `CardCollectionCacheDao.getCollectionsWithCount` 已迁移至使用 `collection_member_cache`
 *
 * 数据安全：
 * - `scan_results` 中的扫码元数据（rawData/ocrText/qrCodeContent/confidence）不再保留，
 *   服务端已通过 `/api` 接管扫码历史管理。
 * - 仅保留联系人 ↔ 名片夹关联关系。
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 4 Task #20
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. 新建 collection_member_cache 表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS collection_member_cache (
                contactId INTEGER NOT NULL,
                collectionId INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(contactId, collectionId),
                FOREIGN KEY (contactId) REFERENCES contacts_cache(id) ON DELETE CASCADE,
                FOREIGN KEY (collectionId) REFERENCES card_collections_cache(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_member_cache_contactId ON collection_member_cache(contactId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_member_cache_collectionId ON collection_member_cache(collectionId)")

        // 2. 迁移关联数据（去重：同一 contactId+collectionId 只保留最早的记录）
        db.execSQL("""
            INSERT OR IGNORE INTO collection_member_cache (contactId, collectionId, addedAt)
            SELECT contactId, collectionId, MIN(scannedTime) FROM scan_results
            GROUP BY contactId, collectionId
        """)

        // 3. 删除 V1 扫码历史表
        db.execSQL("DROP TABLE IF EXISTS scan_results")

        Log.d("DatabaseModule", "MIGRATION_12_13: created collection_member_cache, migrated data from scan_results, dropped scan_results")
    }
}

/**
 * v13 → v14 schema 迁移（Phase 4 Task #21：contract — 删除 V1 队列表）。
 *
 * 删除 V1 队列表：
 * - `pending_uploads`（乐观写队列，Phase 3 已退役为只读日志）
 *
 * 前置条件：
 * - SyncStatusRepository.snapshot() 已改为读 sync_cursor + contacts_cache.isLocalOnly
 * - PendingUploadDao 已无生产代码引用
 *
 * 数据安全：
 * - pending_uploads 中的历史数据已无业务含义（队列已退役）
 * - operation_history 表保留（只读本地日志，OperationHistoryRepository 仍消费）
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 4 Task #21
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 删除 V1 队列表
        db.execSQL("DROP TABLE IF EXISTS pending_uploads")

        Log.d("DatabaseModule", "MIGRATION_13_14: dropped V1 queue table (pending_uploads)")
    }
}

/**
 * v14 → v15 schema 迁移（Phase B6：V1 残留表清理）。
 *
 * 删除 FTS 虚拟表 + 触发器 + V1 表：
 * 1. contacts_fts / tags_fts（FTS4 虚拟表，V2 搜索已改用 LIKE）
 * 2. room_fts_content_sync_* 触发器（Room 对 FTS contentEntity 自动生成）
 * 3. contacts / tags / contact_tag / card_collections / user_profile（V1 实体表）
 *
 * 前置条件：
 * - Phase 3 已完成 V1→V2 cache 表迁移（MIGRATION_5_6）
 * - Phase 3 Task #17 已删 V1 字段表（MIGRATION_10_11）
 * - Phase 4 Task #19 已删 V1 平台表（MIGRATION_11_12）
 * - Phase 4 Task #20 已删 V1 扫码表（MIGRATION_12_13）
 * - Phase 4 Task #21 已删 V1 队列表（MIGRATION_13_14）
 * - 无生产代码引用这些表（grep 验证：仅迁移 SQL + 注释）
 *
 * 数据安全：
 * - V2 cache 表已包含所有业务数据
 * - FTS 表可从 V1 contacts/tags 重建（但 V2 不再使用 FTS）
 *
 * 对应规约：implementation_plan.md Phase B B6
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. 先删触发器（依赖 FTS 表），再删 FTS 虚拟表
        //    Room 自动生成的 FTS content sync 触发器
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_AFTER_INSERT")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_AFTER_INSERT")
        // 遗留触发器（v2→v3 已 DROP，防御性再删一次）
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ad")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_au")

        // 2. DROP FTS 虚拟表
        db.execSQL("DROP TABLE IF EXISTS contacts_fts")
        db.execSQL("DROP TABLE IF EXISTS tags_fts")

        // 3. DROP V1 实体表
        db.execSQL("DROP TABLE IF EXISTS contact_tag")
        db.execSQL("DROP TABLE IF EXISTS contacts")
        db.execSQL("DROP TABLE IF EXISTS tags")
        db.execSQL("DROP TABLE IF EXISTS card_collections")
        db.execSQL("DROP TABLE IF EXISTS user_profile")

        Log.d("DatabaseModule", "MIGRATION_14_15: dropped FTS tables/triggers + V1 tables " +
              "(contacts, tags, contact_tag, card_collections, user_profile)")
    }
}

/**
 * v15 → v16 schema 迁移（Phase 2：通用 Outbox，规格 §3.1 / §5.1）。
 *
 * 1. 新建 `outbox` 表 + ready 索引 + mergeKey 唯一认领索引（列定义必须与
 *    OutboxEntity 的 Room 期望 schema 逐字一致，否则启动校验抛
 *    "Migration didn't properly handle: outbox"）。
 * 2. DROP 旁路表 `pending_person_updates`。该表由 `2c53e21`（2026-09-02）引入，
 *    未随任何版本发版（仓库无 tag），**无生产数据 → 不搬行、不写兼容分支**（§8 #3）。
 *
 * [修复防御] 禁止 destructive fallback：迁移缺失宁可抛异常也不抹用户数据。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // [P3 修复] contacts_cache 主键补 AUTOINCREMENT（实体疏漏，Tag/Collection 均为自增）。
        // 旧表 id 无自增：id=0 字面插入 + REPLACE 会把已有行塌缩成一行。表重建保留全部数据，
        // 列与索引不变；FTS4（contacts_fts）挂在 V1 contacts 表上，不受影响。
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `contacts_cache_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `serverId` TEXT,
                `name` TEXT NOT NULL,
                `avatarUrl` TEXT,
                `avatarPath` TEXT,
                `note` TEXT,
                `bio` TEXT,
                `pinyinInitial` TEXT NOT NULL,
                `platformsJson` TEXT NOT NULL,
                `createTime` INTEGER NOT NULL,
                `updateTime` INTEGER NOT NULL,
                `lastSyncedAt` INTEGER NOT NULL,
                `isLocalOnly` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                `self` INTEGER
            )
        """)
        db.execSQL("""
            INSERT INTO `contacts_cache_new` (
                `id`,`serverId`,`name`,`avatarUrl`,`avatarPath`,`note`,`bio`,`pinyinInitial`,
                `platformsJson`,`createTime`,`updateTime`,`lastSyncedAt`,`isLocalOnly`,`isDeleted`,`self`
            ) SELECT
                `id`,`serverId`,`name`,`avatarUrl`,`avatarPath`,`note`,`bio`,`pinyinInitial`,
                `platformsJson`,`createTime`,`updateTime`,`lastSyncedAt`,`isLocalOnly`,`isDeleted`,`self`
            FROM `contacts_cache`
        """)
        db.execSQL("DROP TABLE `contacts_cache`")
        db.execSQL("ALTER TABLE `contacts_cache_new` RENAME TO `contacts_cache`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_cache_isDeleted` ON `contacts_cache` (`isDeleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_cache_isLocalOnly` ON `contacts_cache` (`isLocalOnly`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_contacts_cache_serverId` ON `contacts_cache` (`serverId`)")
        Log.d("DatabaseModule", "MIGRATION_16_17: contacts_cache rebuilt with AUTOINCREMENT id (data preserved)")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS outbox (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                entityKind TEXT NOT NULL,
                localId INTEGER NOT NULL,
                remoteId TEXT,
                op TEXT NOT NULL,
                mergeKey TEXT,
                payloadJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                attempts INTEGER NOT NULL,
                nextAttemptAt INTEGER NOT NULL,
                lastError TEXT
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_nextAttemptAt_entityKind` ON `outbox` (`nextAttemptAt`, `entityKind`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_mergeKey` ON `outbox` (`mergeKey`)")
        db.execSQL("DROP TABLE IF EXISTS pending_person_updates")

        Log.d("DatabaseModule", "MIGRATION_15_16: outbox created, pending_person_updates dropped (no data carried)")
    }
}

@Database(
    entities = [
        // V2 cache 表(主路径)
        ContactCacheEntity::class,
        ContactFieldCacheEntity::class,
        ContactFieldValueCacheEntity::class,
        ContactPlatformCacheEntity::class,
        TagCacheEntity::class,
        CardCollectionCacheEntity::class,
        UserProfileCacheEntity::class,
        ContactTagCacheEntity::class,
        // [Phase 3] sync 游标
        SyncCursorEntity::class,
        // [Phase 2] Person Profile 子表
        PersonProfileCacheEntity::class,
        // [Phase 3 Task #30] custom_fields V2 cache 表
        CustomFieldCacheEntity::class,
        // [Phase 4 Task #20] 名片夹成员关联 V2 cache 表
        CollectionMemberCacheEntity::class,
        // V2 queue 表（退役为本地只读日志）
        OperationHistoryEntity::class,
        // [Phase 2] 通用 Outbox（规格 §3.1，替代 pending_person_updates 旁路表）
        OutboxEntity::class,
    ],
    version = 17,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // [A3] V2 cache DAO(主路径)
    abstract fun contactCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
    abstract fun contactFieldCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
    abstract fun contactFieldValueCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
    abstract fun contactPlatformCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
    abstract fun tagCacheDao(): top.mcxiafeng.badger.data.cache.dao.TagCacheDao
    abstract fun cardCollectionCacheDao(): top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
    abstract fun userProfileCacheDao(): top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
    abstract fun contactTagCacheDao(): top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
    // [Phase 3 Task #30] custom_fields V2 cache DAO
    abstract fun customFieldCacheDao(): top.mcxiafeng.badger.data.cache.dao.CustomFieldCacheDao

    // [Phase 4 Task #20] 名片夹成员关联 V2 cache DAO
    abstract fun collectionMemberCacheDao(): top.mcxiafeng.badger.data.cache.dao.CollectionMemberCacheDao

    // [Phase 3] sync 游标 DAO
    abstract fun syncCursorDao(): top.mcxiafeng.badger.data.cache.dao.SyncCursorDao

    // [Phase 2] Person Profile 子表 DAO
    abstract fun personProfileCacheDao(): top.mcxiafeng.badger.data.cache.dao.PersonProfileCacheDao

    // [V2-P2] queue DAO(历史只读日志)
    abstract fun operationHistoryDao(): OperationHistoryDao

    // [Phase 2] 通用 Outbox DAO
    abstract fun outboxDao(): OutboxDao

    companion object {
        // [§14.2] 提取出 build 工厂,让 Koin module 可以单行构造。对应原 Hilt
        // DatabaseModule.provideDatabase,但把 callback 内的"seed/ensureDefaults
        // / backupDatabaseBeforeDestructive"全部下放到这里,Koin 端只需一行。
        fun build(context: android.content.Context): AppDatabase {
            return androidx.room.Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "badger_database"
            )
                .addCallback(object : androidx.room.RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedDefaults(db)
                    }

                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        ensureDefaults(db)
                    }

                    override fun onDestructiveMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        backupDatabaseBeforeDestructive(context)
                    }
                })
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                )
                // [§14.7 / §15.4 #17] 迁移链 MIGRATION_1_2~5_6 已完整覆盖 1→6;
                // 移除 fallbackToDestructiveMigration() 以免版本错位时静默丢数据。
                // 万一未来真的发生迁移缺失,Room 会抛 IllegalStateException,crashlytics
                // 上报后人工补 Migration,而不是悄悄抹掉用户的联系人。
                .build()
        }

        private fun backupDatabaseBeforeDestructive(context: android.content.Context) {
            try {
                val dbDir = context.getDatabasePath("badger_database").parentFile ?: return
                val src = java.io.File(dbDir, "badger_database")
                if (!src.exists()) {
                    android.util.Log.w(TAG, "backupDatabaseBeforeDestructive: source db not found, skip")
                    return
                }
                val dumpDir = java.io.File(dbDir, "dump").apply { mkdirs() }
                val dst = java.io.File(dumpDir, "badger_${System.currentTimeMillis()}.db")
                src.copyTo(dst, overwrite = false)
                android.util.Log.e(TAG, "backupDatabaseBeforeDestructive: copied ${src.length()} bytes to ${dst.absolutePath}")
                android.util.Log.e(TAG, "  → adb pull ${dst.absolutePath} 把损坏前的 db 拿出来")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "backupDatabaseBeforeDestructive failed", e)
            }
        }

        private fun seedDefaults(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            // [Phase 3] contact_fields 已删，改用 contact_fields_cache
            top.mcxiafeng.badger.ocr.ALL_FIELDS.forEachIndexed { index, def ->
                db.execSQL(
                    "INSERT OR REPLACE INTO contact_fields_cache (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)",
                    arrayOf<Any>(def.displayName, def.fieldKey, def.fieldKey ?: "", index + 1, now)
                )
            }
            db.execSQL(
                "INSERT OR REPLACE INTO user_profile_cache (id, name, bio, platformsJson, updateTime) VALUES (1, '用户', NULL, '{}', ?)",
                arrayOf<Any>(now)
            )
            db.execSQL(
                "INSERT OR REPLACE INTO card_collections_cache (id, name, description, personMembers, createTime, isLocalOnly) VALUES (1, '默认名片夹', '所有新扫描的联系人将添加到此处', '[]', ?, 1)",
                arrayOf<Any>(now)
            )
        }

        private fun ensureDefaults(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            // [Phase 3] contact_fields 已删，改用 contact_fields_cache
            top.mcxiafeng.badger.ocr.ALL_FIELDS.forEachIndexed { index, def ->
                val cursor = db.query("SELECT id FROM contact_fields_cache WHERE fieldKey = ?", arrayOf(def.fieldKey))
                val exists = cursor.moveToFirst()
                cursor.close()
                if (!exists) {
                    db.execSQL(
                        "INSERT INTO contact_fields_cache (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)",
                        arrayOf<Any>(def.displayName, def.fieldKey, def.fieldKey ?: "", index + 1, now)
                    )
                }
            }
            val profileCursor = db.query("SELECT id FROM user_profile_cache WHERE id = 1")
            val profileExists = profileCursor.moveToFirst()
            profileCursor.close()
            if (!profileExists) {
                db.execSQL(
                    "INSERT INTO user_profile_cache (id, name, bio, platformsJson, updateTime) VALUES (1, '用户', NULL, '{}', ?)",
                    arrayOf<Any>(now)
                )
            }
        }

        private const val TAG = "DatabaseModule"
    }
}
