package top.mcxiafeng.badger.data

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

        // 5. Create FTS sync triggers
        db.execSQL("""
            CREATE TRIGGER contacts_ai AFTER INSERT ON contacts BEGIN
                INSERT INTO contacts_fts(rowid, name, note) VALUES (new.id, new.name, new.note);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER contacts_ad AFTER DELETE ON contacts BEGIN
                INSERT INTO contacts_fts(contacts_fts, rowid, name, note) VALUES('delete', old.id, old.name, old.note);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER contacts_au AFTER UPDATE ON contacts BEGIN
                INSERT INTO contacts_fts(contacts_fts, rowid, name, note) VALUES('delete', old.id, old.name, old.note);
                INSERT INTO contacts_fts(rowid, name, note) VALUES (new.id, new.name, new.note);
            END
        """)
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
        ContactFts::class
    ],
    version = 3,
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
}
