package top.mcxiafeng.badger.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.PlatformEntry
import android.widget.Toast
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS

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
    @SerializedName("fields") val fields: List<FieldExport>,
    @SerializedName("platforms") val platforms: Map<String, PlatformEntryExport>? = null
)

data class FieldExport(
    @SerializedName("fieldKey") val fieldKey: String,
    @SerializedName("value") val value: String
)

data class PlatformEntryExport(
    @SerializedName("value") val value: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("jumpLink") val jumpLink: String? = null,
    @SerializedName("originalLink") val originalLink: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

data class ImportResult(
    val importedCollections: Int,
    val importedContacts: Int,
    val mergedContacts: Int
)

// ===== 导入冲突数据模型 =====

data class ImportConflict(
    val collectionExport: CollectionExport,
    val existingCollection: CardCollection?,
    val contactConflicts: List<ContactConflict>
)

data class ContactConflict(
    val contactExport: ContactExport,
    val existingContact: Contact?,
    val similarityScore: Float,
    val matchFields: List<String>
)

enum class CollectionConflictAction {
    MERGE, RENAME, SKIP
}

enum class ContactConflictAction {
    MERGE, NEW_STYLE, FORCE_IMPORT, SKIP
}

// ===== 导出/导入逻辑 =====

private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

/**
 * 导出指定名片夹为 JSON
 */
suspend fun exportToJson(repository: ContactRepository, collectionIds: List<Long>): String {
    Log.d("Tester", "exportToJson: collectionIds=$collectionIds")
    val allFields = repository.getAllFieldsOnce()
    val fieldMap = allFields.associateBy { it.id }

    val collections = collectionIds.mapNotNull { id ->
        val collection = repository.getCollectionById(id) ?: return@mapNotNull null
        val contacts = repository.getContactsByCollectionOnce(id)
        val contactExports = contacts.map { contact ->
            val fieldValues = repository.getFieldValuesByContactOnce(contact.id)
            val regularFields = fieldValues.mapNotNull { fv ->
                val key = fv.fieldId?.let { fieldMap[it]?.fieldKey } ?: return@mapNotNull null
                if (key in PLATFORM_FIELD_KEYS) return@mapNotNull null
                FieldExport(fieldKey = key, value = fv.value)
            }
            val platformExports = (contact.platforms ?: emptyMap()).mapValues { (_, entry) ->
                PlatformEntryExport(
                    value = entry.value,
                    displayName = entry.displayName,
                    jumpLink = entry.jumpLink,
                    originalLink = entry.originalLink,
                    avatarUrl = entry.avatarUrl
                )
            }
            ContactExport(
                name = contact.name,
                avatarUrl = contact.avatarUrl,
                note = contact.note,
                fields = regularFields,
                platforms = platformExports.ifEmpty { null }
            )
        }
        CollectionExport(
            name = collection.name,
            description = collection.description,
            contacts = contactExports
        )
    }

    val json = gson.toJson(BadgerExport(collections = collections))
    Log.d("Tester", "exportToJson: exported ${collections.size} collections, json length=${json.length}")
    return json
}

/**
 * 预扫描导入冲突（不执行任何数据库操作）
 */
suspend fun analyzeImportConflicts(repository: ContactRepository, json: String): List<ImportConflict> {
    Log.d("Tester", "analyzeImportConflicts: json length=${json.length}")
    val export = try {
        gson.fromJson(json, BadgerExport::class.java)
    } catch (_: Exception) {
        throw IllegalArgumentException("无效的 JSON 格式")
    }
    if (export.version != 1) throw IllegalArgumentException("不支持的版本: ${export.version}")

    val existingCollections = repository.getAllCollectionsOnce().associateBy { it.name }

    return export.collections.map { collectionExport ->
        val existingCollection = existingCollections[collectionExport.name]
        val contactConflicts = collectionExport.contacts.map { contactExport ->
            val fieldValues = contactExport.fields.associate { it.fieldKey to it.value }.toMutableMap()
            // 社交平台字段也参与重复检测
            contactExport.platforms?.forEach { (key, entry) ->
                if (!entry.value.isNullOrBlank()) fieldValues[key] = entry.value
            }
            Log.d("Tester", "analyzeImportConflicts: contact='${contactExport.name}', fieldValues=$fieldValues, platforms=${contactExport.platforms?.keys}")
            val dupResult = repository.checkDuplicate(contactExport.name, fieldValues, emptyMap())
            ContactConflict(
                contactExport = contactExport,
                existingContact = if (dupResult.isDuplicate) dupResult.existingContact else null,
                similarityScore = dupResult.similarityScore,
                matchFields = dupResult.matchFields
            )
        }
        ImportConflict(
            collectionExport = collectionExport,
            existingCollection = existingCollection,
            contactConflicts = contactConflicts
        )
    }.also {
        Log.d("Tester", "analyzeImportConflicts: ${it.size} collections, conflicts: ${it.count { c -> c.existingCollection != null || c.contactConflicts.any { cc -> cc.existingContact != null } }}")
    }
}

/**
 * 检查是否有任何冲突需要用户处理
 */
fun hasConflicts(conflicts: List<ImportConflict>): Boolean {
    return conflicts.any { it.existingCollection != null || it.contactConflicts.any { it.existingContact != null } }
}

/**
 * 执行导入（根据用户选择处理冲突）
 */
suspend fun executeImport(
    repository: ContactRepository,
    conflicts: List<ImportConflict>,
    collectionActions: Map<String, CollectionConflictAction>,
    contactActions: Map<String, ContactConflictAction>,
    renamedCollectionNames: Map<String, String>,
    contactAddStyle: Map<String, Boolean> = emptyMap()
): ImportResult {
    Log.d("Tester", "executeImport: ${conflicts.size} collections, collectionActions=$collectionActions, contactActions=$contactActions")
    val allFields = repository.getAllFieldsOnce()
    val fieldKeyMap = allFields.associateBy { it.fieldKey }

    var importedCollections = 0
    var importedContacts = 0
    var mergedContacts = 0

    for (conflict in conflicts) {
        val action = collectionActions[conflict.collectionExport.name] ?: CollectionConflictAction.MERGE
        if (action == CollectionConflictAction.SKIP) {
            Log.d("Tester", "executeImport: skipped collection '${conflict.collectionExport.name}'")
            continue
        }

        val collectionId = if (action == CollectionConflictAction.RENAME) {
            val newName = renamedCollectionNames[conflict.collectionExport.name] ?: "${conflict.collectionExport.name}_2"
            repository.insertCollection(
                CardCollection(name = newName, description = conflict.collectionExport.description)
            ).also { Log.d("Tester", "executeImport: renamed collection to '$newName', id=$it") }
        } else if (conflict.existingCollection != null) {
            Log.d("Tester", "executeImport: merging into existing collection '${conflict.collectionExport.name}', id=${conflict.existingCollection.id}")
            conflict.existingCollection.id
        } else {
            repository.insertCollection(
                CardCollection(name = conflict.collectionExport.name, description = conflict.collectionExport.description)
            ).also { Log.d("Tester", "executeImport: created new collection '${conflict.collectionExport.name}', id=$it") }
        }

        for (contactConflict in conflict.contactConflicts) {
            val contactAction = contactActions[contactConflict.contactExport.name]
                ?: if (contactConflict.existingContact != null) ContactConflictAction.MERGE else ContactConflictAction.FORCE_IMPORT

            when (contactAction) {
                ContactConflictAction.MERGE -> {
                    if (contactConflict.existingContact != null) {
                        val existing = contactConflict.existingContact
                        Log.d("Tester", "executeImport: merging contact '${contactConflict.contactExport.name}' into existing id=${existing.id}")
                        val freshContact = repository.getContactById(existing.id) ?: existing
                        val updatedAvatarUrl = if (freshContact.avatarUrl.isNullOrBlank() && !contactConflict.contactExport.avatarUrl.isNullOrBlank()) contactConflict.contactExport.avatarUrl else freshContact.avatarUrl
                        repository.updateContact(freshContact.copy(avatarUrl = updatedAvatarUrl, updateTime = System.currentTimeMillis()))
                        val existingFieldMap = repository.getFieldValueMapByContact(existing.id)
                        for (fieldExport in contactConflict.contactExport.fields) {
                            if (existingFieldMap.containsKey(fieldExport.fieldKey)) continue
                            val field = fieldKeyMap[fieldExport.fieldKey] ?: continue
                            repository.insertFieldValue(ContactFieldValue(contactId = existing.id, fieldId = field.id, value = fieldExport.value))
                        }
                        // 合并社交平台数据（已有 key 跳过，新 key 添加）
                        contactConflict.contactExport.platforms?.forEach { (key, entry) ->
                            if (freshContact.platforms?.containsKey(key) == true) return@forEach
                            repository.updateContactPlatform(existing.id, key, PlatformEntry(
                                value = entry.value,
                                displayName = entry.displayName,
                                jumpLink = entry.jumpLink ?: "",
                                originalLink = entry.originalLink,
                                avatarUrl = entry.avatarUrl
                            ))
                        }
                        if (!repository.existsContactInCollection(existing.id, collectionId)) {
                            repository.addContactToCollection(existing.id, collectionId, "import")
                        }
                        mergedContacts++
                    } else {
                        importedContacts += importAsNewContact(repository, contactConflict.contactExport, collectionId, fieldKeyMap)
                    }
                }
                ContactConflictAction.NEW_STYLE -> {
                    if (contactConflict.existingContact != null) {
                        Log.d("Tester", "executeImport: new-style for '${contactConflict.contactExport.name}', existing id=${contactConflict.existingContact.id}")
                        if (!repository.existsContactInCollection(contactConflict.existingContact.id, collectionId)) {
                            repository.addContactToCollection(contactConflict.existingContact.id, collectionId, "import")
                        }
                        val freshContact = repository.getContactById(contactConflict.existingContact.id) ?: contactConflict.existingContact
                        repository.updateContact(freshContact.copy(updateTime = System.currentTimeMillis()))
                        mergedContacts++
                    } else {
                        importedContacts += importAsNewContact(repository, contactConflict.contactExport, collectionId, fieldKeyMap)
                    }
                }
                ContactConflictAction.FORCE_IMPORT -> {
                    importedContacts += importAsNewContact(repository, contactConflict.contactExport, collectionId, fieldKeyMap)
                }
                ContactConflictAction.SKIP -> {
                    Log.d("Tester", "executeImport: skipping contact '${contactConflict.contactExport.name}'")
                }
            }
            // 附加新样式：在主操作之外再添加一条名片夹记录
            if (contactAddStyle[contactConflict.contactExport.name] == true && contactAction != ContactConflictAction.SKIP) {
                val styleContactId = when (contactAction) {
                    ContactConflictAction.MERGE -> contactConflict.existingContact?.id ?: continue
                    ContactConflictAction.FORCE_IMPORT -> continue // FORCE_IMPORT already creates new
                    else -> contactConflict.existingContact?.id ?: continue
                }
                Log.d("Tester", "executeImport: addStyle for '${contactConflict.contactExport.name}', contactId=$styleContactId")
                repository.addContactToCollection(styleContactId, collectionId, "import_style")
            }
        }
        importedCollections++
    }

    Log.d("Tester", "executeImport: done, collections=$importedCollections, new=$importedContacts, merged=$mergedContacts")
    return ImportResult(importedCollections, importedContacts, mergedContacts)
}

private suspend fun importAsNewContact(
    repository: ContactRepository,
    contactExport: ContactExport,
    collectionId: Long,
    fieldKeyMap: Map<String, ContactField>
): Int {
    val contactId = repository.insertContact(
        Contact(name = contactExport.name, avatarUrl = contactExport.avatarUrl, note = contactExport.note)
    )
    Log.d("Tester", "importAsNewContact: '${contactExport.name}', id=$contactId")
    for (fieldExport in contactExport.fields) {
        val field = fieldKeyMap[fieldExport.fieldKey] ?: continue
        repository.insertFieldValue(ContactFieldValue(contactId = contactId, fieldId = field.id, value = fieldExport.value))
    }
    // 导入社交平台数据
    contactExport.platforms?.forEach { (key, entry) ->
        repository.updateContactPlatform(contactId, key, PlatformEntry(
            value = entry.value,
            displayName = entry.displayName,
            jumpLink = entry.jumpLink ?: "",
            originalLink = entry.originalLink,
            avatarUrl = entry.avatarUrl
        ))
    }
    repository.addContactToCollection(contactId, collectionId, "import")
    return 1
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
        Toast.makeText(context, "导入完成：${result.importedCollections}个名片夹，${result.importedContacts}个新联系人，${result.mergedContacts}个已合并", Toast.LENGTH_LONG).show()
    }
}

/**
 * 从 JSON 导入名片夹（自动处理，无交互）
 */
suspend fun importFromJson(repository: ContactRepository, json: String): ImportResult {
    Log.d("Tester", "importFromJson: json length=${json.length}")
    val conflicts = analyzeImportConflicts(repository, json)
    // 无冲突时自动合并，有冲突时默认合并
    return executeImport(repository, conflicts, emptyMap(), emptyMap(), emptyMap())
}

/**
 * 从 JSON 导入联系人到指定名片夹（不创建新名片夹，无交互）
 */
suspend fun importContactsToCollection(repository: ContactRepository, collectionId: Long, json: String): Int {
    val conflicts = analyzeImportConflicts(repository, json)
    val allFields = repository.getAllFieldsOnce()
    val fieldKeyMap = allFields.associateBy { it.fieldKey }

    var count = 0
    var merged = 0
    for (conflict in conflicts) {
        for (contactConflict in conflict.contactConflicts) {
            if (contactConflict.existingContact != null) {
                val existing = contactConflict.existingContact
                Log.d("Tester", "importContactsToCollection: merging duplicate '${contactConflict.contactExport.name}' into existing id=${existing.id}")
                val freshContact = repository.getContactById(existing.id) ?: existing
                val updatedAvatarUrl = if (freshContact.avatarUrl.isNullOrBlank() && !contactConflict.contactExport.avatarUrl.isNullOrBlank()) contactConflict.contactExport.avatarUrl else freshContact.avatarUrl
                repository.updateContact(freshContact.copy(avatarUrl = updatedAvatarUrl, updateTime = System.currentTimeMillis()))
                val existingFieldMap = repository.getFieldValueMapByContact(existing.id)
                for (fieldExport in contactConflict.contactExport.fields) {
                    if (existingFieldMap.containsKey(fieldExport.fieldKey)) continue
                    val field = fieldKeyMap[fieldExport.fieldKey] ?: continue
                    repository.insertFieldValue(ContactFieldValue(contactId = existing.id, fieldId = field.id, value = fieldExport.value))
                }
                // 合并社交平台数据
                contactConflict.contactExport.platforms?.forEach { (key, entry) ->
                    if (freshContact.platforms?.containsKey(key) == true) return@forEach
                    repository.updateContactPlatform(existing.id, key, PlatformEntry(
                        value = entry.value,
                        displayName = entry.displayName,
                        jumpLink = entry.jumpLink ?: "",
                        originalLink = entry.originalLink,
                        avatarUrl = entry.avatarUrl
                    ))
                }
                if (!repository.existsContactInCollection(existing.id, collectionId)) {
                    repository.addContactToCollection(existing.id, collectionId, "import")
                }
                merged++
            } else {
                count += importAsNewContact(repository, contactConflict.contactExport, collectionId, fieldKeyMap)
            }
        }
    }
    Log.d("Tester", "importContactsToCollection: imported=$count, merged=$merged")
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
