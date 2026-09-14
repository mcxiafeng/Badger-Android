package top.mcxiafeng.badger.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import top.mcxiafeng.badger.ocr.ALL_FIELDS
import top.mcxiafeng.badger.shared.util.nowMs

object AppDatabaseSeed {

    fun seedDefaults(db: SQLiteConnection) {
        val now = nowMs()
        
        ALL_FIELDS.forEachIndexed { index, def ->
            db.prepare(
                "INSERT OR REPLACE INTO contact_fields_cache (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)"
            ).use { stmt ->
                stmt.bindText(1, def.displayName)
                stmt.bindText(2, def.fieldKey)
                stmt.bindText(3, def.fieldKey ?: "")
                stmt.bindLong(4, (index + 1).toLong())
                stmt.bindLong(5, now)
                stmt.step()
            }
        }
        db.prepare(
            "INSERT OR REPLACE INTO user_profile_cache (id, name, bio, platformsJson, updateTime) VALUES (1, '用户', NULL, '{}', ?)"
        ).use { stmt ->
            stmt.bindLong(1, now)
            stmt.step()
        }
        db.prepare(
            "INSERT OR REPLACE INTO card_collections_cache (id, name, description, personMembers, createTime, isLocalOnly) VALUES (1, '默认名片夹', '所有新扫描的联系人将添加到此处', '[]', ?, 1)"
        ).use { stmt ->
            stmt.bindLong(1, now)
            stmt.step()
        }
    }

    fun ensureDefaults(db: SQLiteConnection) {
        val now = nowMs()
        ALL_FIELDS.forEachIndexed { index, def ->
            var exists = false
            db.prepare("SELECT id FROM contact_fields_cache WHERE fieldKey = ?").use { stmt ->
                stmt.bindText(1, def.fieldKey)
                exists = stmt.step()
            }
            if (!exists) {
                db.prepare(
                    "INSERT INTO contact_fields_cache (fieldName, fieldKey, icon, sortOrder, isSystem, isEnabled, createTime) VALUES (?, ?, ?, ?, 1, 1, ?)"
                ).use { stmt ->
                    stmt.bindText(1, def.displayName)
                    stmt.bindText(2, def.fieldKey)
                    stmt.bindText(3, def.fieldKey ?: "")
                    stmt.bindLong(4, (index + 1).toLong())
                    stmt.bindLong(5, now)
                    stmt.step()
                }
            }
        }
        var profileExists = false
        db.prepare("SELECT id FROM user_profile_cache WHERE id = 1").use { stmt ->
            profileExists = stmt.step()
        }
        if (!profileExists) {
            db.prepare(
                "INSERT INTO user_profile_cache (id, name, bio, platformsJson, updateTime) VALUES (1, '用户', NULL, '{}', ?)"
            ).use { stmt ->
                stmt.bindLong(1, now)
                stmt.step()
            }
        }
    }
}
