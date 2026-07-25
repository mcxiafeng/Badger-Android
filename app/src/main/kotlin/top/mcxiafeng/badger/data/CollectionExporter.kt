package top.mcxiafeng.badger.data

import android.util.Log
import kotlinx.coroutines.flow.first
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink

// ===== JSON 数据模型 (v3 协议) =====
/**
 * 导出根对象。v1 已废弃,v2 起纳入 tag 持久化,v3 起 Tag 携带 source/confidence/createTime
 * 老 v1/v2 json 反序列化不会抛错 — tags.source 视为默认 "import",confidence/createTime 默认 1.0/0
 * —— [analyzeImportConflicts] 仅拒绝 v1 输入,v2 视为可接受的兼容格式。
 */
data class BadgerExport(
    @SerializedName("version") val version: Int = 3,
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
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("fields") val fields: List<FieldExport>,
    @SerializedName("platforms") val platforms: Map<String, PlatformEntryExport>? = null,
    /** v2 新增;v1 json 解析时默认为 null */
    @SerializedName("tags") val tags: List<TagExport>? = null
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

/**
 * v3 Tag 持久化结构,带 source/confidence/createTime,完整保留本地来源信息。
 *
 * - v2 老 JSON: source 默认 "import", confidence 1.0, createTime 0
 * - pinyinInitial / showDot 是运行时偏好,跨设备保留意义不大,留用目标库即可
 */
data class TagExport(
    @SerializedName("name") val name: String,
    @SerializedName("color") val color: Long,
    /** v3 新增;v2 JSON 缺省时为 "import" */
    @SerializedName("source") val source: String = "import",
    /** v3 新增;AI 关联时的置信度 [0,1];手动标签为 1.0 */
    @SerializedName("confidence") val confidence: Float = 1.0f,
    /** v3 新增;关联时间戳 ms,0 表示未知 */
    @SerializedName("createTime") val createTime: Long = 0L
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

// ===== Tag source 区分 =====
private const val TAG_SOURCE_IMPORT = "import"
private const val TAG_SOURCE_NEW_STYLE = "import_new_style"

// ===== Gson 单例 =====

private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

// ===== 导出/导入 =====

/**
 * 导出指定名片夹为 JSON (v2,含 tags)
 *
 * @param tagRepository 用于批量取每个 contact 的 tag,避免 N+1
 */
suspend fun exportToJson(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    tagRepository: TagRepository,
    collectionIds: List<Long>
): String {
    Log.d("Tester", "exportToJson: collectionIds=$collectionIds")
    val allFields = fieldRepository.getAllFieldsOnce()
    val fieldMap = allFields.associateBy { it.id }

    val collections = collectionIds.mapNotNull { id ->
        val collection = collectionRepository.getCollectionById(id) ?: return@mapNotNull null
        val contacts = collectionRepository.getContactsByCollectionOnce(id)
        val platformsByContact = if (contacts.isNotEmpty()) {
            contactRepository.getAllContactPlatformsGrouped()
        } else emptyMap()
        val tagsByContact = if (contacts.isNotEmpty()) {
            tagRepository.getTagsForContactsOnce(contacts.map { it.id })
        } else emptyMap()
        // [P1-9] 同时拉 contact_tag 关联行,导出 source/confidence/createTime
        val crossRefsByContact = if (contacts.isNotEmpty()) {
            val refs = tagRepository.getCrossRefsForContacts(contacts.map { it.id })
            refs.groupBy { it.contactId }
        } else emptyMap()

        val contactExports = contacts.map { contact ->
            val fieldValues = fieldRepository.getFieldValuesByContactOnce(contact.id)
            val regularFields = fieldValues.mapNotNull { fv ->
                val key = fv.fieldId?.let { fieldMap[it]?.fieldKey } ?: return@mapNotNull null
                if (key in PLATFORM_FIELD_KEYS) return@mapNotNull null
                FieldExport(fieldKey = key, value = fv.value)
            }
            val platformExports = (platformsByContact[contact.id] ?: emptyList()).associate { cp ->
                cp.platformKey to PlatformEntryExport(
                    value = cp.value,
                    displayName = cp.displayName,
                    jumpLink = cp.jumpLink,
                    originalLink = cp.originalLink,
                    avatarUrl = cp.avatarUrl
                )
            }
            // [P1-9] 按 (contactId, tagId) 关联行优先级合并：关联行 source > Tag 行 source
            val contactCrossRefs = crossRefsByContact[contact.id].orEmpty()
                .associateBy { it.tagId }
            val tagExports = (tagsByContact[contact.id] ?: emptyList()).map { tag ->
                val ref = contactCrossRefs[tag.id]
                TagExport(
                    name = tag.name,
                    color = tag.color,
                    source = ref?.source?.takeIf { it.isNotBlank() } ?: tag.source,
                    confidence = ref?.confidence ?: 1.0f,
                    createTime = ref?.createTime?.takeIf { it > 0 } ?: tag.createTime,
                )
            }
            ContactExport(
                name = contact.name,
                avatarUrl = contact.avatarUrl,
                note = contact.note,
                bio = contact.bio,
                fields = regularFields,
                platforms = platformExports.ifEmpty { null },
                tags = tagExports.ifEmpty { null }
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
suspend fun analyzeImportConflicts(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    json: String
): List<ImportConflict> {
    Log.d("Tester", "analyzeImportConflicts: json length=${json.length}")
    val export = try {
        gson.fromJson(json, BadgerExport::class.java)
    } catch (e: Exception) {
        Log.e("Tester", "analyzeImportConflicts: 无效的 JSON 格式", e)
        throw IllegalArgumentException("无效的 JSON 格式")
    }
    if (export.version !in setOf(2, 3)) throw IllegalArgumentException("不支持的版本: ${export.version}（请用 Badger v2/v3 导出的 JSON）")

    val existingCollections = collectionRepository.getAllCollectionsOnce().associateBy { it.name }

    val allContacts = contactRepository.getAllContacts().first()
    val contactsByName = allContacts.groupBy { it.name.lowercase() }
    val allPlatformsByContact = contactRepository.getAllContactPlatformsGrouped()
    val platformValueIndex = mutableMapOf<String, MutableMap<String, MutableList<Contact>>>()
    for (contact in allContacts) {
        val platforms = allPlatformsByContact[contact.id] ?: emptyList()
        for (cp in platforms) {
            val value = cp.value ?: continue
            platformValueIndex.getOrPut(cp.platformKey) { mutableMapOf() }
                .getOrPut(value) { mutableListOf() }
                .add(contact)
        }
    }

    return export.collections.map { collectionExport ->
        val existingCollection = existingCollections[collectionExport.name]
        val contactConflicts = collectionExport.contacts.map { contactExport ->
            val fieldValues = contactExport.fields.associate { it.fieldKey to it.value }.toMutableMap()
            contactExport.platforms?.forEach { (key, entry) ->
                if (!entry.value.isNullOrBlank()) fieldValues[key] = entry.value
            }
            Log.d("Tester", "analyzeImportConflicts: contact='${contactExport.name}', fieldValues=$fieldValues, platforms=${contactExport.platforms?.keys}")

            var bestMatch: Contact? = null
            var bestScore = 0f
            var matchedFields = emptyList<String>()

            val nameMatches = contactsByName[contactExport.name.lowercase()] ?: emptyList()
            for (contact in nameMatches) {
                bestScore = 1.0f
                bestMatch = contact
                matchedFields = listOf("name")
                break
            }
            if (bestScore < 1.0f) {
                for ((key, value) in fieldValues) {
                    if (value.isBlank()) continue
                    val platformMatches = platformValueIndex[key]?.get(value)
                    if (platformMatches != null) {
                        for (contact in platformMatches) {
                            bestScore = 1.0f
                            bestMatch = contact
                            matchedFields = listOf(key)
                        }
                    }
                }
            }

            val dupResult = DuplicateCheckResult(
                isDuplicate = bestScore >= 1.0f,
                existingContact = bestMatch,
                similarityScore = bestScore,
                matchFields = matchedFields
            )
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
 * 执行导入（根据用户选择处理冲突）
 *
 * @param tagRepository v2 新增:用于 upsertTag + 关联 contactTag
 * @param contactAddStyle 兼容旧 UI 控件:`true` 时额外给该联系人建一个 "导入样式 N" Tag
 */
suspend fun executeImport(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    tagRepository: TagRepository,
    conflicts: List<ImportConflict>,
    collectionActions: Map<String, CollectionConflictAction>,
    contactActions: Map<String, ContactConflictAction>,
    renamedCollectionNames: Map<String, String>,
    contactAddStyle: Map<String, Boolean> = emptyMap()
): ImportResult {
    Log.d("Tester", "executeImport: ${conflicts.size} collections, collectionActions=$collectionActions, contactActions=$contactActions")
    val allFields = fieldRepository.getAllFieldsOnce()
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
            collectionRepository.insertCollection(
                CardCollection(
                    name = newName,
                    description = conflict.collectionExport.description,
                    createTime = System.currentTimeMillis(),
                )
            ).also { Log.d("Tester", "executeImport: renamed collection to '$newName', id=$it") }
        } else if (conflict.existingCollection != null) {
            Log.d("Tester", "executeImport: merging into existing collection '${conflict.collectionExport.name}', id=${conflict.existingCollection.id}")
            conflict.existingCollection.id
        } else {
            collectionRepository.insertCollection(
                CardCollection(
                    name = conflict.collectionExport.name,
                    description = conflict.collectionExport.description,
                    createTime = System.currentTimeMillis(),
                )
            ).also { Log.d("Tester", "executeImport: created new collection '${conflict.collectionExport.name}', id=$it") }
        }

        for (contactConflict in conflict.contactConflicts) {
            val contactAction = contactActions[contactConflict.contactExport.name]
                ?: if (contactConflict.existingContact != null) ContactConflictAction.MERGE else ContactConflictAction.FORCE_IMPORT

            // 每条 contact 处理后,resolvedContactId 用于 contactAddStyle 补打 Tag
            var resolvedContactId: Long? = null

            when (contactAction) {
                ContactConflictAction.MERGE -> {
                    if (contactConflict.existingContact != null) {
                        val existing = contactConflict.existingContact
                        Log.d("Tester", "executeImport: merging contact '${contactConflict.contactExport.name}' into existing id=${existing.id}")
                        val freshContact = contactRepository.getContactById(existing.id) ?: existing
                        val updatedAvatarUrl = if (freshContact.avatarUrl.isNullOrBlank() && !contactConflict.contactExport.avatarUrl.isNullOrBlank()) contactConflict.contactExport.avatarUrl else freshContact.avatarUrl
                        contactRepository.updateContact(freshContact.copy(avatarUrl = updatedAvatarUrl, updateTime = System.currentTimeMillis()))
                        val existingFieldMap = fieldRepository.getFieldValueMapByContact(existing.id)
                        for (fieldExport in contactConflict.contactExport.fields) {
                            if (existingFieldMap.containsKey(fieldExport.fieldKey)) continue
                            val field = fieldKeyMap[fieldExport.fieldKey] ?: continue
                            fieldRepository.insertFieldValue(ContactFieldValue(contactId = existing.id, fieldId = field.id, value = fieldExport.value))
                        }
                        val existingPlatformKeys = contactRepository.getContactPlatformKeys(existing.id)
                        contactConflict.contactExport.platforms?.forEach { (key, entry) ->
                            if (key in existingPlatformKeys) return@forEach
                            val resolvedJumpLink = entry.jumpLink?.ifBlank { null } ?: buildPlatformLink(key, entry.value ?: "")
                            contactRepository.updateContactPlatform(existing.id, key, PlatformEntry(
                                value = entry.value,
                                displayName = entry.displayName,
                                jumpLink = resolvedJumpLink,
                                originalLink = entry.originalLink,
                                avatarUrl = entry.avatarUrl
                            ))
                        }
                        applyContactTags(tagRepository, existing.id, contactConflict.contactExport.tags)
                        if (!collectionRepository.existsContactInCollection(existing.id, collectionId)) {
                            collectionRepository.addContactToCollection(existing.id, collectionId, "import")
                        }
                        mergedContacts++
                        resolvedContactId = existing.id
                    } else {
                        val newId = importAsNewContact(contactRepository, fieldRepository, collectionRepository, contactConflict.contactExport, collectionId, fieldKeyMap)
                        if (newId > 0) applyContactTags(tagRepository, newId, contactConflict.contactExport.tags)
                        importedContacts++
                        resolvedContactId = newId.takeIf { it > 0 }
                    }
                }
                ContactConflictAction.NEW_STYLE -> {
                    // v5+ 语义:为该联系人建一个新 Tag(name = "导入样式 N"),并沿用源 tag 还原
                    if (contactConflict.existingContact != null) {
                        Log.d("Tester", "executeImport: new-style for '${contactConflict.contactExport.name}', existing id=${contactConflict.existingContact.id}")
                        if (!collectionRepository.existsContactInCollection(contactConflict.existingContact.id, collectionId)) {
                            collectionRepository.addContactToCollection(contactConflict.existingContact.id, collectionId, "import")
                        }
                        val existingId = contactConflict.existingContact.id
                        applyContactTags(tagRepository, existingId, contactConflict.contactExport.tags)
                        val styleTagId = tagRepository.upsertTag(
                            name = nextNewStyleName(tagRepository),
                            color = 0xFF1976D2L,
                            source = TAG_SOURCE_NEW_STYLE
                        )
                        tagRepository.addTagToContact(existingId, styleTagId)
                        contactRepository.updateContact(
                            (contactRepository.getContactById(existingId) ?: contactConflict.existingContact)
                                .copy(updateTime = System.currentTimeMillis())
                        )
                        mergedContacts++
                        resolvedContactId = existingId
                    } else {
                        val newId = importAsNewContact(contactRepository, fieldRepository, collectionRepository, contactConflict.contactExport, collectionId, fieldKeyMap)
                        if (newId > 0) {
                            applyContactTags(tagRepository, newId, contactConflict.contactExport.tags)
                            val styleTagId = tagRepository.upsertTag(
                                name = nextNewStyleName(tagRepository),
                                color = 0xFF1976D2L,
                                source = TAG_SOURCE_NEW_STYLE
                            )
                            tagRepository.addTagToContact(newId, styleTagId)
                        }
                        importedContacts++
                        resolvedContactId = newId.takeIf { it > 0 }
                    }
                }
                ContactConflictAction.FORCE_IMPORT -> {
                    val newId = importAsNewContact(contactRepository, fieldRepository, collectionRepository, contactConflict.contactExport, collectionId, fieldKeyMap)
                    if (newId > 0) applyContactTags(tagRepository, newId, contactConflict.contactExport.tags)
                    importedContacts++
                    resolvedContactId = newId.takeIf { it > 0 }
                }
                ContactConflictAction.SKIP -> {
                    Log.d("Tester", "executeImport: skipping contact '${contactConflict.contactExport.name}'")
                }
            }

            // 附加:用户在 UI 额外勾选"再打一个 tag"时,补建一个 Tag(NEW_STYLE 已自带此行为,不重复)
            if (contactAddStyle[contactConflict.contactExport.name] == true &&
                contactAction != ContactConflictAction.SKIP &&
                resolvedContactId != null &&
                contactAction != ContactConflictAction.NEW_STYLE
            ) {
                val styleTagId = tagRepository.upsertTag(
                    name = nextNewStyleName(tagRepository),
                    color = 0xFF1976D2L,
                    source = TAG_SOURCE_NEW_STYLE
                )
                tagRepository.addTagToContact(resolvedContactId, styleTagId)
                Log.d("Tester", "executeImport: addStyle extra tag for '${contactConflict.contactExport.name}', contactId=$resolvedContactId")
            }
        }
        importedCollections++
    }

    Log.d("Tester", "executeImport: done, collections=$importedCollections, new=$importedContacts, merged=$mergedContacts")
    return ImportResult(importedCollections, importedContacts, mergedContacts)
}

/**
 * 把 json 里携带的 tags 应用到指定 contact。
 * - 同名 tag 已存在:复用并按需同步 color (用户期待"无损还原")
 * - 整批包在 [tagRepository.applyImportedTags] 事务内,任一失败回滚
 * - [P1-9] 每个 tag 的 source/confidence/createTime 跟随 JSON 透传
 */
private suspend fun applyContactTags(
    tagRepository: TagRepository,
    contactId: Long,
    tags: List<TagExport>?
) {
    if (tags.isNullOrEmpty()) return
    tagRepository.applyImportedTags(contactId = contactId, tagExports = tags)
}

/**
 * 算出下一个 "导入样式 N" 名字。查现有 source='import_new_style' 的数量 +1。
 */
private suspend fun nextNewStyleName(tagRepository: TagRepository): String {
    val count = try {
        tagRepository.getAllTagsOnce().count {
            it.source == TAG_SOURCE_NEW_STYLE && it.name.startsWith("导入样式 ")
        }
    } catch (_: Exception) { 0 }
    return "导入样式 ${count + 1}"
}

/**
 * 新建一条 Contact + 写 fields/platforms + 加入名片夹。
 * @return 新 contact 的 id;0 表示失败(理论上不该发生)
 */
private suspend fun importAsNewContact(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    contactExport: ContactExport,
    collectionId: Long,
    fieldKeyMap: Map<String, ContactField>
): Long {
    val now = System.currentTimeMillis()
    val contactId = contactRepository.insertContact(
        Contact(
            id = 0L,
            name = contactExport.name,
            avatarUrl = contactExport.avatarUrl,
            note = contactExport.note,
            createTime = now,
            updateTime = now,
        )
    )
    Log.d("Tester", "importAsNewContact: '${contactExport.name}', id=$contactId")
    for (fieldExport in contactExport.fields) {
        val field = fieldKeyMap[fieldExport.fieldKey] ?: continue
        fieldRepository.insertFieldValue(ContactFieldValue(contactId = contactId, fieldId = field.id, value = fieldExport.value))
    }
    contactExport.platforms?.forEach { (key, entry) ->
        val resolvedJumpLink = entry.jumpLink?.ifBlank { null } ?: buildPlatformLink(key, entry.value ?: "")
        contactRepository.updateContactPlatform(contactId, key, PlatformEntry(
            value = entry.value,
            displayName = entry.displayName,
            jumpLink = resolvedJumpLink,
            originalLink = entry.originalLink,
            avatarUrl = entry.avatarUrl
        ))
    }
    collectionRepository.addContactToCollection(contactId, collectionId, "import")
    return contactId
}

/**
 * 从 JSON 导入名片夹（自动处理，无交互）
 */
suspend fun importFromJson(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    tagRepository: TagRepository,
    json: String
): ImportResult {
    Log.d("Tester", "importFromJson: json length=${json.length}")
    val conflicts = analyzeImportConflicts(contactRepository, fieldRepository, collectionRepository, json)
    return executeImport(contactRepository, fieldRepository, collectionRepository, tagRepository, conflicts, emptyMap(), emptyMap(), emptyMap())
}

/**
 * 从 JSON 导入联系人到指定名片夹（不创建新名片夹，无交互）
 */
suspend fun importContactsToCollection(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    tagRepository: TagRepository,
    collectionId: Long,
    json: String
): Int {
    val conflicts = analyzeImportConflicts(contactRepository, fieldRepository, collectionRepository, json)
    val allFields = fieldRepository.getAllFieldsOnce()
    val fieldKeyMap = allFields.associateBy { it.fieldKey }

    var count = 0
    var merged = 0
    for (conflict in conflicts) {
        for (contactConflict in conflict.contactConflicts) {
            if (contactConflict.existingContact != null) {
                val existing = contactConflict.existingContact
                Log.d("Tester", "importContactsToCollection: merging duplicate '${contactConflict.contactExport.name}' into existing id=${existing.id}")
                val freshContact = contactRepository.getContactById(existing.id) ?: existing
                val updatedAvatarUrl = if (freshContact.avatarUrl.isNullOrBlank() && !contactConflict.contactExport.avatarUrl.isNullOrBlank()) contactConflict.contactExport.avatarUrl else freshContact.avatarUrl
                contactRepository.updateContact(freshContact.copy(avatarUrl = updatedAvatarUrl, updateTime = System.currentTimeMillis()))
                val existingFieldMap = fieldRepository.getFieldValueMapByContact(existing.id)
                for (fieldExport in contactConflict.contactExport.fields) {
                    if (existingFieldMap.containsKey(fieldExport.fieldKey)) continue
                    val field = fieldKeyMap[fieldExport.fieldKey] ?: continue
                    fieldRepository.insertFieldValue(ContactFieldValue(contactId = existing.id, fieldId = field.id, value = fieldExport.value))
                }
                val existingPlatformKeys = contactRepository.getContactPlatformKeys(existing.id)
                contactConflict.contactExport.platforms?.forEach { (key, entry) ->
                    if (key in existingPlatformKeys) return@forEach
                    val resolvedJumpLink = entry.jumpLink?.ifBlank { null } ?: buildPlatformLink(key, entry.value ?: "")
                    contactRepository.updateContactPlatform(existing.id, key, PlatformEntry(
                        value = entry.value,
                        displayName = entry.displayName,
                        jumpLink = resolvedJumpLink,
                        originalLink = entry.originalLink,
                        avatarUrl = entry.avatarUrl
                    ))
                }
                applyContactTags(tagRepository, existing.id, contactConflict.contactExport.tags)
                if (!collectionRepository.existsContactInCollection(existing.id, collectionId)) {
                    collectionRepository.addContactToCollection(existing.id, collectionId, "import")
                }
                merged++
            } else {
                val newId = importAsNewContact(contactRepository, fieldRepository, collectionRepository, contactConflict.contactExport, collectionId, fieldKeyMap)
                if (newId > 0) applyContactTags(tagRepository, newId, contactConflict.contactExport.tags)
                count++
            }
        }
    }
    Log.d("Tester", "importContactsToCollection: imported=$count, merged=$merged")
    return count
}

/**
 * 解析 JSON 用于预览（不执行导入）。
 * v2/json 任意版本都能解析,用于给用户看"数量"。
 */
fun previewImport(json: String): Pair<Int, Int> {
    val export = try {
        gson.fromJson(json, BadgerExport::class.java)
    } catch (e: Exception) {
        Log.w("CollectionExporter", "previewImport 解析失败", e)
        return 0 to 0
    }
    val contacts = export.collections.sumOf { it.contacts.size }
    return export.collections.size to contacts
}
