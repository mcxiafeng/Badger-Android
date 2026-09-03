package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * 社交平台条目（共享 JSON shape）。
 *
 * 该类型用于 V2 cache 中 `platformsJson` 的 Gson 序列化/反序列化。
 * 字段名是稳定的 JSON wire format，不代表需要保留 V1 HTTP API。
 */
@Immutable
data class PlatformEntry(
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("jumpLink") val jumpLink: String = "",
    @SerializedName("originalLink") val originalLink: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)
