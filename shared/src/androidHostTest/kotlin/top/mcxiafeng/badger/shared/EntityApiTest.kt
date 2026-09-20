package top.mcxiafeng.badger.shared

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.network.core.CollectionApi
import top.mcxiafeng.badger.network.core.PersonApi
import top.mcxiafeng.badger.network.core.ProfileApi
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.network.core.TagsApi
import top.mcxiafeng.badger.utils.HttpResult
import kotlin.uuid.Uuid

/**
 * 实体 API 集成探针：覆盖 data/user/entity 中 Person / Profile / Collection / Tags
 * （缓存层 data/cache 不在本测试范围）的 CRUD + 成员操作，直连运行中的 Badger-Server。
 *
 * 与示例 ApiTest 同源：androidHostTest 源集，runTest + println 探针风格。
 * 依赖 PublicApi.serverUrl 指向已启动的后端；会话经 ensureLoggedInSession 种入
 * System 库载体（生产由登录写入）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntityApiTest {

    private val lines = mutableListOf<String>()
    private var failed = 0

    // 开发后端预置身份（TestSession 经 /api/auth/me 实测对齐）
    private val seedUserUuid = TestSession.SEED_USER_UUID

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
            lines.add("✓ $name")
            println("✓ $name")
        } catch (e: Throwable) {
            failed++
            val msg = "✗ $name — ${e.javaClass.simpleName}: ${e.message}"
            lines.add(msg)
            println(msg)
        }
    }

    /** createXxx 返回 HttpResult，body = {"code":200,...,"data":{"uuid":"..."}}，抽出 uuid。 */
    private fun createdUuid(result: HttpResult): Uuid? {
        val body = (result as? HttpResult.Success)?.body ?: return null
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject ?: return null
        return data["uuid"]?.jsonPrimitive?.content?.let { Uuid.parse(it) }
    }

    @Test
    fun entityLifecycle() = runTest {
        println("==================================================================================================")
        println("EntityApiTest: 开始（后端=${PublicApi.serverUrl}）")
        println("==================================================================================================")
        TestSession.ensureLoggedIn()

        // ---------------- Person ----------------
        // 服务端 getPersons 会回填 ownerId/profileId、移除 owner/profile —— 与客户端实体对齐，decode 正常。
        step("Person.getPersons") {
            val list = PersonApi.getPersons()
            println("  -> size=${list?.size}")
            check(list != null)
        }
        // createPerson 服务端读取 body.uuid（若提供），故自定 uuid 可用于后续 update/delete。
        val personUuid = Uuid.random()
        step("Person.createPerson") {
            val person = Person(
                uuid = personUuid,
                ownerId = seedUserUuid,
                profileId = TestSession.SEED_PROFILE_UUID,
                name = "测试联系人_EntityApiTest",
                createTime = System.currentTimeMillis()
            )
            val r = PersonApi.createPerson(person)
            check(r is HttpResult.Success) { "createPerson 非 Success: $r" }
            val returned = createdUuid(r)
            check(returned == personUuid) { "返回 uuid 不一致 expected=$personUuid actual=$returned" }
        }
        step("Person.getPerson(new)") {
            val p = PersonApi.getPerson(personUuid)
            check(p != null && p.uuid == personUuid)
        }
        step("Person.updatePerson") {
            val person = Person(
                uuid = personUuid,
                ownerId = seedUserUuid,
                profileId = TestSession.SEED_PROFILE_UUID,
                name = "改后联系人_EntityApiTest",
                createTime = System.currentTimeMillis()
            )
            val r = PersonApi.updatePerson(person)
            check(r is HttpResult.Success) { "updatePerson 非 Success: $r" }
        }
        // ---------------- Profile ----------------
        // getProfile 响应移除 creator，与客户端 Profile 实体对齐，decode 正常。
        step("Profile.getProfile(self)") {
            val p = ProfileApi.getProfile(TestSession.SEED_PROFILE_UUID)
            println("  -> uuid=${p?.uuid}")
            check(p != null && p.uuid == TestSession.SEED_PROFILE_UUID)
        }
        // updateProfile 改的是当前登录用户自身的 profile（PUT /api/user/profile 无 uuid 路径）。
        // 为不破坏预置数据，采用"原样回写"——先 GET 再原样 PUT，净数据不变。
        step("Profile.updateProfile(round-trip)") {
            val p = ProfileApi.getProfile(TestSession.SEED_PROFILE_UUID)!!
            val r = ProfileApi.updateProfile(p)
            println("  -> updateProfile result=$r")
        }

        // ---------------- Collection ----------------
        // ⚠️ 预期 decode 失败：服务端 getCollections 移除 owner 后不回填 ownerUuid，
        //    且响应含 backgroundURL/personMembers/createTime 未在客户端实体声明 →
        //    decode 抛 MissingFieldException(ownerUuid) / UnknownKeyException。空列表时不触发。
        step("Collection.getCollections(before)") {
            val list = CollectionApi.getCollections()
            println("  -> size(before)=${list?.size}")
        }
        // createCollection 服务端忽略 body.uuid、自行生成，故须从响应抽 uuid。
        var collectionUuid: Uuid? = null
        step("Collection.createCollection") {
            val c = Collection(
                uuid = Uuid.random(),
                name = "测试名片夹_EntityApiTest",
                description = "由 EntityApiTest 创建",
                ownerUuid = seedUserUuid,
                createTime = System.currentTimeMillis()
            )
            val r = CollectionApi.createCollection(c)
            check(r is HttpResult.Success) { "createCollection 非 Success: $r" }
            collectionUuid = createdUuid(r)
            println("  -> server uuid=$collectionUuid")
            check(collectionUuid != null)
        }
        step("Collection.getCollections(after)") {
            val list = CollectionApi.getCollections()
            println("  -> size(after)=${list?.size}")
        }
        step("Collection.updateCollection") {
            val created = collectionUuid ?: error("无 collectionUuid")
            val c = Collection(
                uuid = created,
                name = "改后名片夹_EntityApiTest",
                description = "由 EntityApiTest 更新",
                ownerUuid = seedUserUuid,
                createTime = System.currentTimeMillis()
            )
            val r = CollectionApi.updateCollection(c)
            check(r is HttpResult.Success) { "updateCollection 非 Success: $r" }
        }
        step("Collection.addCollectionMember") {
            val created = collectionUuid ?: error("无 collectionUuid")
            val r = CollectionApi.addCollectionMember(created, personUuid)
            check(r is HttpResult.Success) { "addCollectionMember 非 Success: $r" }
        }
        step("Collection.removeCollectionMember") {
            val created = collectionUuid ?: error("无 collectionUuid")
            val r = CollectionApi.removeCollectionMember(created, personUuid)
            check(r is HttpResult.Success) { "removeCollectionMember 非 Success: $r" }
        }
        // ---------------- Tags ----------------
        // ⚠️ 预期 decode 失败：服务端 getTags 响应含 createTime/colorHash/personMembers 未在
        //    客户端 Tags 实体（仅 uuid+name）声明 → decode 抛 UnknownKeyException。空列表时不触发。
        step("Tags.getTags(before)") {
            val list = TagsApi.getTags()
            println("  -> size(before)=${list?.size}")
        }
        var tagUuid: Uuid? = null
        step("Tags.createTag") {
            val t = Tags(uuid = Uuid.random(), name = "测试标签_EntityApiTest")
            val r = TagsApi.createTag(t)
            check(r is HttpResult.Success) { "createTag 非 Success: $r" }
            tagUuid = createdUuid(r)
            println("  -> server uuid=$tagUuid")
            check(tagUuid != null)
        }
        step("Tags.getTags(after)") {
            val list = TagsApi.getTags()
            println("  -> size(after)=${list?.size}")
        }
        step("Tags.updateTag") {
            val created = tagUuid ?: error("无 tagUuid")
            val t = Tags(uuid = created, name = "改后标签_EntityApiTest")
            val r = TagsApi.updateTag(t)
            check(r is HttpResult.Success) { "updateTag 非 Success: $r" }
        }
        step("Tags.addTagMember") {
            val created = tagUuid ?: error("无 tagUuid")
            val r = TagsApi.addTagMember(created, personUuid)
            check(r is HttpResult.Success) { "addTagMember 非 Success: $r" }
        }
        step("Tags.removeTagMember") {
            val created = tagUuid ?: error("无 tagUuid")
            val r = TagsApi.removeTagMember(created, personUuid)
            check(r is HttpResult.Success) { "removeTagMember 非 Success: $r" }
        }
        // ---------------- 清理（person 留到最后：Collection/Tag 成员步骤都要用） ----------------
        step("Collection.deleteCollection") {
            val created = collectionUuid ?: error("无 collectionUuid")
            val r = CollectionApi.deleteCollection(created)
            check(r is HttpResult.Success) { "deleteCollection 非 Success: $r" }
        }
        step("Tags.deleteTag") {
            val created = tagUuid ?: error("无 tagUuid")
            val r = TagsApi.deleteTag(created)
            check(r is HttpResult.Success) { "deleteTag 非 Success: $r" }
        }
        step("Person.deletePerson") {
            val r = PersonApi.deletePerson(personUuid)
            check(r is HttpResult.Success) { "deletePerson 非 Success: $r" }
        }

        // 收尾释放：掐断 Room InvalidationTracker 刷新任务跨测试类残留
        TestSession.closeHolderDb()
        println("==================================================================================================")
        println("EntityApiTest: 汇总 —— 共 ${lines.size} 步，失败 $failed")
        lines.forEach { println("  $it") }
        println("==================================================================================================")
        if (failed > 0) {
            throw AssertionError("EntityApiTest 存在 $failed 项失败，详见上方输出")
        }
    }
}
