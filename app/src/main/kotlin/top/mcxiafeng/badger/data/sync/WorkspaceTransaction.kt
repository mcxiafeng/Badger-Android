package top.mcxiafeng.badger.data.sync

import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.account.AccountWriteLocks

/** Locks precede the SQLite transaction: repository read-modify-write must finish before remote application. */
internal suspend fun <T> workspaceTransaction(db: AppDatabase, block: suspend () -> T): T =
    AccountWriteLocks.forDao(db.contactDao()).withLock {
        AccountWriteLocks.forDao(db.userProfileDao()).withLock {
            AccountWriteLocks.forDao(db.cardCollectionDao()).withLock {
                db.withTransaction { block() }
            }
        }
    }
