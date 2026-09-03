package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase

/**
 * [K02 spike] Room KMP databaseBuilder 的平台边界（expect/actual）。
 * K07 接入真实 AppDatabase 时沿用此模式：Android 走 Context 文件路径，iOS 走 Documents 目录。
 */
expect fun platformSpikeDatabaseBuilder(name: String): RoomDatabase.Builder<SpikeDatabase>
