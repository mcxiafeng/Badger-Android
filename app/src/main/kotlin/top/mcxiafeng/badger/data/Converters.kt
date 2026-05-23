package top.mcxiafeng.badger.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room 数据库类型转换器
 *
 * 将数据库不支持的数据类型（如 Date、Map）与数据库支持的类型（Long、String）之间互相转换。
 */
class Converters {
    /** Map<String, PlatformEntry> → JSON 字符串 */
    @TypeConverter
    fun fromPlatformsMap(map: Map<String, PlatformEntry>?): String? {
        return map?.let { Gson().toJson(it) }
    }

    /** JSON 字符串 → Map<String, PlatformEntry> */
    @TypeConverter
    fun toPlatformsMap(json: String?): Map<String, PlatformEntry>? {
        return json?.let {
            Gson().fromJson(it, object : TypeToken<Map<String, PlatformEntry>>() {}.type)
        }
    }
}
