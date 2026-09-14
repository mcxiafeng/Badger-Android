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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationChainTest {

    
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
                
                AppDatabase.ALL_MIGRATIONS.filter { it.startVersion >= 6 }.forEach { it.migrate(conn) }

                conn.prepare("SELECT name, pinyinInitial, serverId FROM contacts_cache").use { stmt ->
                    assertTrue(stmt.step())
                    assertTrue("王五" == stmt.getText(0))
                    assertTrue("W" == stmt.getText(1))
                    assertTrue("srv-1" == stmt.getText(2))
                }
                
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
