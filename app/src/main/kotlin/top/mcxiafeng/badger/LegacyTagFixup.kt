package top.mcxiafeng.badger

import android.util.Log
import top.mcxiafeng.badger.data.account.AccountDatabaseProvider
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl
import javax.inject.Inject
import javax.inject.Singleton

/** Repairs historical tag initials using a fixed legacy-local database handle. */
@Singleton
class LegacyTagFixup @Inject constructor(
    private val accounts: AccountStore,
    private val databases: AccountDatabaseProvider,
) {
    suspend fun runOnce() {
        val db = databases.open(accounts.localScope())
        val tags = TagRepositoryImpl(db.tagDao(), db.contactTagDao(), db.tagFtsDao(), db.contactDao(), db)
        try {
            val pending = tags.getAllTagsOnce().filter { it.source == "legacy" && it.pinyinInitial.isBlank() }
            Log.d(TAG, "Repairing legacy local tags; count=${pending.size}")
            for (tag in pending) {
                try {
                    tags.recomputePinyinInitial(tag.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Legacy tag repair failed", e)
                }
            }
            Log.d(TAG, "Legacy local tag repair completed")
        } catch (e: Exception) {
            Log.e(TAG, "Legacy local tag repair failed", e)
        }
    }

    companion object {
        private const val TAG = "LegacyTagFixupTester"
    }
}
