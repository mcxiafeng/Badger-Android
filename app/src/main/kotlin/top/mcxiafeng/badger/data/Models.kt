package top.mcxiafeng.badger.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/**
 * 联系人实体
 *
 * @property id 联系人唯一ID，自增主键
 * @property name 联系人姓名
 * @property avatarUrl 头像URL，可选
 * @property note 备注，可选
 * @property createTime 创建时间（毫秒时间戳）
 * @property updateTime 最后更新时间（毫秒时间戳）
 */
@Entity(tableName = "contacts")
@TypeConverters(Converters::class)
@Immutable
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null,
    val avatarPath: String? = null,
    val note: String? = null,
    val pinyinInitial: String = "",
    val platforms: Map<String, PlatformEntry>? = null,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

/**
 * 联系人字段定义（系统预置字段）
 *
 * 定义了联系人可以拥有的各种联系方式/社交账号类型，如手机、邮箱、微信等。
 * 每个字段通过 [fieldKey] 唯一标识，在扫描识别时用于映射提取到的信息。
 *
 * @property id 字段定义ID，自增主键
 * @property fieldName 字段显示名称（如"手机"、"邮箱"）
 * @property fieldKey 字段标识键（如"phone"、"email"），用于程序内部引用
 * @property icon 图标标识，可选
 * @property sortOrder 排序权重，数值越小越靠前
 * @property isSystem 是否为系统预置字段，系统字段不可删除
 * @property isEnabled 是否启用，禁用后不会在界面上显示
 * @property createTime 创建时间
 */
@Entity(tableName = "contact_fields")
@Immutable
data class ContactField(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fieldName: String,
    val fieldKey: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isEnabled: Boolean = true,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 自定义字段定义
 *
 * 用户可以创建自定义字段来扩展联系人的信息维度，
 * 比如添加"公司"、"职位"等系统未预置的字段。
 *
 * @property id 自定义字段ID，自增主键
 * @property fieldName 字段显示名称
 * @property fieldType 字段类型（如"text"、"number"、"date"等）
 * @property options 可选项的 JSON 字符串（用于下拉选择类型的字段）
 * @property sortOrder 排序权重
 * @property isEnabled 是否启用
 * @property createTime 创建时间
 */
@Entity(tableName = "custom_fields")
@Immutable
data class CustomField(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fieldName: String,
    val fieldType: String,
    val options: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 联系人字段值（关联表）
 *
 * 存储每个联系人的具体字段值。一个联系人可以对应多个字段值，
 * 每个值要么关联到系统预置字段 [ContactField]，要么关联到自定义字段 [CustomField]。
 *
 * 级联删除：当关联的联系人、系统字段或自定义字段被删除时，对应的字段值也会自动删除。
 * 同一联系人对同一字段可以存储多个值（如多个手机号、多个邮箱）。
 *
 * @property id 记录ID，自增主键
 * @property contactId 所属联系人ID
 * @property fieldId 关联的系统预置字段ID，与 [customFieldId] 互斥
 * @property customFieldId 关联的自定义字段ID，与 [fieldId] 互斥
 * @property value 字段值内容
 * @property createTime 创建时间
 * @property updateTime 更新时间
 */
@Entity(
    tableName = "contact_field_values",
    foreignKeys = [
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContactField::class,
            parentColumns = ["id"],
            childColumns = ["fieldId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomField::class,
            parentColumns = ["id"],
            childColumns = ["customFieldId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contactId", "fieldId"]),
        Index(value = ["contactId", "customFieldId"]),
        Index(value = ["contactId"])
    ]
)
@TypeConverters(Converters::class)
@Immutable
data class ContactFieldValue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val fieldId: Long? = null,
    val customFieldId: Long? = null,
    val value: String,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

/**
 * 联系人及其所有字段值的组合数据类
 *
 * 用于在界面上一次性展示联系人的完整信息。
 *
 * @property contact 联系人基本信息
 * @property fieldValues 经过排序的字段值列表（包含显示名称、图标等元信息）
 */
@Immutable
data class ContactWithFields(
    val contact: Contact,
    val fieldValues: List<ContactFieldDisplay>
)

/**
 * 联系人字段值的展示数据类
 *
 * 整合了字段定义和具体值，方便 UI 层直接渲染。
 * 支持同一字段类型的多条记录（如多个手机号）。
 *
 * @property valueId 字段值记录的数据库 ID（用于编辑/删除时精确定位）
 * @property fieldId 系统预置字段ID
 * @property customFieldId 自定义字段ID
 * @property fieldName 字段显示名称
 * @property fieldKey 系统字段的标识键
 * @property icon 字段图标
 * @property fieldType 自定义字段的类型
 * @property value 字段的具体值
 * @property sortOrder 排序权重
 */
@Immutable
data class ContactFieldDisplay(
    val valueId: Long,
    val fieldId: Long?,
    val customFieldId: Long?,
    val fieldName: String,
    val fieldKey: String?,
    val icon: String?,
    val fieldType: String?,
    val value: String,
    val sortOrder: Int
)

/**
 * 名片夹/合集
 *
 * 用于对联系人进行分组管理，比如"工作名片"、"个人社交"等。
 *
 * @property id 名片夹ID，自增主键
 * @property name 名片夹名称
 * @property description 名片夹描述，可选
 * @property createTime 创建时间
 */
@Entity(tableName = "card_collections")
@Immutable
data class CardCollection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val backgroundImagePath: String? = null,
    val dominantColor: Long? = null,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 扫描结果记录
 *
 * 记录每次扫描识别的原始数据及其关联的联系人和名片夹。
 * 作为联系人和名片夹之间的多对多关联表使用。
 * 同一联系人在同一名片夹可以有多条记录（不同样式/主色调）。
 *
 * @property id 自增主键
 * @property contactId 关联的联系人ID
 * @property collectionId 关联的名片夹ID
 * @property scannedTime 扫描时间
 * @property sourceType 扫描来源类型："scan"（二维码扫描）或 "photo"（拍照识别）
 * @property styleColor 名片主色调（ARGB Long），自动识别名片背景色时保存
 * @property rawData 原始扫描数据
 * @property ocrText OCR 文字识别结果
 * @property qrCodeContent 二维码内容
 * @property confidence 识别置信度（0-1）
 */
@Entity(
    tableName = "scan_results",
    foreignKeys = [
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CardCollection::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contactId", "collectionId"]),
        Index(value = ["collectionId"]),
        Index(value = ["contactId"])
    ]
)
@Immutable
data class ScanResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val collectionId: Long,
    val scannedTime: Long = System.currentTimeMillis(),
    val sourceType: String,
    val styleColor: Long? = null,
    val rawData: String? = null,
    val ocrText: String? = null,
    val qrCodeContent: String? = null,
    val confidence: Float = 0f
)

/**
 * 重复联系人检查结果
 *
 * @property isDuplicate 是否判定为重复（相似度 >= 1.0 时为重复）
 * @property existingContact 已存在的相似联系人
 * @property similarityScore 相似度评分（范围 0~2，含名字相似度加权）
 * @property matchFields 匹配到的字段名称列表（如"手机"、"邮箱"）
 */
@Immutable
data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val existingContact: Contact?,
    val similarityScore: Float,
    val matchFields: List<String>
)

/**
 * 字段合并选择
 */
enum class MergeChoice {
    /** 保留已有值，不做任何操作 */
    KEEP,
    /** 替换已有值为新值 */
    REPLACE,
    /** 追加新值（同一字段多个值） */
    APPEND
}

/**
 * 字段合并条目：合并对话框中逐字段对比
 */
@Immutable
data class FieldMergeEntry(
    val fieldKey: String,
    val fieldName: String,
    val existingValue: String?,
    val newValue: String?,
    val selectedValue: MergeChoice = MergeChoice.APPEND
)

/**
 * 社交平台条目
 *
 * 存储每个社交平台的信息：平台昵称、跳转链接 + 平台ID/账号。
 * jumpLink 是必须的（用于生成二维码），value 是可选的（从链接自动提取，提取失败时手动填写）。
 * displayName 是该平台的昵称（如 QQ昵称、B站昵称）。
 *
 * @property displayName 平台昵称（可选，如"小明"、"Up主名"）
 * @property jumpLink 跳转链接（必填，用于生成二维码和自动解析 ID）
 * @property value 平台ID/账号（可选，如 QQ号、UID、微信号等；优先从 jumpLink 自动提取）
 */
@Immutable
data class PlatformEntry(
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("jumpLink") val jumpLink: String = "",
    @SerializedName("originalLink") val originalLink: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

@Entity(
    tableName = "contact_platforms",
    foreignKeys = [ForeignKey(
        entity = Contact::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["platformKey"]),
        Index(value = ["contactId", "platformKey"], unique = true)
    ]
)
@Immutable
data class ContactPlatform(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val platformKey: String,
    val value: String? = null,
    val displayName: String? = null,
    val jumpLink: String = "",
    val originalLink: String? = null,
    val avatarUrl: String? = null
)

data class LetterCount(val letter: String, val count: Int)

/**
 * 用户个人资料（"我的名片"）
 *
 * 存储当前用户自己的社交信息，用于扩列页展示和二维码生成。
 * 整个应用只有一个 UserProfile 记录（id = 1）。
 *
 * 所有社交平台统一存储在 [platforms] 中，不再区分预置平台和自定义平台。
 *
 * @property id 固定为 1L，单例记录
 * @property name 全局昵称/姓名
 * @property avatarPath 本地头像文件路径（null 时使用首字母占位）
 * @property cardImagePath 名片背景图片本地路径（null 时显示蓝色占位）
 * @property bio 全局个人简介
 * @property platforms 社交平台信息，JSON 格式存储 {"平台名": PlatformEntry, ...}
 * @property defaultPlatform 默认跳转链接的平台名称（用于名片页二维码）
 * @property updateTime 最后更新时间
 */
@Entity(tableName = "user_profile")
@TypeConverters(Converters::class)
@Immutable
data class UserProfile(
    @PrimaryKey
    val id: Long = 1L,
    val name: String = "",
    val avatarPath: String? = null,
    val cardImagePath: String? = null,
    val bio: String? = null,
    val platforms: Map<String, PlatformEntry>? = null,
    val defaultPlatform: String? = null,
    val updateTime: Long = System.currentTimeMillis()
)

@Fts4(contentEntity = Contact::class)
@Entity(tableName = "contacts_fts")
@Immutable
data class ContactFts(
    val name: String,
    val note: String?
)
