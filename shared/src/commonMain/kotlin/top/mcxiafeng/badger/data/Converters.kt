package top.mcxiafeng.badger.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.mcxiafeng.badger.data.user.entity.Contact
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncType
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.uuid.Uuid

class Converters {

    @TypeConverter
    fun fromEntityKind(value: EntityKind): String = value.name

    @TypeConverter
    fun toEntityKind(value: String): EntityKind = EntityKind.valueOf(value)

    @TypeConverter
    fun fromSyncType(value: SyncType): String = value.name

    @TypeConverter
    fun toSyncType(value: String): SyncType = SyncType.valueOf(value)

    @TypeConverter
    fun fromUuid(value: Uuid): String = value.toString()

    @TypeConverter
    fun toUuid(value: String): Uuid = Uuid.parse(value)

    @TypeConverter
    fun fromJson(value: JsonObject): String = Json.encodeToString(value)
    @TypeConverter
    fun toContactList(value: String): List<Contact> {
        val jsonArray = Json.parseToJsonElement(value).jsonArray
        val list = mutableListOf<Contact>()
        for (element in jsonArray) {
            val elementObject = element.jsonObject
            list.add(Contact("${elementObject["platformName"]?.jsonPrimitive?.contentOrNull}","${elementObject["sourceUrl"]?.jsonPrimitive?.contentOrNull}"))
        }
        return list
    }

    @TypeConverter
    fun toContactJson(value: List<Contact>): String {
        return Json.encodeToJsonElement(value).jsonArray.toString()
    }

    @TypeConverter
    fun parseJson(value: String): JsonObject {
        return Json.parseToJsonElement(value).jsonObject
    }


    @TypeConverter
    fun fromStringMap(map: Map<String, String>): String = Json.encodeToString(map)

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromUuidList(value: List<Uuid>): String = Json.encodeToString(value)

    @TypeConverter
    fun toUuidList(value: String): List<Uuid> = Json.decodeFromString(value)

    @TypeConverter
    fun fromJsonObjectList(list: List<JsonObject>?): String {
        if (list.isNullOrEmpty()) return "[]"
        return Json.encodeToString(list)
    }

    @TypeConverter
    fun toJsonObjectList(value: String): List<JsonObject>? {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }


}
