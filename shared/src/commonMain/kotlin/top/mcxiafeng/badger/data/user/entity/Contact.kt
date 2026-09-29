package top.mcxiafeng.badger.data.user.entity

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject


@Serializable
data class Contact(
    val platformName: String,
    val sourceUrl: String,
)