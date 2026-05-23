package top.mcxiafeng.badger.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldValue
import android.widget.Toast
import top.mcxiafeng.badger.data.ContactRepository

// ===== JSON 数据模型 =====

data class BadgerExport(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("app") val app: String = "badger",
    @SerializedName("exportTime") val exportTime: Long = System.currentTimeMillis(),
    @SerializedName("collections") val collections: List<CollectionExport>
)

data class CollectionExport(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("contacts") val contacts: List<ContactExport>
)

data class ContactExport(
    @SerializedName("name") val name: String,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("isFavorite") val isFavorite: Boolean = false,
    @SerializedName("fields") val fields: List<FieldExport>
)

data class FieldExport(
    @SerializedName("fieldKey") val fieldKey: String,
    @SerializedName("value") val value: String
)

data class ImportResult(
    val importedCollections: Int,
    val importedContacts: Int,
    val skippedCollections: Int
)

// ===== 导出/导入逻辑 =====

private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

/**
 * 导出指定名片夹为 JSON
 */
suspend fun exportToJson(repository: ContactRepository, collectionIds: List<Long>): String {
    val allFields = repository.getAllFieldsOnce()
    val fieldMap = allFields.associateBy { it.id }

    val collections = collectionIds.mapNotNull { id ->
        val collection = repository.getCollectionById(id) ?: return@mapNotNull null
        val contacts = repository.getContactsByCollectionOnce(id)
        val contactExports = contacts.map { contact ->
            val fieldValues = repository.getFieldValuesByContactOnce(contact.id)
            val fields = fieldValues.mapNotNull { fv ->
                val key = fv.fieldId?.let { fieldMap[it]?.fieldKey } ?: return@mapNotNull null
                FieldExport(fieldKey = key, value = fv.value)
            }
            ContactExport(
                name = contact.name,
                avatarUrl = contact.avatarUrl,
                note = contact.note,
                isFavorite = contact.isFavorite,
                fields = fields
            )
        }
        CollectionExport(
            name = collection.name,
            description = collection.description,
            contacts = contactExports
        )
    }

    return gson.toJson(BadgerExport(collections = collections))
}

/**
 * 从 JSON 导入名片夹
 */
suspend fun importFromJson(repository: ContactRepository, json: String): ImportResult {
    val export = try {
        gson.fromJson(json, BadgerExport::class.java)
    } catch (_: Exception) {
        throw IllegalArgumentException("无效的 JSON 格式")
    }

    if (export.version != 1) {
        throw IllegalArgumentException("不支持的版本: ${export.version}")
    }

    val allFields = repository.getAllFieldsOnce()
    val fieldKeyMap = allFields.associateBy { it.fieldKey }

    var importedCollections = 0
    var importedContacts = 0
    var skippedCollections = 0

    // 获取已有名片夹名称，同名跳过
    val existingNames = repository.getAllCollectionsOnce().map { it.name }.toSet()

    for (collectionExport in export.collections) {
        if (collectionExport.name in existingNames) {
            skippedCollections++
            continue
        }

        val collectionId = repository.insertCollection(
            CardCollection(
                name = collectionExport.name,
                description = collectionExport.description
            )
        )

        for (contactExport in collectionExport.contacts) {
            val contactId = repository.insertContact(
                Contact(
                    name = contactExport.name,
                    avatarUrl = contactExport.avatarUrl,
                    note = contactExport.note,
                    isFavorite = contactExport.isFavorite
                )
            )

            for (fieldExport in contactExport.fields) {
                val field = fieldKeyMap[fieldExport.fieldKey] ?: continue
                repository.insertFieldValue(
                    ContactFieldValue(
                        contactId = contactId,
                        fieldId = field.id,
                        value = fieldExport.value
                    )
                )
            }

            repository.addContactToCollection(contactId, collectionId, "import")
            importedContacts++
        }

        importedCollections++
    }

    return ImportResult(importedCollections, importedContacts, skippedCollections)
}

/**
 * 从剪贴板导入
 */
suspend fun importFromClipboard(context: android.content.Context, repository: ContactRepository) {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
    if (text.isNullOrBlank()) throw IllegalArgumentException("剪贴板为空")
    val result = importFromJson(repository, text)
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        Toast.makeText(context, "导入完成：${result.importedCollections}个名片夹，${result.importedContacts}个联系人", Toast.LENGTH_LONG).show()
    }
}

/**
 * 从 JSON 导入联系人到指定名片夹（不创建新名片夹）
 */
suspend fun importContactsToCollection(repository: ContactRepository, collectionId: Long, json: String): Int {
    val export = try {
        gson.fromJson(json, BadgerExport::class.java)
    } catch (_: Exception) {
        throw IllegalArgumentException("无效的 JSON 格式")
    }
    if (export.version != 1) throw IllegalArgumentException("不支持的版本: ${export.version}")

    val allFields = repository.getAllFieldsOnce()
    val fieldKeyMap = allFields.associateBy { it.fieldKey }

    var count = 0
    for (collectionExport in export.collections) {
        for (contactExport in collectionExport.contacts) {
            val contactId = repository.insertContact(
                Contact(
                    name = contactExport.name,
                    avatarUrl = contactExport.avatarUrl,
                    note = contactExport.note,
                    isFavorite = contactExport.isFavorite
                )
            )
            for (fieldExport in contactExport.fields) {
                val field = fieldKeyMap[fieldExport.fieldKey] ?: continue
                repository.insertFieldValue(
                    ContactFieldValue(
                        contactId = contactId,
                        fieldId = field.id,
                        value = fieldExport.value
                    )
                )
            }
            repository.addContactToCollection(contactId, collectionId, "import")
            count++
        }
    }
    return count
}

/**
 * 解析 JSON 用于预览（不执行导入）
 */
fun previewImport(json: String): Pair<Int, Int> {
    val export = try {
        gson.fromJson(json, BadgerExport::class.java)
    } catch (_: Exception) {
        return 0 to 0
    }
    val contacts = export.collections.sumOf { it.contacts.size }
    return export.collections.size to contacts
}
