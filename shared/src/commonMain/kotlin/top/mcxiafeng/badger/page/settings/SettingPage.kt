package top.mcxiafeng.badger.page.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.UserInfo
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.User
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import kotlin.uuid.Uuid

class SettingPage {

    companion object {
        @Composable
        fun PageSetting() {
            val scope = rememberCoroutineScope()
            Column {
                Button(
                    onClick = {
                        scope.launch {
                            ensureLoggedIn()
                        }
                    }
                ) {
                    Text("登录测试")
                }
            }
        }

        suspend fun ensureLoggedIn() {
            // 在运行中的应用内，不应重置数据库持有者，因为它在应用启动时已由持久化 builder 初始化。
            // 只有在单元测试中（如 TestSession.ensureLoggedIn）才需要重置并重新 init 一个 inMemory 库。
            // SystemDbHolder.resetForTest()

            val userUuid = Uuid.parse("b3115f13-1e09-4046-9f1f-37d61829a0da")
            val profileUuid = Uuid.parse("2f421318-76cb-4650-96c7-95eefd50b8e7")
            val token = "XVQG98HHhIR5OdDYhxRZLsw56VKbwpeVON1DwvCz3Iw"

            // 1. 插入系统库会话（同步载体）
            val systemDao = SystemDbHolder.get().userInfoDao()
            systemDao.clearAllUserInfos()
            systemDao.upsertUserInfo(UserInfo(userUuid = userUuid, token = token))

            // 2. 插入用户缓存库数据（User & Profile），确保 SocialPage 加载时能读取到完整信息
            val cacheDb = CacheDbHolder.get()
            val userDao = cacheDb.userDao()
            val profileDao = cacheDb.profileDao()

            userDao.upsertUser(
                User(
                    uuid = userUuid,
                    profileUuid = profileUuid,
                    name = "badger_tester",
                    displayName = "Badger 测试员",
                    token = token,
                    avatar = "",
                    email = "test@mcxiafeng.top",
                    isAdmin = true,
                    userSettings = JsonObject(emptyMap()),
                    syncVersion = 1L,
                    lastLogin = 1731652136000L,
                    createTime = 1731652136000L
                )
            )

            profileDao.upsertProfile(
                Profile(
                    uuid = profileUuid,
                    sex = "男",
                    description = "这是通过「登录测试」按钮自动生成的测试名片。Badger 是一款跨平台电子名片夹，支持云同步与 NFC。",
                    country = "中国",
                    region = "北京",
                    birthday = "2024-11-15",
                    contactMap = mapOf(
                        "GitHub" to "https://github.com/mcxiafeng",
                        "WeChat" to "badger_wx_dev",
                        "Telegram" to "badger_support"
                    )
                )
            )
        }
    }
}
