package top.mcxiafeng.badger.data.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.account.AccountWriteLocks
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WorkspaceTransactionTest {
    @Test fun existingWriterFinishesBeforeRemoteTransactionIsScheduled() = runBlocking<Unit> {
        val context: Context = RuntimeEnvironment.getApplication()
        val queue = ArrayDeque<Runnable>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().setTransactionExecutor(Executor { queue.addLast(it) }).build()
        try {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("INSERT INTO user_profile(id,name,cardImagePath,defaultPlatform,updateTime) VALUES (1,'本人','old','qq',1)")
            val gate = AccountWriteLocks.forDao(db.userProfileDao())
            val owner = Any()
            gate.lock(owner)
            val remote = async(Dispatchers.Unconfined) {
                workspaceTransaction(db) {
                    val latest = sql.rows("user_profile").single().deepCopy().apply {
                        remove("id"); addProperty("defaultPlatform", "github")
                    }
                    sql.upsert("user_profile", 1, latest)
                }
            }
            try {
                // Scheduling SQLite first would deadlock a repository writer holding this mutex.
                assertThat(queue).isEmpty()
                assertThat(remote.isCompleted).isFalse()
                sql.execSQL("UPDATE user_profile SET cardImagePath = 'new' WHERE id = 1")
                gate.unlock(owner)
                yield()
                assertThat(queue).hasSize(1)
                queue.removeFirst().run()
                remote.await()
                assertThat(sql.rows("user_profile").single().str("cardImagePath")).isEqualTo("new")
                assertThat(sql.rows("user_profile").single().str("defaultPlatform")).isEqualTo("github")
            } finally {
                if (gate.holdsLock(owner)) gate.unlock(owner)
                remote.cancel()
                while (queue.isNotEmpty()) queue.removeFirst().run()
            }
        } finally { db.close() }
    }
}
