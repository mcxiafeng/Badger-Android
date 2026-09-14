package top.mcxiafeng.badger.data.importer

import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.mcxiafeng.badger.data.model.ContactField
import top.mcxiafeng.badger.data.model.ContactFieldValue
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.nowMs

@Serializable
data class BadgerExport(
    @SerialName("version") val version: Int = 3,
    @SerialName("app") val app: String = "badger",
    @SerialName("exportTime") val exportTime: Long = nowMs(),
    @SerialName("collections") val collections: List<CollectionExport> = emptyList()
)

@Serializable
data class CollectionExport(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("contacts") val contacts: List<ContactExport> = emptyList()
)

@Serializable
data class ContactExport(
    @SerialName("name") val name: String,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("fields") val fields: List<FieldExport> = emptyList(),
    @SerialName("platforms") val platforms: Map<String, PlatformEntryExport>? = null,
    
    @SerialName("tags") val tags: List<TagExport>? = null
)

@Serializable
data class FieldExport(
    @SerialName("fieldKey") val fieldKey: String,
    @SerialName("value") val value: String
)

@Serializable
data class PlatformEntryExport(
    @SerialName("value") val value: String? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("jumpLink") val jumpLink: String? = null,
    @SerialName("originalLink") val originalLink: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null
)

@Serializable
data class TagExport(
    @SerialName("name") val name: String,
    @SerialName("color") val color: Long,
    
    @SerialName("source") val source: String = "import",
    
    @SerialName("confidence") val confidence: Float = 1.0f,
    
    @SerialName("createTime") val createTime: Long = 0L
)

data class ImportResult(
    val importedCollections: Int,
    val importedContacts: Int,
    val mergedContacts: Int
)

data class ImportConflict(
    val rowId: Int,
    val collectionExport: CollectionExport,
    val existingCollection: CardCollection?,
    val contactConflicts: List<ContactConflict>
)

data class ContactConflict(
    val rowId: Int,
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

private const val TAG = "CollectionExporter"
private const val TAG_SOURCE_NEW_STYLE = "import_new_style"

private val ExportJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
    prettyPrint = true
    prettyPrintIndent = "  "
}

suspend fun exportToJson(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    tagRepository: TagRepository,
    collectionIds: List<Long>
): String {
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

    val json = ExportJson.encodeToString(BadgerExport(collections = collections))
        return json
}

suspend fun analyzeImportConflicts(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    json: String
): List<ImportConflict> {
        val export = try {
        ExportJson.decodeFromString<BadgerExport>(json)
    } catch (e: Exception) {
        BadgerLog.e(TAG, "analyzeImportConflicts: 无效的 JSON 格式", e)
        throw IllegalArgumentException("无效的 JSON 格式")
    }
    if (export.version !in setOf(2, 3)) throw IllegalArgumentException("不支持的版本: ${export.version}（请用 Badger v2/v3 导出的 JSON）")
    
    val safeCollections = export.collections ?: emptyList()

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

    
    var nextContactRowId = 0
    return safeCollections.mapIndexed { collectionIndex, collectionExport ->
        val existingCollection = existingCollections[collectionExport.name]
        
        val safeContacts = collectionExport.contacts ?: emptyList()
        val contactConflicts = safeContacts.map { contactExport ->
            val safeFields = contactExport.fields ?: emptyList()
            val fieldValues = safeFields.associate { it.fieldKey to it.value }.toMutableMap()
            contactExport.platforms?.forEach { (key, entry) ->
                if (!entry.value.isNullOrBlank()) fieldValues[key] = entry.value
            }

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
                        val lastContact = platformMatches.lastOrNull()
                        if (lastContact != null) {
                            bestScore = 1.0f
                            bestMatch = lastContact
                            matchedFields = listOf(key)
                        }
                    }
                }
            }

            
            ContactConflict(
                
                rowId = nextContactRowId++,
                contactExport = contactExport,
                existingContact = if (bestScore >= 1.0f) bestMatch else null,
                similarityScore = bestScore,
                matchFields = matchedFields
            )
        }
        ImportConflict(
            
            rowId = collectionIndex,
            collectionExport = collectionExport,
            existingCollection = existingCollection,
            contactConflicts = contactConflicts
        )
    }
}

suspend fun executeImport(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    tagRepository: TagRepository,
    conflicts: List<ImportConflict>,
    collectionActions: Map<Int, CollectionConflictAction>,
    contactActions: Map<Int, ContactConflictAction>,
    renamedCollectionNames: Map<Int, String>,
    contactAddStyle: Map<Int, Boolean> = emptyMap()
): ImportResult {
        val allFields = fieldRepository.getAllFieldsOnce()
    val fieldKeyMap = allFields.associateBy { it.fieldKey }

    var importedCollections = 0
    var importedContacts = 0
    var mergedContacts = 0

    for (conflict in conflicts) {
        val action = collectionActions[conflict.rowId] ?: CollectionConflictAction.MERGE
        if (action == CollectionConflictAction.SKIP) {
                        continue
        }

        val collectionId = if (action == CollectionConflictAction.RENAME) {
            val newName = renamedCollectionNames[conflict.rowId] ?: "${conflict.collectionExport.name}_2"
            collectionRepository.insertCollection(
                CardCollection(
                    name = newName,
                    description = conflict.collectionExport.description,
                    createTime = nowMs(),
                )
            )
        } else if (conflict.existingCollection != null) {
                        conflict.existingCollection.id
        } else {
            collectionRepository.insertCollection(
                CardCollection(
                    name = conflict.collectionExport.name,
                    description = conflict.collectionExport.description,
                    createTime = nowMs(),
                )
            )
        }

        for (contactConflict in conflict.contactConflicts) {
            val contactAction = contactActions[contactConflict.rowId]
                ?: if (contactConflict.existingContact != null) ContactConflictAction.MERGE else ContactConflictAction.FORCE_IMPORT

            
            var resolvedContactId: Long? = null

            when (contactAction) {
                ContactConflictAction.MERGE -> {
                    if (contactConflict.existingContact != null) {
                        val existing = contactConflict.existingContact
                                                val freshContact = contactRepository.getContactById(existing.id) ?: existing
                        val updatedAvatarUrl = if (freshContact.avatarUrl.isNullOrBlank() && !contactConflict.contactExport.avatarUrl.isNullOrBlank()) contactConflict.contactExport.avatarUrl else freshContact.avatarUrl
                        contactRepository.updateContact(freshContact.copy(avatarUrl = updatedAvatarUrl, updateTime = nowMs()))
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
                    
                    if (contactConflict.existingContact != null) {
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
                                .copy(updateTime = nowMs())
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
                                    }
            }

            
            if (contactAddStyle[contactConflict.rowId] == true &&
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
                            }
        }
        importedCollections++
    }

        return ImportResult(importedCollections, importedContacts, mergedContacts)
}

private suspend fun applyContactTags(
    tagRepository: TagRepository,
    contactId: Long,
    tags: List<TagExport>?
) {
    if (tags.isNullOrEmpty()) return
    tagRepository.applyImportedTags(contactId = contactId, tagExports = tags)
}

private suspend fun nextNewStyleName(tagRepository: TagRepository): String {
    val count = try {
        tagRepository.getAllTagsOnce().count {
            it.source == TAG_SOURCE_NEW_STYLE && it.name.startsWith("导入样式 ")
        }
    } catch (_: Exception) { 0 }
    return "导入样式 ${count + 1}"
}

private suspend fun importAsNewContact(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    contactExport: ContactExport,
    collectionId: Long,
    fieldKeyMap: Map<String, ContactField>
): Long {
    val now = nowMs()
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

suspend fun importFromJson(
    contactRepository: ContactRepository,
    fieldRepository: FieldRepository,
    collectionRepository: CollectionRepository,
    tagRepository: TagRepository,
    json: String
): ImportResult {
        val conflicts = analyzeImportConflicts(contactRepository, fieldRepository, collectionRepository, json)
    return executeImport(contactRepository, fieldRepository, collectionRepository, tagRepository, conflicts, emptyMap(), emptyMap(), emptyMap())
}

fun previewImport(json: String): Pair<Int, Int> {
    val export = try {
        ExportJson.decodeFromString<BadgerExport>(json)
    } catch (e: Exception) {
        BadgerLog.w("CollectionExporter", "previewImport 解析失败", e)
        return 0 to 0
    }
    val contacts = export.collections.sumOf { it.contacts.size }
    return export.collections.size to contacts
}
