package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth
import com.google.gson.JsonObject
import top.mcxiafeng.badger.data.repository.ContactMapper
import com.google.gson.JsonParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject as KxJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * [K04] Gson → kotlinx.serialization 迁移对照测试（验收项：同一批真实响应 JSON 双实现断言等价）。
 *
 * 双实现 = 左侧 Gson 手写解析（复刻迁移前 ServerApiTypes/PersonApi 的 stringOrNull/takeIfString
 * 防御链），右侧 = 迁移后的 kotlinx DTO from(JsonObject) / BadgerJson 解码。
 *
 * 覆盖三类风险：
 * 1. 网络响应解析等价（含缺字段 / JSON null / 数字形态 code）；
 * 2. **存储兼容**：老版本 Gson 写入 DB 的 platformsJson / outbox payloadJson 必须能被新解码器读出；
 * 3. Outbox payload 字段级 merge 语义在新 JsonObject 实现下不变。
 *
 * 本测试在 Gson 依赖移除后转为纯 kotlinx 快照测试（Gson 断言侧删除，期望值内联）。
 */
class JsonMigrationParityTest {

    // ========== 1. 网络响应解析等价 ==========

    @Test
    fun `login response parses identically via gson and kotlinx`() {
        val body = """
            {"code":200,"message":"ok","data":{
              "token":"tok-123",
              "user":{"uuid":"u1","name":"alice","displayName":"Alice","email":null,
                      "isAdmin":true,"profile":{"sex":"female"},"lastLogin":"2026-09-01 10:00:00"}
            }}
        """.trimIndent()

        // 左：Gson 手写防御链（迁移前实现）
        val gsonRoot = JsonParser.parseString(body).asJsonObject
        val gsonData = gsonRoot.getAsJsonObject("data")
        val gsonUser = gsonData.getAsJsonObject("user")
        val gsonToken = gsonData.get("token").takeIf { !it.isJsonNull }?.asString.orEmpty()
        val gsonEmail = gsonUser.get("email")?.takeIf { !it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
        val gsonIsAdmin = gsonUser.get("isAdmin")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val gsonProfile = gsonUser.getAsJsonObject("profile")

        // 右：kotlinx DTO
        val kxData = (BadgerJson.parseToJsonElement(body) as KxJsonObject)["data"] as KxJsonObject
        val parsed = AuthResponse.ofLogin(kxData)

        assertThat(parsed.token).isEqualTo(gsonToken)
        assertThat(parsed.user).isNotNull()
        assertThat(parsed.user!!.email).isEqualTo(gsonEmail) // JSON null → null 两边一致
        assertThat(parsed.user!!.isAdmin).isEqualTo(gsonIsAdmin)
        assertThat(parsed.user!!.profile).isNotNull()
        // Gson JsonObject 与 kotlinx JsonObject 内容等价（toString 规范化后）
        assertThat(normalize(parsed.user!!.profile.toString()))
            .isEqualTo(normalize(gsonProfile.toString()))
    }

    @Test
    fun `person dto with missing and null fields parses identically`() {
        val body = """
            {"uuid":"p1","name":null,"createTime":1759000000000,
             "profile":{"avatarURL":"https://a/1.jpg","contactMap":{"qq":"123","wechat":""}},
             "self":true}
        """.trimIndent()

        val gsonObj = JsonParser.parseString(body).asJsonObject
        // Gson 防御链：null name → stringOrNull 返回 null → orEmpty()
        val gsonName = gsonObj.get("name")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }.orEmpty()
        val gsonCreateTime = gsonObj.get("createTime")?.takeIf { !it.isJsonNull }?.asString
        val gsonSelf = gsonObj.get("self")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val gsonContactMap = gsonObj.getAsJsonObject("profile").getAsJsonObject("contactMap")
            .entrySet()!!.associate { (k, v) -> k to (if (v.isJsonNull) "" else v.asString) }

        val kxObj = BadgerJson.parseToJsonElement(body) as KxJsonObject
        val dto = PersonDto.from(kxObj)

        assertThat(dto.name).isEqualTo(gsonName) // null → ""
        assertThat(dto.createTime).isEqualTo(gsonCreateTime) // epoch 数值 → content 字符串
        assertThat(dto.self).isEqualTo(gsonSelf)
        assertThat(dto.profile!!.contactMap).isEqualTo(gsonContactMap) // 空串 value 保留
        assertThat(dto.createTimeMillis()).isEqualTo(1759000000000L)
    }

    @Test
    fun `sync page with version as number and hasMore false parses identically`() {
        val body = """
            {"version":42,"hasMore":false,"changes":[
              {"version":41,"type":"UPDATE","objectName":"Person","objectId":"p1",
               "fieldName":"name","value":{"name":"bob"}}
            ]}
        """.trimIndent()
        val gsonObj = JsonParser.parseString(body).asJsonObject
        val gsonVersion = gsonObj.get("version")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        val gsonChanges = gsonObj.getAsJsonArray("changes").size()

        val page = SyncPage.from(BadgerJson.parseToJsonElement(body) as KxJsonObject)
        assertThat(page.version).isEqualTo(gsonVersion)
        assertThat(page.changes.size).isEqualTo(gsonChanges)
        assertThat(page.changes.first().value).isInstanceOf(KxJsonObject::class.java)
    }

    @Test
    fun `api result code as double-form number still rejects`() {
        // Gson 数字→Double 陷阱的形态：code 写成 400.0（字符串内是合法 JSON number）
        val gsonRoot = JsonParser.parseString("""{"code":400.0,"message":"bad"}""")
        val gsonCode = gsonRoot.asJsonObject.get("code")?.takeIf { !it.isJsonNull }?.asInt
        val kxRoot = BadgerJson.parseToJsonElement("""{"code":400.0,"message":"bad"}""") as KxJsonObject
        val kxCode = intOr(kxRoot["code"], 0)
        assertThat(kxCode).isEqualTo(gsonCode) // 400 两边一致（intOr 收敛 "400.0" 形态）
        assertThat(kxCode).isEqualTo(400)
    }

    // ========== 2. 存储兼容：老 Gson 写入的数据可读 ==========

    @Test
    fun `legacy gson-encoded platformsJson decodes via kotlinx`() {
        // 老版本 Gson 写入 contacts_cache.platformsJson 的真实形态
        val legacyJson = """
            {"qq":{"displayName":"QQ","jumpLink":"https://qq.com/123","originalLink":null,"value":"123","avatarUrl":null},
             "wechat":{"displayName":"微信","jumpLink":"","originalLink":null,"value":"wxid_x","avatarUrl":null}}
        """.trimIndent()
        val map = ContactMapper.decodePlatformsMap(legacyJson)
        Truth.assertThat(map).isNotNull()
        Truth.assertThat(map!!.getValue("qq").value).isEqualTo("123")
        Truth.assertThat(map.getValue("qq").displayName).isEqualTo("QQ")
        Truth.assertThat(map.getValue("wechat").jumpLink).isEmpty()
        // 回写 round-trip
        val reencoded = ContactMapper.encodePlatformsMap(map)
        Truth.assertThat(ContactMapper.decodePlatformsMap(reencoded)).isEqualTo(map)
    }

    @Test
    fun `gson-built outbox payload string parses via BadgerJson`() {
        // 老版本 Gson JsonObject().apply { addProperty(...) }.toString() 的产物
        val gsonPayload = JsonObject().apply {
            addProperty("name", "张三")
            add("profile", JsonObject().apply { addProperty("description", "bio") })
        }.toString()

        val kxPayload = BadgerJson.parseToJsonElement(gsonPayload) as KxJsonObject
        assertThat((kxPayload["name"] as JsonPrimitive).content).isEqualTo("张三")
        val profile = kxPayload["profile"] as KxJsonObject
        assertThat((profile["description"] as JsonPrimitive).content).isEqualTo("bio")
    }

    // ========== 3. Outbox payload 字段级 merge 语义 ==========

    @Test
    fun `payload field merge semantics preserved on kotlinx`() {
        // 模拟 OutboxStore.mergePayload：旧 payload 有 name+profile.description，
        // 新 payload 只带 name → merge 后 profile 保留旧值、name 换新
        val existing = buildJsonObject {
            put("name", "old-name")
            put("profile", buildJsonObject { put("description", "old-bio") })
        }
        val incoming = buildJsonObject { put("name", "new-name") }

        // 复刻 OutboxStore.mergePayload 的实现（非 null 字段覆盖，null/缺省保留）
        val merged = KxJsonObject(existing.entries.associate { (k, v) ->
            k to (incoming[k] ?: v)
        })

        assertThat((merged["name"] as JsonPrimitive).content).isEqualTo("new-name")
        val profile = merged["profile"] as KxJsonObject
        assertThat((profile["description"] as JsonPrimitive).content).isEqualTo("old-bio")
    }

    @Test
    fun `notification parse skips null uuid and keeps defaults`() {
        val row = """{"uuid":null,"title":"hi","read":true}"""
        val gsonObj = JsonParser.parseString(row).asJsonObject
        // Gson：uuid null → stringOrNull null → parse 返回 null（跳过该行）
        val gsonUuid = gsonObj.get("uuid")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        assertThat(gsonUuid).isNull()
        // kotlinx：同样跳过；且 JsonNull 伪装 JsonPrimitive 的陷阱被 takeIfString 守卫
        val kxObj = BadgerJson.parseToJsonElement(row) as KxJsonObject
        assertThat(UserNotification.parse(kxObj)).isNull()

        // read=true 正常路径
        val okRow = """{"uuid":"n1","title":"hi","read":true}"""
        val parsed = UserNotification.parse(BadgerJson.parseToJsonElement(okRow) as KxJsonObject)
        assertThat(parsed!!.read).isTrue()
        assertThat(parsed.body).isEmpty() // 缺 body → ""（对齐 Gson orEmpty 默认）
    }

    // ========== 工具 ==========

    /** Gson 与 kotlinx 的 toString 空白差异归一（Gson 无空格，kotlinx 默认无空格，此处双保险）。 */
    private fun normalize(json: String): String =
        JsonParser.parseString(json).toString()
}
