package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase

expect suspend fun <T> RoomDatabase.dbTransaction(block: suspend () -> T): T
