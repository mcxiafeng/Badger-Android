package top.mcxiafeng.badger.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.AppDatabaseSeed

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
