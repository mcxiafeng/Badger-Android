package top.mcxiafeng.badger.data

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import top.mcxiafeng.badger.shared.db.androidDatabaseBuilder

/**
 * [KMP K08-B] AppDatabase 的 Android 平台构造宿主（从 AppDatabase companion 拆出）。
 * AppDatabase 本体（@Database + DAO + 迁移链清单）已迁 shared commonMain；
 * 本文件保留平台耦合面：filesDir builder、seed/ensureDefaults 回调（依赖 ocr.ALL_FIELDS，
 * 该注册表引用 R.drawable 资源——留 app 源集）、破坏性迁移前备份。
 */
object AppDatabaseHost {

    private const val TAG = "DatabaseModule"

    // [§14.2] build 工厂,让 Koin module 可以单行构造。
    //
    // [KMP K07] Room KMP 接入：bundled SQLiteDriver（双端一致行为）+ 同一 getDatabasePath
    // 文件。迁移链 1→17 原样保留（签名已改 SQLiteConnection）。
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
            // [§14.7 / §15.4 #17] 迁移链 MIGRATION_1_2~5_6 已完整覆盖 1→6;
            // 移除 fallbackToDestructiveMigration() 以免版本错位时静默丢数据。
            // 万一未来真的发生迁移缺失,Room 会抛 IllegalStateException,crashlytics
            // 上报后人工补 Migration,而不是悄悄抹掉用户的联系人。
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
        // [KMP K16] seed/ensureDefaults 上移 common（AppDatabaseSeed，iOS 同款复用）
        top.mcxiafeng.badger.data.AppDatabaseSeed.seedDefaults(db)
    }

    private fun ensureDefaults(db: SQLiteConnection) {
        top.mcxiafeng.badger.data.AppDatabaseSeed.ensureDefaults(db)
    }
}
