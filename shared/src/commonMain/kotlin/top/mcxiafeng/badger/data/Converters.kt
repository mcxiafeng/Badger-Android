package top.mcxiafeng.badger.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncType
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
    fun fromJson(value: JsonObject): String = value.toString()

    @TypeConverter
    fun toJson(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject

    @TypeConverter
    fun fromStringMap(map: Map<String, String>): String = Json.encodeToString(map)

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromUuidList(value: List<Uuid>): String = Json.encodeToString(value)

    @TypeConverter
    fun toUuidList(value: String): List<Uuid> = Json.decodeFromString(value)
}
