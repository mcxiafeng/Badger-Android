package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/** [KMP K13b] Android actual：room-ktx withTransaction（原路径零变化）。 */
actual suspend fun <T> RoomDatabase.dbTransaction(block: suspend () -> T): T =
    withTransaction { block() }
