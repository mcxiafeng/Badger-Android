package top.mcxiafeng.badger.data

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import top.mcxiafeng.badger.shared.db.androidDatabaseBuilder

object AppDatabaseHost {

    private const val TAG = "DatabaseModule"

    fun build(context: android.content.Context): AppDatabase {
        return top.mcxiafeng.badger.shared.db.androidDatabaseBuilder(
            AppDatabase::class.java,
            AppDatabase.DB_NAME,
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SQLiteConnection) {
                    super.onCreate(db)
                    seedDefaults(db)
                }

                override fun onOpen(db: SQLiteConnection) {
                    super.onOpen(db)
                    ensureDefaults(db)
                }

                override fun onDestructiveMigration(db: SQLiteConnection) {
                    super.onDestructiveMigration(db)
                    backupDatabaseBeforeDestructive(context)
                }
            })
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
    }

    private fun backupDatabaseBeforeDestructive(context: android.content.Context) {
        try {
            val dbDir = context.getDatabasePath(AppDatabase.DB_NAME).parentFile ?: return
            val src = java.io.File(dbDir, AppDatabase.DB_NAME)
            if (!src.exists()) {
                Log.w(TAG, "backupDatabaseBeforeDestructive: source db not found, skip")
                return
            }
            val dumpDir = java.io.File(dbDir, "dump").apply { mkdirs() }
            val dst = java.io.File(dumpDir, "badger_${System.currentTimeMillis()}.db")
            src.copyTo(dst, overwrite = false)
            Log.e(TAG, "backupDatabaseBeforeDestructive: copied ${src.length()} bytes to ${dst.absolutePath}")
            Log.e(TAG, "  → adb pull ${dst.absolutePath} 把损坏前的 db 拿出来")
        } catch (e: Exception) {
            Log.e(TAG, "backupDatabaseBeforeDestructive failed", e)
        }
    }

    private fun seedDefaults(db: SQLiteConnection) {
        
        AppDatabaseSeed.seedDefaults(db)
    }

    private fun ensureDefaults(db: SQLiteConnection) {
        AppDatabaseSeed.ensureDefaults(db)
    }
}
