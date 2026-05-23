package top.mcxiafeng.badger.testutil

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.UserProfile

class InMemoryDatabaseRule(
    private val context: Context
) : TestWatcher() {
    lateinit var db: AppDatabase
        private set

    override fun starting(description: Description) {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking { seedDefaults() }
    }

    override fun finished(description: Description) {
        db.close()
    }

    private suspend fun seedDefaults() {
        val defaultFields = listOf(
            ContactField(id = 1, fieldName = "手机", fieldKey = "phone", sortOrder = 0, isSystem = true),
            ContactField(id = 2, fieldName = "邮箱", fieldKey = "email", sortOrder = 1, isSystem = true),
            ContactField(id = 3, fieldName = "微信", fieldKey = "wechat", sortOrder = 2, isSystem = true),
            ContactField(id = 4, fieldName = "QQ", fieldKey = "qq", sortOrder = 3, isSystem = true),
            ContactField(id = 5, fieldName = "哔哩哔哩", fieldKey = "bilibili", sortOrder = 4, isSystem = true),
            ContactField(id = 6, fieldName = "微博", fieldKey = "weibo", sortOrder = 5, isSystem = true),
            ContactField(id = 7, fieldName = "抖音", fieldKey = "douyin", sortOrder = 6, isSystem = true),
            ContactField(id = 8, fieldName = "GitHub", fieldKey = "github", sortOrder = 7, isSystem = true),
            ContactField(id = 9, fieldName = "Telegram", fieldKey = "telegram", sortOrder = 8, isSystem = true),
            ContactField(id = 10, fieldName = "小红书", fieldKey = "xiaohongshu", sortOrder = 9, isSystem = true),
            ContactField(id = 11, fieldName = "Facebook", fieldKey = "facebook", sortOrder = 10, isSystem = true),
            ContactField(id = 12, fieldName = "X", fieldKey = "x", sortOrder = 11, isSystem = true),
            ContactField(id = 13, fieldName = "网站", fieldKey = "website", sortOrder = 12, isSystem = true),
        )
        defaultFields.forEach { db.contactFieldDao().insertField(it) }
        db.cardCollectionDao().insertCollection(CardCollection(id = 1, name = "默认名片夹"))
        db.userProfileDao().saveProfile(UserProfile(id = 1L, name = "用户"))
    }
}
