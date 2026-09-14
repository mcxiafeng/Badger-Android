package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase
import androidx.room.withTransaction

actual suspend fun <T> RoomDatabase.dbTransaction(block: suspend () -> T): T =
    withTransaction { block() }
