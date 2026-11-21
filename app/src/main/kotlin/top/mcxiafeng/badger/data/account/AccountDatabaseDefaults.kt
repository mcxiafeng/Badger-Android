package top.mcxiafeng.badger.data.account

import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.ocr.ALL_FIELDS

internal object AccountDatabaseDefaults : RoomDatabase.Callback() {
    private const val TAG = "AccountDatabaseDefaultsTester"

    override fun onCreate(db: SupportSQLiteDatabase) {
        seedDefaults(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        ensureDefaults(db)
        dropLegacyFtsTriggers(db)
    }

    private fun seedDefaults(db: SupportSQLiteDatabase) {
        Log.d(TAG, "seedDefaults: seeding default fields and profile")
        val now = System.currentTimeMillis()
        ALL_FIELDS.forEachIndexed { index, def ->
            db.execSQL(
                "INSERT OR REPLACE INTO contact_fields (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)",
                arrayOf<Any>(def.displayName, def.fieldKey, def.fieldKey ?: "", index + 1, now)
            )
        }
        db.execSQL(
            "INSERT OR REPLACE INTO user_profile (id, name, bio, updateTime) VALUES (1, '用户', NULL, ?)",
            arrayOf<Any>(now)
        )
        db.execSQL(
            "INSERT OR REPLACE INTO card_collections (id, name, description, createTime) VALUES (1, '默认名片夹', '所有新扫描的联系人将添加到此处', ?)",
            arrayOf<Any>(now)
        )
        Log.d(TAG, "seedDefaults: done")
    }

    private fun ensureDefaults(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        ALL_FIELDS.forEachIndexed { index, def ->
            val cursor = db.query("SELECT id FROM contact_fields WHERE fieldKey = ?", arrayOf(def.fieldKey))
            val exists = cursor.moveToFirst()
            cursor.close()
            if (!exists) {
                db.execSQL(
                    "INSERT INTO contact_fields (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)",
                    arrayOf<Any>(def.displayName, def.fieldKey, def.fieldKey ?: "", index + 1, now)
                )
                Log.d(TAG, "ensureDefaults: inserted missing field ${def.fieldKey}")
            }
        }
        val profileCursor = db.query("SELECT id FROM user_profile WHERE id = 1")
        val profileExists = profileCursor.moveToFirst()
        profileCursor.close()
        if (!profileExists) {
            db.execSQL(
                "INSERT INTO user_profile (id, name, bio, updateTime) VALUES (1, '用户', NULL, ?)",
                arrayOf<Any>(now)
            )
            Log.d(TAG, "ensureDefaults: inserted default profile")
        }
        ensureMyContact(db, now)
    }

    /**
     * 确保"我的名片"联系人（id = MY_CONTACT_ID = -1）存在。
     *
     * 用户自己的名片按真实联系人存储，编辑页直接复用 ContactDetailPage。
     * 老用户首次打开时把 user_profile 里的 name/bio/avatarPath/platforms(JSON)
     * 迁移到 contacts(-1) + contact_platforms；user_profile 此后仅保留
     * cardImagePath/defaultPlatform 两个 Contact 没有的字段。
     * 幂等：contacts 已有 -1 行时直接返回。
     */
    private fun ensureMyContact(db: SupportSQLiteDatabase, now: Long) {
        val myCursor = db.query("SELECT id FROM contacts WHERE id = -1")
        val myExists = myCursor.moveToFirst()
        myCursor.close()
        if (myExists) return

        var name = ""
        var bio: String? = null
        var avatarPath: String? = null
        var platformsJson: String? = null
        val profileCursor = db.query(
            "SELECT name, bio, avatarPath, platforms, updateTime FROM user_profile WHERE id = 1"
        )
        if (profileCursor.moveToFirst()) {
            name = profileCursor.getString(0) ?: ""
            bio = profileCursor.getString(1)
            avatarPath = profileCursor.getString(2)
            platformsJson = profileCursor.getString(3)
        }
        profileCursor.close()

        db.execSQL(
            "INSERT INTO contacts (id, name, avatarUrl, avatarPath, note, bio, pinyinInitial, platforms, createTime, updateTime) VALUES (-1, ?, NULL, ?, NULL, ?, '', NULL, ?, ?)",
            arrayOf<Any>(name, avatarPath ?: "", bio ?: "", now, now)
        )
        // 迁移旧平台数据（JSON map）到 contact_platforms 表
        if (!platformsJson.isNullOrBlank()) {
            try {
                val map: Map<String, PlatformEntry> = Gson().fromJson(
                    platformsJson,
                    object : TypeToken<Map<String, PlatformEntry>>() {}.type
                )
                map.forEach { (key, entry) ->
                    if (entry.jumpLink.isNotBlank() || !entry.value.isNullOrBlank()) {
                        db.execSQL(
                            "INSERT OR REPLACE INTO contact_platforms (contactId, platformKey, value, displayName, jumpLink, originalLink, avatarUrl) VALUES (-1, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any>(key, entry.value ?: "", entry.displayName ?: "", entry.jumpLink, entry.originalLink ?: "", entry.avatarUrl ?: "")
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ensureMyContact: 迁移 user_profile.platforms 失败", e)
            }
        }
        Log.d(TAG, "ensureMyContact: seeded my contact (id=-1), platform data present=${platformsJson != null}")
    }

    /**
     * 清理老版本 MIGRATION_2_3 创建的 contacts_ai/ad/au FTS 同步触发器。
     *
     * 旧迁移使用 FTS4 `'delete'` 控制命令手写了 3 个同步触发器，
     * 但 Room 通过 @Fts4(contentEntity = Contact::class) 已经自动生成了
     * 4 个 room_fts_content_sync_* 触发器。两套触发器在 DELETE 行时并发执行，
     * 迁移版的 `contacts_ad` 触发 FTS4 `'delete'` 命令在内容表行已删除的情况下
     * 报 SQLITE_ERROR，导致 deleteContact 崩溃。
     *
     * 保留 Room 自动生成的触发器即可维护 FTS 索引，所以这里直接 DROP 掉手写的。
     */
    private fun dropLegacyFtsTriggers(db: SupportSQLiteDatabase) {
        val legacyTriggers = listOf("contacts_ai", "contacts_ad", "contacts_au")
        legacyTriggers.forEach { trigger ->
            db.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
        }
        Log.d(TAG, "dropLegacyFtsTriggers: dropped legacy FTS sync triggers (conflicted with Room's auto-generated triggers)")
    }

}
