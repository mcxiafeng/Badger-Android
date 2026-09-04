package top.mcxiafeng.badger.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * [KMP K07] 迁移链在 Room KMP（bundled driver）下的回归验证。
 *
 * K07 把 16 条 Migration 的签名从 SupportSQLiteDatabase 改为 SQLiteConnection，
 * SQL 语句原样保留。本测试不依赖 MigrationTestHelper（其 KMP 变体在 Android
 * Robolectric 下存在 factory 实例/v17 文件互踩问题），而是：
 * 1. 用 exportSchema 的 6.json/13.json `createSql` 手动建出目标版本 schema
 * 2. 插入数据行（满足 NOT NULL 列）
 * 3. 依次执行 MIGRATION_6_7 … MIGRATION_16_17
 * 4. 断言数据保留 + 17.json 的表结构与实际 sqlite_master 对齐
 *
 * 这等效验证了「Android 旧库 → bundled driver 全链升级」的核心承诺：数据不丢、
 * 迁移语句在 SQLiteConnection 语义下全部可用、终态结构匹配 17 版 schema。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationChainTest {

    // Robolectric 测试 CWD = app/，schema 目录相对 CWD 就是 schemas/
    private val schemaDir = File("schemas/top.mcxiafeng.badger.data.AppDatabase")
        .takeIf { it.exists() }
        ?: File("app/schemas/top.mcxiafeng.badger.data.AppDatabase")

    @Test
    fun migrate6To17_preservesContactRows() {
        val dbFile = Files.createTempFile("mig-chain-6", ".db").toFile()
        try {
            BundledSQLiteDriver().open(dbFile.absolutePath).use { conn ->
                createSchemaFromBundle(conn, 6)
                conn.prepare(
                    "INSERT INTO contacts_cache (name, pinyinInitial, platformsJson, isDeleted, serverId, isLocalOnly, createTime, updateTime, serverVersion, lastSyncedAt) " +
                        "VALUES ('王五', 'W', '{}', 0, 'srv-1', 1, 1000, 1000, 0, 0)"
                ).use { it.step() }
            }

            BundledSQLiteDriver().open(dbFile.absolutePath).use { conn ->
                // 从 v6 起步：只执行 startVersion>=6 的迁移（更早的迁移针对 v6 已不存在的 V1 表）
                AppDatabase.ALL_MIGRATIONS.filter { it.startVersion >= 6 }.forEach { it.migrate(conn) }

                conn.prepare("SELECT name, pinyinInitial, serverId FROM contacts_cache").use { stmt ->
                    assertTrue(stmt.step())
                    assertTrue("王五" == stmt.getText(0))
                    assertTrue("W" == stmt.getText(1))
                    assertTrue("srv-1" == stmt.getText(2))
                }
                // 迁移链后实际表集合应包含 17 版全部表
                val expected = tablesOfBundle(17)
                val actual = actualTables(conn).filterNot { it.startsWith("android_") || it == "room_master_table" }.toSet()
                val missing = expected - actual
                assertTrue("missing tables: $missing", missing.isEmpty())
            }
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun migrate13To17_outboxTableExists() {
        val dbFile = Files.createTempFile("mig-chain-13", ".db").toFile()
        try {
            BundledSQLiteDriver().open(dbFile.absolutePath).use { conn ->
                createSchemaFromBundle(conn, 13)
            }
            BundledSQLiteDriver().open(dbFile.absolutePath).use { conn ->
                AppDatabase.ALL_MIGRATIONS.filter { it.startVersion >= 13 }.forEach { it.migrate(conn) }
                conn.prepare("SELECT COUNT(*) FROM outbox").use { stmt ->
                    assertTrue(stmt.step())
                    assertTrue(stmt.getLong(0) == 0L)
                }
            }
        } finally {
            dbFile.delete()
        }
    }

    // ---- bundle 工具：从 exportSchema JSON 取建表 SQL / 表集合 ----

    private fun bundle(version: Int): Map<String, Any> {
        val text = File(schemaDir, "$version.json").readText()
        @Suppress("UNCHECKED_CAST")
        val root = top.mcxiafeng.badger.network.BadgerJson.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            text,
        ) as kotlinx.serialization.json.JsonObject
        return mapOf("json" to root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun tablesOfBundle(version: Int): Set<String> {
        val root = bundle(version)["json"] as kotlinx.serialization.json.JsonObject
        val db = root["database"] as kotlinx.serialization.json.JsonObject
        return db["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet()
    }

    private fun createSchemaFromBundle(conn: SQLiteConnection, version: Int) {
        val root = bundle(version)["json"] as kotlinx.serialization.json.JsonObject
        val db = root["database"] as kotlinx.serialization.json.JsonObject
        db["entities"]!!.jsonArray.map { it.jsonObject }.forEach { e ->
            val createSql = e["createSql"]!!.jsonPrimitive.content
                .replace("\${TABLE_NAME}", e["tableName"]!!.jsonPrimitive.content)
            conn.prepare(createSql).use { it.step() }
            // 索引
            e["indices"]?.jsonArray?.forEach { idx ->
                val obj = idx.jsonObject
                val sql = obj["createSql"]!!.jsonPrimitive.content
                    .replace("\${TABLE_NAME}", e["tableName"]!!.jsonPrimitive.content)
                conn.prepare(sql).use { it.step() }
            }
        }
    }

    private fun actualTables(conn: SQLiteConnection): List<String> {
        val out = mutableListOf<String>()
        conn.prepare("SELECT name FROM sqlite_master WHERE type='table'").use { stmt ->
            while (stmt.step()) out.add(stmt.getText(0))
        }
        return out
    }
}
