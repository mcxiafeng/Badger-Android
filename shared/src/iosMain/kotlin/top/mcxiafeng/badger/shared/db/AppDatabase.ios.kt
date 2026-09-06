package top.mcxiafeng.badger.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.AppDatabaseSeed

/**
 * [KMP K16] iOS AppDatabase builder：NSDocumentDirectory + bundled driver + 17 版迁移链。
 *
 * iOS 端是全新数据库（Q4 裁决：空库 + 服务端全量 pull bootstrap，SyncEngine 已具备），
 * 迁移链 addMigrations 原样挂载仅为防御（全新库 v1 起步不会触发）；destructive 备份
 * 是 Android 旧库升级专属语义，iOS 无此场景。seed/ensureDefaults 与 Android 同源
 * （common AppDatabaseSeed）。
 */
fun iosAppDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(iosDocumentsDbPath(AppDatabase.DB_NAME))
        .setDriver(BundledSQLiteDriver())
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SQLiteConnection) {
                super.onCreate(db)
                AppDatabaseSeed.seedDefaults(db)
            }

            override fun onOpen(db: SQLiteConnection) {
                super.onOpen(db)
                AppDatabaseSeed.ensureDefaults(db)
            }
        })
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
