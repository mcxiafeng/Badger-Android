package top.mcxiafeng.badger.data.account

import android.content.Context
import android.util.Log
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import top.mcxiafeng.badger.data.ContactSearchIndex
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.MIGRATION_1_2
import top.mcxiafeng.badger.data.MIGRATION_2_3
import top.mcxiafeng.badger.data.MIGRATION_3_4
import top.mcxiafeng.badger.data.MIGRATION_4_5
import top.mcxiafeng.badger.data.MIGRATION_5_6
import javax.inject.Inject
import javax.inject.Singleton

/** Handles stay open across account changes so in-flight work keeps its original database. */
@Singleton
class AccountDatabaseProvider @Inject constructor(@ApplicationContext private val context: Context) {
    private val databases = mutableMapOf<String, AppDatabase>()

    @Synchronized
    fun open(scope: AccountScope): AppDatabase = databases.getOrPut(scope.key) {
        val expected = if (scope.isLocal) AccountScope.LOCAL_DATABASE_NAME else
            AccountScope.remote(
                checkNotNull(scope.serverId), checkNotNull(scope.userId), checkNotNull(scope.dataEpoch),
                scope.username, scope.baseUrl,
            ).also { require(it.key == scope.key) }.databaseName
        require(scope.databaseName == expected) { "Account database identity mismatch" }
        Log.d(TAG, "Creating fixed database handle; local=${scope.isLocal}")
        Room.databaseBuilder(context, AppDatabase::class.java, expected)
            .addCallback(AccountDatabaseDefaults)
            .addCallback(ContactSearchIndex)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    companion object {
        private const val TAG = "AccountDatabaseProviderTester"
    }
}
