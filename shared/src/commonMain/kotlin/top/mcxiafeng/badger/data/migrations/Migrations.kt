package top.mcxiafeng.badger.data.migrations

import top.mcxiafeng.badger.utils.BadgerLog
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE card_collections ADD COLUMN backgroundImagePath TEXT")
        db.execSQL("ALTER TABLE card_collections ADD COLUMN dominantColor INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SQLiteConnection) {
        
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

        
        db.execSQL("ALTER TABLE contacts ADD COLUMN pinyinInitial TEXT NOT NULL DEFAULT ''")

        
        db.execSQL("DROP TABLE IF EXISTS contacts_fts")
        db.execSQL("CREATE VIRTUAL TABLE contacts_fts USING fts4(name, note, content=`contacts`)")

        
        db.execSQL("INSERT INTO contacts_fts(rowid, name, note) SELECT id, name, note FROM contacts")

        
        
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ad")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_au")
        BadgerLog.d("DatabaseModule","MIGRATION_2_3: dropped legacy FTS sync triggers")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SQLiteConnection) {
        
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

        
        
        db.execSQL("UPDATE contacts SET updateTime = updateTime WHERE id > 0")

        BadgerLog.d("DatabaseModule","MIGRATION_3_4: created tags/contact_tag, bumped contacts")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SQLiteConnection) {
        val now = top.mcxiafeng.badger.shared.util.nowMs()

        
        db.execSQL("ALTER TABLE contacts ADD COLUMN bio TEXT")
        db.execSQL("ALTER TABLE tags ADD COLUMN showDot INTEGER NOT NULL DEFAULT 1")
        BadgerLog.d("DatabaseModule","MIGRATION_4_5: added bio/showDot columns")

        
        
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
        BadgerLog.d("DatabaseModule","MIGRATION_4_5: migrated legacy styleColor to tags/contact_tag")

        
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
        
        
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'scan_results'")
        db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES ('scan_results', (SELECT MAX(id) FROM scan_results))")
        BadgerLog.d("DatabaseModule","MIGRATION_4_5: rebuilt scan_results without styleColor, restored sqlite_sequence")

        
        db.execSQL("DROP TABLE IF EXISTS contacts_fts")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ad")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_au")
        
        
        
        
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_AFTER_INSERT")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS contacts_fts USING fts4(name, note, bio, content=`contacts`)")
        db.execSQL("INSERT INTO contacts_fts(rowid, name, note, bio) SELECT id, name, note, bio FROM contacts")
        BadgerLog.d("DatabaseModule","MIGRATION_4_5: rebuilt FTS with bio column, dropped legacy FTS triggers")

        
        db.execSQL("UPDATE contacts SET updateTime = updateTime WHERE id > 0")

        
        
        db.execSQL("ALTER TABLE contact_tag ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
        db.execSQL("ALTER TABLE contact_tag ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE contact_tag ADD COLUMN createTime INTEGER NOT NULL DEFAULT 0")
        
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_tag_contactId_source ON contact_tag(contactId, source)")
        BadgerLog.d("DatabaseModule","MIGRATION_4_5: contact_tag +source/confidence/createTime + (contactId,source) index")

        
        
        db.execSQL("DROP TABLE IF EXISTS tags_fts")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_AFTER_INSERT")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS tags_fts USING fts4(name, pinyinInitial, content=`tags`)")
        db.execSQL("INSERT INTO tags_fts(rowid, name, pinyinInitial) SELECT id, name, pinyinInitial FROM tags")
        BadgerLog.d("DatabaseModule","MIGRATION_4_5: tags_fts created")

        
        db.execSQL("UPDATE contacts SET updateTime = updateTime WHERE id > 0")

        BadgerLog.d("DatabaseModule","MIGRATION_4_5: done — bio/showDot added, styleColor removed, legacy tags migrated, FTS rebuilt with bio, sqlite_sequence restored, contact_tag +source/confidence/createTime, tags_fts created")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SQLiteConnection) {
        
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

        
        
        
        
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contact_fields_fieldKey ON contact_fields(fieldKey)")

        
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

        
        db.execSQL("UPDATE contacts SET updateTime = updateTime WHERE id > 0")

        BadgerLog.d("DatabaseModule","MIGRATION_5_6: 8 cache tables populated (isLocalOnly=1), " +
              "pending_uploads + operation_history empty, FTS untouched")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SQLiteConnection) {
        
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

        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_cursor (
                id INTEGER PRIMARY KEY NOT NULL,
                lastVersion INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("INSERT OR REPLACE INTO sync_cursor (id, lastVersion, updatedAt) VALUES (1, 0, 0)")

        BadgerLog.d("DatabaseModule","MIGRATION_6_7: dropped serverVersion, uuid 语义化, " +
              "tags/collections +serverId/colorHash/personMembers, sync_cursor created")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN sex TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN country TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN region TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN birthday TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN backgroundURL TEXT")
        db.execSQL("ALTER TABLE user_profile_cache ADD COLUMN extra TEXT")
        BadgerLog.d("DatabaseModule","MIGRATION_7_8: user_profile_cache +sex/country/region/birthday/backgroundURL/extra")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SQLiteConnection) {
        
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

        
        db.execSQL("ALTER TABLE contacts_cache ADD COLUMN self INTEGER")

        
        db.execSQL("DROP INDEX IF EXISTS index_contacts_cache_serverId")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contacts_cache_serverId ON contacts_cache(serverId)")

        BadgerLog.d("DatabaseModule","MIGRATION_8_9: person_profile_cache created (PK=contactServerId), contacts_cache +self, serverId unique index")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SQLiteConnection) {
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

        BadgerLog.d("DatabaseModule","MIGRATION_9_10: created custom_fields_cache table")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SQLiteConnection) {
        
        db.execSQL("DROP TABLE IF EXISTS contact_field_values")
        
        db.execSQL("DROP TABLE IF EXISTS custom_fields")
        
        db.execSQL("DROP TABLE IF EXISTS contact_fields")

        BadgerLog.d("DatabaseModule","MIGRATION_10_11: dropped V1 field tables (contact_field_values, custom_fields, contact_fields)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SQLiteConnection) {
        
        db.execSQL("DROP TABLE IF EXISTS contact_platforms")

        BadgerLog.d("DatabaseModule","MIGRATION_11_12: dropped V1 platform table (contact_platforms)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SQLiteConnection) {
        
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

        
        db.execSQL("""
            INSERT OR IGNORE INTO collection_member_cache (contactId, collectionId, addedAt)
            SELECT contactId, collectionId, MIN(scannedTime) FROM scan_results
            GROUP BY contactId, collectionId
        """)

        
        db.execSQL("DROP TABLE IF EXISTS scan_results")

        BadgerLog.d("DatabaseModule","MIGRATION_12_13: created collection_member_cache, migrated data from scan_results, dropped scan_results")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SQLiteConnection) {
        
        db.execSQL("DROP TABLE IF EXISTS pending_uploads")

        BadgerLog.d("DatabaseModule","MIGRATION_13_14: dropped V1 queue table (pending_uploads)")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SQLiteConnection) {
        
        
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_contacts_fts_AFTER_INSERT")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tags_fts_AFTER_INSERT")
        
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_ad")
        db.execSQL("DROP TRIGGER IF EXISTS contacts_au")

        
        db.execSQL("DROP TABLE IF EXISTS contacts_fts")
        db.execSQL("DROP TABLE IF EXISTS tags_fts")

        
        db.execSQL("DROP TABLE IF EXISTS contact_tag")
        db.execSQL("DROP TABLE IF EXISTS contacts")
        db.execSQL("DROP TABLE IF EXISTS tags")
        db.execSQL("DROP TABLE IF EXISTS card_collections")
        db.execSQL("DROP TABLE IF EXISTS user_profile")

        BadgerLog.d("DatabaseModule","MIGRATION_14_15: dropped FTS tables/triggers + V1 tables " +
              "(contacts, tags, contact_tag, card_collections, user_profile)")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SQLiteConnection) {
        
        
        
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
        BadgerLog.d("DatabaseModule","MIGRATION_16_17: contacts_cache rebuilt with AUTOINCREMENT id (data preserved)")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SQLiteConnection) {
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

        BadgerLog.d("DatabaseModule","MIGRATION_15_16: outbox created, pending_person_updates dropped (no data carried)")
    }
}
