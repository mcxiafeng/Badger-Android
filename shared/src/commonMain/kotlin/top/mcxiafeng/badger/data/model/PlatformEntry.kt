package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 社交平台条目（共享 JSON shape）。
 *
 * 该类型用于 V2 cache 中 `platformsJson` 的序列化/反序列化（[K04] Gson → kotlinx.serialization）。
 * 属性名即 JSON wire format 字段名（原 Gson @SerializedName 与属性名一致，存储兼容无需 @SerialName）。
 */
@Immutable
@Serializable
data class PlatformEntry(
    val displayName: String? = null,
    val jumpLink: String = "",
    val originalLink: String? = null,
    val value: String? = null,
    val avatarUrl: String? = null
)
