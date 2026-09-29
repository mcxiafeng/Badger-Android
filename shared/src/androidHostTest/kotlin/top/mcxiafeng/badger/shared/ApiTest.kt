package top.mcxiafeng.badger.shared

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.user.entity.Contact
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.network.core.PersonApi
import top.mcxiafeng.badger.network.core.ProfileApi
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiTest {

    @Test
    fun testCreatePersonWithFullProfile() = runTest {
        println("==================================================================================================")
        println("ApiTest: 开始创建新 Person 及完整 Profile 测试")
        println("==================================================================================================")

        TestSession.ensureLoggedIn()

        // 1. 构造一个包含完整字段信息的 Profile 对象
        val fullProfile = Profile(
            uuid = Uuid.parse("327b9b5c-6214-4e4f-bd82-013071c35f6e"),
            sex = "MALE",
            backgroundURL = "https://example.com/images/bg_developer.jpg",
            description = "高级 Android / KMP 架构师，专注于跨平台技术与电子名片系统架构设计",
            country = "中国",
            region = "北京市",
            birthday = "1995-06-15",
            contact = listOf(
                Contact(platformName = "GitHub", sourceUrl = "https://github.com/mcxiafeng"),
                Contact(platformName = "Email", sourceUrl = "mailto:mcxiafeng@example.com"),
                Contact(platformName = "Phone", sourceUrl = "tel:+8613800000000"),
                Contact(platformName = "Website", sourceUrl = "https://mcxiafeng.top"),
                Contact(platformName = "WeChat", sourceUrl = "weixin://dl/chat?username=mcxiafeng_dev"),
                Contact(platformName = "X", sourceUrl = "https://x.com/mcxiafeng")
            ),
//            extra = buildJsonObject {
//                put("company", "Badger Tech Studio")
//                put("title", "Lead Architect")
//                put("department", "Mobile Engineering")
//                put("note", "由 ApiTest 建立的高完成度测试名片")
//                put("customField", "自定义扩展数据测试")
//                put("tags", buildJsonArray {
//                    add("KMP")
//                    add("Android")
//                    add("Kotlin")
//                    add("Architecture")
//                })
//            }
        )

        // 2. 更新服务器 Profile 数据
        println("正在更新后端 Profile (uuid=${fullProfile.uuid})...")
        ProfileApi.updateProfile(fullProfile)

        // 3. 校验并获取最新的 Profile
//        val fetchedProfile = ProfileApi.getProfile(fullProfile.uuid)
//        println("获取到更新后的 Profile: $fetchedProfile")
//        println(" -> 性别: ${fetchedProfile?.sex}")
//        println(" -> 地区: ${fetchedProfile?.country} / ${fetchedProfile?.region}")
//        println(" -> 生日: ${fetchedProfile?.birthday}")
//        println(" -> 简介: ${fetchedProfile?.description}")
//        println(" -> 社交联系方式: ${fetchedProfile?.contact}")
//        println(" -> 扩展信息 extra: ${fetchedProfile?.extra}")

        // 4. 创建关联此 Profile 的新 Person 实体
//        val personUuid = Uuid.random()
//        val newPerson = Person(
//            uuid = personUuid,
//            ownerId = TestSession.SEED_USER_UUID,
//            profileId = fullProfile.uuid,
//            name = "张三 (完整 Profile 测试)",
//            avatarURL = "https://example.com/avatar/zhangsan.jpg",
//            createTime = System.currentTimeMillis(),
//            updateTime = System.currentTimeMillis()
//        )
//
//        println("正在创建新 Person (uuid=$personUuid)...")
//        val createResult = PersonApi.createPerson(newPerson)
//        println("创建 Person 结果: $createResult")

        // 5. 获取并确认单个 Person 及 Person 列表
//        val fetchedPerson = PersonApi.getPerson(Uuid.parse("13320f62-742d-488d-b647-0d7614fc099f"))
//        println("获取到新创建的 Person: $fetchedPerson")
//
//        val persons = PersonApi.getPersons()
//        println("获取到的 Person 列表总量: ${persons?.size}")
//        persons?.forEachIndexed { index, p ->
//            println(" [$index] uuid=${p.uuid}, name=${p.name}, profileId=${p.profileId}")
//        }

        println("==================================================================================================")
        println("ApiTest: 测试完成")
        println("==================================================================================================")
    }
}

