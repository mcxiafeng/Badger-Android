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

class JsonMigrationParityTest {

    

    @Test
    fun `login response parses identically via gson and kotlinx`() {
        val body = """
            {"code":200,"message":"ok","data":{
              "token":"tok-123",
              "user":{"uuid":"u1","name":"alice","displayName":"Alice","email":null,
                      "isAdmin":true,"profile":{"sex":"female"},"lastLogin":"2026-09-01 10:00:00"}
            }}
        """.trimIndent()

        
        val gsonRoot = JsonParser.parseString(body).asJsonObject
        val gsonData = gsonRoot.getAsJsonObject("data")
        val gsonUser = gsonData.getAsJsonObject("user")
        val gsonToken = gsonData.get("token").takeIf { !it.isJsonNull }?.asString.orEmpty()
        val gsonEmail = gsonUser.get("email")?.takeIf { !it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
        val gsonIsAdmin = gsonUser.get("isAdmin")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val gsonProfile = gsonUser.getAsJsonObject("profile")

        
        val kxData = (BadgerJson.parseToJsonElement(body) as KxJsonObject)["data"] as KxJsonObject
        val parsed = AuthResponse.ofLogin(kxData)

        assertThat(parsed.token).isEqualTo(gsonToken)
        assertThat(parsed.user).isNotNull()
        assertThat(parsed.user!!.email).isEqualTo(gsonEmail) 
        assertThat(parsed.user!!.isAdmin).isEqualTo(gsonIsAdmin)
        assertThat(parsed.user!!.profile).isNotNull()
        
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
        
        val gsonName = gsonObj.get("name")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }.orEmpty()
        val gsonCreateTime = gsonObj.get("createTime")?.takeIf { !it.isJsonNull }?.asString
        val gsonSelf = gsonObj.get("self")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val gsonContactMap = gsonObj.getAsJsonObject("profile").getAsJsonObject("contactMap")
            .entrySet()!!.associate { (k, v) -> k to (if (v.isJsonNull) "" else v.asString) }

        val kxObj = BadgerJson.parseToJsonElement(body) as KxJsonObject
        val dto = PersonDto.from(kxObj)

        assertThat(dto.name).isEqualTo(gsonName) 
        assertThat(dto.createTime).isEqualTo(gsonCreateTime) 
        assertThat(dto.self).isEqualTo(gsonSelf)
        assertThat(dto.profile!!.contactMap).isEqualTo(gsonContactMap) 
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
        
        val gsonRoot = JsonParser.parseString("""{"code":400.0,"message":"bad"}""")
        val gsonCode = gsonRoot.asJsonObject.get("code")?.takeIf { !it.isJsonNull }?.asInt
        val kxRoot = BadgerJson.parseToJsonElement("""{"code":400.0,"message":"bad"}""") as KxJsonObject
        val kxCode = intOr(kxRoot["code"], 0)
        assertThat(kxCode).isEqualTo(gsonCode) 
        assertThat(kxCode).isEqualTo(400)
    }

    

    @Test
    fun `legacy gson-encoded platformsJson decodes via kotlinx`() {
        
        val legacyJson = """
            {"qq":{"displayName":"QQ","jumpLink":"https://qq.com/123","originalLink":null,"value":"123","avatarUrl":null},
             "wechat":{"displayName":"微信","jumpLink":"","originalLink":null,"value":"wxid_x","avatarUrl":null}}
        """.trimIndent()
        val map = ContactMapper.decodePlatformsMap(legacyJson)
        Truth.assertThat(map).isNotNull()
        Truth.assertThat(map!!.getValue("qq").value).isEqualTo("123")
        Truth.assertThat(map.getValue("qq").displayName).isEqualTo("QQ")
        Truth.assertThat(map.getValue("wechat").jumpLink).isEmpty()
        
        val reencoded = ContactMapper.encodePlatformsMap(map)
        Truth.assertThat(ContactMapper.decodePlatformsMap(reencoded)).isEqualTo(map)
    }

    @Test
    fun `gson-built outbox payload string parses via BadgerJson`() {
        
        val gsonPayload = JsonObject().apply {
            addProperty("name", "张三")
            add("profile", JsonObject().apply { addProperty("description", "bio") })
        }.toString()

        val kxPayload = BadgerJson.parseToJsonElement(gsonPayload) as KxJsonObject
        assertThat((kxPayload["name"] as JsonPrimitive).content).isEqualTo("张三")
        val profile = kxPayload["profile"] as KxJsonObject
        assertThat((profile["description"] as JsonPrimitive).content).isEqualTo("bio")
    }

    

    @Test
    fun `payload field merge semantics preserved on kotlinx`() {
        
        
        val existing = buildJsonObject {
            put("name", "old-name")
            put("profile", buildJsonObject { put("description", "old-bio") })
        }
        val incoming = buildJsonObject { put("name", "new-name") }

        
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
        
        val gsonUuid = gsonObj.get("uuid")
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        assertThat(gsonUuid).isNull()
        
        val kxObj = BadgerJson.parseToJsonElement(row) as KxJsonObject
        assertThat(UserNotification.parse(kxObj)).isNull()

        
        val okRow = """{"uuid":"n1","title":"hi","read":true}"""
        val parsed = UserNotification.parse(BadgerJson.parseToJsonElement(okRow) as KxJsonObject)
        assertThat(parsed!!.read).isTrue()
        assertThat(parsed.body).isEmpty() 
    }

    

    
    private fun normalize(json: String): String =
        JsonParser.parseString(json).toString()
}
