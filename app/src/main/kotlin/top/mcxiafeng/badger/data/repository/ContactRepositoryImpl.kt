package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.decodePlatformsMap
import top.mcxiafeng.badger.data.repository.ContactMapper.encodePlatformsMap
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactField
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactWithFields
import top.mcxiafeng.badger.data.repository.ContactMapper.toFieldDisplay
import top.mcxiafeng.badger.data.repository.ContactMapper.toFieldValue
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatform
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.data.snapshot.ContactSnapshotter
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.PinyinUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class ContactRepositoryImpl @Inject constructor(
    private val contactCacheDao: ContactCacheDao,
    private val contactFieldCacheDao: ContactFieldCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    // ========== [V2-P5] optimisticUpdate 依赖 ==========
    private val contactSnapshotter: ContactSnapshotter,
    private val pendingDao: PendingUploadDao,
    private val historyDao: OperationHistoryDao,
    private val pendingUploadScheduler: PendingUploadScheduler,
    private val deviceIdProvider: DeviceIdProvider,
) : ContactRepository {

    private val contactMutex = Mutex()

    // ========== 联系人基本操作 ==========

    override fun getAllContacts(): Flow<List<ContactCacheEntity>> = contactCacheDao.getAllContacts()

    override fun getLetterIndex(): Flow<List<LetterCount>> = contactCacheDao.getLetterIndex()

    override suspend fun getContactById(id: Long): ContactCacheEntity? = withContext(Dispatchers.IO) {
        contactCacheDao.getContactById(id)
    }

    override fun getAllContactsWithFields(): Flow<List<ContactWithFields>> {
        return contactCacheDao.getAllContacts().map { contacts ->
            contacts.map { contact ->
                contact.toContactWithFields(emptyList())
            }
        }
    }

    override suspend fun getContactWithFieldsById(id: Long): ContactWithFields? = withContext(Dispatchers.IO) {
        val contact = contactCacheDao.getContactById(id) ?: return@withContext null
        val fieldValues = contactFieldValueCacheDao.getFieldValuesByContactOnce(id)

        val fieldIds = fieldValues.mapNotNull { it.fieldId }.distinct()

        val fieldMap = if (fieldIds.isNotEmpty()) {
            contactFieldCacheDao.getFieldsByIds(fieldIds).filter { it.isEnabled }
                .associate { it.id to it.toContactField() }
        } else emptyMap()

        val fields = fieldValues.mapNotNull { value ->
            if (value.fieldId != null) {
                val field = fieldMap[value.fieldId] ?: return@mapNotNull null
                value.toFieldDisplay(
                    fieldName = field.fieldName,
                    fieldKey = field.fieldKey,
                    icon = field.icon,
                    sortOrder = field.sortOrder,
                )
            } else null
        }.sortedBy { it.sortOrder }

        contact.toContactWithFields(fields)
    }

    override suspend fun insertContact(contact: ContactCacheEntity): Long = withContext(Dispatchers.IO) {
        val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
            contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
        } else contact
        // [V2-P5] 创建联系人走乐观队列(对齐 §5.2「创建走乐观」)。
        // 若服务端 P0 协议未就绪,Worker 会 CONFLICT → 操作可撤销(P7 历史页)。
        val newId = contactCacheDao.insertContact(withPinyin)
        val now = System.currentTimeMillis()
        val opId = UUID.randomUUID().toString()
        val snapshotBefore = contactSnapshotter.toJsonFromCache(newId, now)
        // inverse = 撤销创建=删除联系人(snapshotBefore 即 contact 主体)
        val inverseJson = buildJsonObject {
            addProperty("action", OperationTypes.DELETE_CONTACT)
            addProperty("contactId", newId)
            addProperty("snapshot", snapshotBefore)
        }.toString()
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = newId,
                opType = OperationTypes.CREATE_CONTACT,
                resourceVersion = 0L,
                payloadJson = buildJsonObject {
                    addProperty("name", withPinyin.name)
                    addProperty("bio", withPinyin.bio)
                    addProperty("note", withPinyin.note)
                    addProperty("avatarUrl", withPinyin.avatarUrl)
                }.toString(),
                createdAt = now,
                status = "PENDING",
                deviceId = deviceIdProvider.deviceId(),
            )
        )
        historyDao.insert(
            OperationHistoryEntity(
                opId = opId,
                contactId = newId,
                opType = OperationTypes.CREATE_CONTACT,
                opLabel = OperationTypes.labelOf(OperationTypes.CREATE_CONTACT),
                payloadJson = """{"contactId":$newId,"name":"${withPinyin.name}"}""",
                snapshotBeforeJson = snapshotBefore,
                snapshotAfterJson = null,
                createdAt = now,
                opStatus = "PENDING",
                inversePayloadJson = inverseJson,
                canUndo = true,
                canReplay = false,
            )
        )
        contactCacheDao.bumpContact(newId)
        pendingUploadScheduler.kick()
        newId
    }

    override suspend fun updateContact(contact: ContactCacheEntity) = withContext(Dispatchers.IO) {
        // [修复防御]: 改名后必须重算 pinyinInitial。
        // 旧实现"只有 isBlank 才补"是契约漏洞——前端 ViewModel 改了 name 但 pinyinInitial
        // 仍是旧值时,sort 桶就会错乱(主列表按 pinyinInitial ASC,桶内按 name ASC,导致
        // "Bob" 这种 B 名字插在 S 桶里;侧栏 getLetterIndex 也 GROUP BY 在错误的 pinyinInitial
        // 上)。这里做 name 是否真的变了 + pinyinInitial 与重算结果是否一致,任一不符就重算并写回。
        val normalized = contact.copy(
            pinyinInitial = normalizePinyinInitial(contact.name, contact.pinyinInitial)
        )
        val existing = contactCacheDao.getContactById(contact.id)
        if (existing == null) {
            // [修复防御]: 联系人已被删 / 不存在 — 不入队,直接 updateContact 兜底,
            // 让上层不至于崩。生产路径几乎不会走到这。
            contactCacheDao.updateContact(normalized)
            contactCacheDao.bumpContact(normalized.id)
            return@withContext
        }
        if (existing.name != normalized.name) {
            // [V2-P5] 改名走队列(inverse = 改回旧名)
            optimisticUpdate(
                contactId = contact.id,
                opType = OperationTypes.UPDATE_NAME,
                payloadJson = buildJsonObject {
                    addProperty("name", normalized.name)
                    addProperty("pinyinInitial", normalized.pinyinInitial)
                }.toString(),
                inversePayloadJson = buildJsonObject {
                    addProperty("name", existing.name)
                    addProperty("pinyinInitial", existing.pinyinInitial)
                }.toString(),
                applyContactCache = { normalized },
            )
        } else {
            // 其他字段(avatar / note / bio 等)的非队列更新 — P5 阶段保留直写路径,
            // P6+ 再扩 opType(改头像 / 改备注都走队列)。
            contactCacheDao.updateContact(normalized)
            contactCacheDao.bumpContact(normalized.id)
        }
    }

    override suspend fun updateContactBio(contactId: Long, bio: String?) = withContext(Dispatchers.IO) {
        val existing = contactCacheDao.getContactById(contactId) ?: return@withContext
        if (existing.bio == bio) {
            Log.d("Tester", "ContactRepositoryImpl.updateContactBio: id=$contactId no-op (bio unchanged)")
            return@withContext
        }
        Log.d("Tester", "ContactRepositoryImpl.updateContactBio: id=$contactId, oldLen=${existing.bio?.length}, newLen=${bio?.length}")
        optimisticUpdate(
            contactId = contactId,
            opType = OperationTypes.UPDATE_BIO,
            payloadJson = buildJsonObject { addProperty("bio", bio) }.toString(),
            inversePayloadJson = buildJsonObject { addProperty("bio", existing.bio) }.toString(),
            applyContactCache = { it.copy(bio = bio) },
        )
    }

    override suspend fun deleteContact(contact: ContactCacheEntity) = withContext(Dispatchers.IO) {
        contactCacheDao.deleteByIds(listOf(contact.id))
    }

    override suspend fun deleteByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        Log.d("Tester", "ContactRepositoryImpl.deleteByIds: count=${ids.size}")
        contactCacheDao.deleteByIds(ids)
    }

    override fun searchContacts(query: String): Flow<List<ContactCacheEntity>> {
        return if (query.isBlank()) {
            contactCacheDao.getAllContacts()
        } else {
            Log.d("Tester", "searchContacts: raw='$query' (V2 cache: LIKE path)")
            contactCacheDao.searchContacts(query)
        }
    }

    override suspend fun bumpContact(contactId: Long) = withContext(Dispatchers.IO) {
        contactCacheDao.bumpContact(contactId)
    }

    // ========== 联系人社交平台操作 ==========

    override suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        contactMutex.withLock {
            withContext(Dispatchers.IO) {
                if (entry.jumpLink.isBlank() && entry.value.isNullOrBlank()) {
                    // [V2-P5] 空 PlatformEntry 视为删除 → 走 REMOVE_PLATFORM 队列
                    // [修复防御]:不递归调 removeContactPlatform(它在 contactMutex.withLock 内),
                    // 否则 Mutex 在 runTest 单线程调度下死锁(测试 1m 超时)。
                    val existing = contactPlatformCacheDao.getPlatformsByContact(contactId)
                        .firstOrNull { it.platformKey == fieldKey }
                    if (existing == null) {
                        Log.d("Tester", "updateContactPlatform[empty]: id=$contactId key=$fieldKey no-op (absent)")
                        return@withContext
                    }
                    val inverseEntry = PlatformEntry(
                        displayName = existing.displayName,
                        jumpLink = existing.jumpLink,
                        originalLink = existing.originalLink,
                        value = existing.value,
                        avatarUrl = existing.avatarUrl,
                    )
                    optimisticUpdate(
                        contactId = contactId,
                        opType = OperationTypes.REMOVE_PLATFORM,
                        payloadJson = buildJsonObject { addProperty("key", fieldKey) }.toString(),
                        inversePayloadJson = buildJsonObject {
                            addProperty("action", OperationTypes.ADD_PLATFORM)
                            addProperty("key", fieldKey)
                            val entryObj = com.google.gson.JsonObject().apply {
                                addProperty("value", inverseEntry.value)
                                inverseEntry.displayName?.let { addProperty("displayName", it) }
                                addProperty("jumpLink", inverseEntry.jumpLink)
                                inverseEntry.originalLink?.let { addProperty("originalLink", it) }
                                inverseEntry.avatarUrl?.let { addProperty("avatarUrl", it) }
                            }
                            add("entry", entryObj)
                        }.toString(),
                        applyContactCache = { it },
                        applyRelated = { _ ->
                            contactPlatformCacheDao.deleteByContactAndKey(contactId, fieldKey)
                        },
                    )
                    return@withContext
                }
                val existing = contactPlatformCacheDao.getPlatformsByContact(contactId)
                    .firstOrNull { it.platformKey == fieldKey }
                val opType = if (existing == null) {
                    OperationTypes.ADD_PLATFORM
                } else {
                    OperationTypes.UPDATE_PLATFORM
                }
                val inverseEntry: PlatformEntry? = existing?.let {
                    PlatformEntry(
                        displayName = it.displayName,
                        jumpLink = it.jumpLink,
                        originalLink = it.originalLink,
                        value = it.value,
                        avatarUrl = it.avatarUrl,
                    )
                }
                val newPlatform = ContactPlatformCacheEntity(
                    contactId = contactId,
                    platformKey = fieldKey,
                    value = entry.value,
                    displayName = entry.displayName,
                    jumpLink = entry.jumpLink,
                    originalLink = entry.originalLink,
                    avatarUrl = entry.avatarUrl,
                )
                optimisticUpdate(
                    contactId = contactId,
                    opType = opType,
                    payloadJson = buildJsonObject {
                        addProperty("key", fieldKey)
                        val entryObj = com.google.gson.JsonObject().apply {
                            addProperty("value", entry.value)
                            entry.displayName?.let { addProperty("displayName", it) }
                            addProperty("jumpLink", entry.jumpLink)
                            entry.originalLink?.let { addProperty("originalLink", it) }
                            entry.avatarUrl?.let { addProperty("avatarUrl", it) }
                        }
                        add("entry", entryObj)
                    }.toString(),
                    inversePayloadJson = if (inverseEntry == null) {
                        // ADD 反向 = REMOVE
                        """{"action":"REMOVE_PLATFORM","key":"$fieldKey"}"""
                    } else {
                        // UPDATE 反向 = 改回旧 entry
                        buildJsonObject {
                            addProperty("action", "UPDATE_PLATFORM")
                            addProperty("key", fieldKey)
                            val entryObj = com.google.gson.JsonObject().apply {
                                addProperty("value", inverseEntry.value)
                                inverseEntry.displayName?.let { addProperty("displayName", it) }
                                addProperty("jumpLink", inverseEntry.jumpLink)
                                inverseEntry.originalLink?.let { addProperty("originalLink", it) }
                                inverseEntry.avatarUrl?.let { addProperty("avatarUrl", it) }
                            }
                            add("entry", entryObj)
                        }.toString()
                    },
                    applyContactCache = { it }, // 不改 contact 主表
                    applyRelated = { contactCacheDao_unused ->
                        contactPlatformCacheDao.insertPlatform(newPlatform)
                    },
                )
            }
        }
    }

    override suspend fun removeContactPlatform(contactId: Long, fieldKey: String) = contactMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = contactPlatformCacheDao.getPlatformsByContact(contactId)
                .firstOrNull { it.platformKey == fieldKey }
                ?: run {
                    Log.d("Tester", "removeContactPlatform: id=$contactId key=$fieldKey no-op (absent)")
                    return@withContext
                }
            val inverseEntry = PlatformEntry(
                displayName = existing.displayName,
                jumpLink = existing.jumpLink,
                originalLink = existing.originalLink,
                value = existing.value,
                avatarUrl = existing.avatarUrl,
            )
            optimisticUpdate(
                contactId = contactId,
                opType = OperationTypes.REMOVE_PLATFORM,
                payloadJson = buildJsonObject { addProperty("key", fieldKey) }.toString(),
                inversePayloadJson = buildJsonObject {
                    addProperty("action", "ADD_PLATFORM")
                    addProperty("key", fieldKey)
                    val entryObj = com.google.gson.JsonObject().apply {
                        addProperty("value", inverseEntry.value)
                        inverseEntry.displayName?.let { addProperty("displayName", it) }
                        addProperty("jumpLink", inverseEntry.jumpLink)
                        inverseEntry.originalLink?.let { addProperty("originalLink", it) }
                        inverseEntry.avatarUrl?.let { addProperty("avatarUrl", it) }
                    }
                    add("entry", entryObj)
                }.toString(),
                applyContactCache = { it },
                applyRelated = { _ ->
                    contactPlatformCacheDao.deleteByContactAndKey(contactId, fieldKey)
                },
            )
        }
    }

    override suspend fun getAllContactPlatformsGrouped(): Map<Long, List<ContactPlatform>> =
        withContext(Dispatchers.IO) {
            contactPlatformCacheDao.getAllPlatforms()
                .groupBy { it.contactId }
        }

    override suspend fun getContactPlatformKeys(contactId: Long): Set<String> =
        withContext(Dispatchers.IO) {
            contactPlatformCacheDao.getPlatformsByContact(contactId).map { it.platformKey }.toSet()
        }

    override suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        withContext(Dispatchers.IO) {
            contactPlatformCacheDao.getPlatformsByContact(contactId)
        }

    // ========== 重复检测 ==========

    override suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>
    ): DuplicateCheckResult = withContext(Dispatchers.IO) {
        var bestMatch: ContactCacheEntity? = null
        var bestScore = 0f
        var matchedFields = emptyList<String>()

        if (newContactName.isNotBlank()) {
            val exactMatches = contactCacheDao.getContactsByName(newContactName)
            for (contact in exactMatches) {
                if (contact.name.equals(newContactName, ignoreCase = true)) {
                    if (1.0f > bestScore) {
                        bestScore = 1.0f
                        bestMatch = contact
                        matchedFields = listOf("name")
                    }
                }
            }
            if (bestScore < 1.0f) {
                val prefixMatches = contactCacheDao.searchContactsByName(newContactName).first()
                for (contact in prefixMatches) {
                    val nameSimilarity = calculateNameSimilarity(newContactName, contact.name)
                    if (nameSimilarity > 0.7f && nameSimilarity < 1.0f) {
                        val score = nameSimilarity * 0.5f
                        if (score > bestScore) {
                            bestScore = score
                            bestMatch = contact
                            matchedFields = listOf("name")
                        }
                    }
                }
            }
        }

        if (fieldValues.isEmpty() && customFieldValues.isEmpty()) {
            Log.d("Tester", "checkDuplicate: name-only match, bestScore=$bestScore, bestMatch=${bestMatch?.id}, matchedFields=$matchedFields")
            return@withContext DuplicateCheckResult(
                isDuplicate = bestScore >= 1.0f,
                existingContact = bestMatch,
                similarityScore = bestScore.coerceIn(0f, 2f),
                matchFields = matchedFields
            )
        }

        val platformKeys = fieldValues.keys.filter { it in PLATFORM_FIELD_KEYS }.toSet()

        for ((key, value) in fieldValues) {
            if (value.isBlank()) continue

            if (key in platformKeys) {
                val platformDuplicateIds = contactPlatformCacheDao.findContactIdsByPlatform(key, value, -1)
                val platformDuplicates = platformDuplicateIds.mapNotNull { contactCacheDao.getContactById(it) }
                for (contact in platformDuplicates) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    score += 1.0f
                    fields.add(key)
                    val nameSimilarity = calculateNameSimilarity(newContactName, contact.name)
                    if (nameSimilarity > 0.7f) {
                        score += nameSimilarity * 0.5f
                        fields.add("name")
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = contact
                        matchedFields = fields
                    }
                }
            } else {
                val potentialDuplicates = contactCacheDao.searchContacts(value).first()
                for (potential in potentialDuplicates) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    val existingValues = contactFieldValueCacheDao
                        .getFieldValuesByContactOnce(potential.id).map { it.toFieldValue() }
                    for (existingValue in existingValues) {
                        val fieldId = existingValue.fieldId ?: continue
                        val field = contactFieldCacheDao.getFieldById(fieldId)?.toContactField()
                        if (field != null && field.fieldKey == key && existingValue.value == value) {
                            score += 1.0f
                            fields.add(field.fieldName)
                        }
                    }
                    val nameSimilarity = calculateNameSimilarity(newContactName, potential.name)
                    if (nameSimilarity > 0.7f) {
                        score += nameSimilarity * 0.5f
                        fields.add("name")
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = potential
                        matchedFields = fields
                    }
                }
            }
        }

        Log.d("Tester", "checkDuplicate: bestScore=$bestScore, bestMatch=${bestMatch?.id}, matchedFields=$matchedFields")

        DuplicateCheckResult(
            isDuplicate = bestScore >= 1.0f,
            existingContact = bestMatch,
            similarityScore = bestScore.coerceIn(0f, 2f),
            matchFields = matchedFields
        )
    }

    private fun calculateNameSimilarity(name1: String, name2: String): Float {
        if (name1.equals(name2, ignoreCase = true)) return 1.0f
        val set1 = name1.lowercase().toSet()
        val set2 = name2.lowercase().toSet()
        val intersection = set1.intersect(set2).size.toFloat()
        val union = set1.union(set2).size.toFloat()
        return if (union > 0) intersection / union else 0f
    }

    // ========== QAuxv 导入 ==========

    companion object {
        private const val QQ_PLATFORM_KEY = "qq"
        /** QQ 头像源(与 QqAdapter / PlatformIdExtractor 一致)。 */
        private const val QQ_AVATAR_URL_TEMPLATE = "https://q1.qlogo.cn/g?b=qq&nk=%s&s=100"
        /** 头像下载并发上限,避免 N 个 socket 同时打开。 */
        private const val AVATAR_CONCURRENCY = 6
        /** 头像下载超时(毫秒)。 */
        private const val AVATAR_TIMEOUT_MS = 5_000L

        internal fun qqAvatarUrl(uin: Long): String = QQ_AVATAR_URL_TEMPLATE.format(uin)
        internal fun qqAvatarFileName(uin: Long): String = "contact_qq_${uin}_avatar.webp"

        /**
         * 用于测试注入的下载器。默认走 HttpUtil;测试里换成 `{ null }` 跳过实际网络。
         */
        internal var avatarDownloader: suspend (String) -> android.graphics.Bitmap? = {
            HttpUtil.downloadBitmap(it, timeoutMs = AVATAR_TIMEOUT_MS)
        }
    }

    override suspend fun findExistingQQContacts(entries: List<QAuxvFriendEntry>): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext emptyMap()
            val uinStrings = entries.map { it.uin.toString() }.distinct()
            val platforms = contactPlatformCacheDao.getPlatformsByKeyAndValues(QQ_PLATFORM_KEY, uinStrings)
            val map = LinkedHashMap<Long, Long>()
            for (p in platforms) {
                val uin = p.value?.toLongOrNull() ?: continue
                if (!map.containsKey(uin)) {
                    map[uin] = p.contactId
                }
            }
            Log.d("Tester", "findExistingQQContacts: ${entries.size} inputs, ${map.size} existing QQ matches")
            map
        }

    override suspend fun importQAuxvFriends(
        decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>,
        context: Context,
        onProgress: ((QAuxvImportProgress) -> Unit)?,
    ): QAuxvImportSummary {
        Log.d("Tester", "importQAuxvFriends: ${decisions.size} decisions")

        val toDownload = decisions.filter { it.third != QAuxvConflictAction.Skip }.map { it.first }
        val avatarPathByUin = ConcurrentHashMap<Long, String>()
        if (toDownload.isNotEmpty()) {
            onProgress?.invoke(
                QAuxvImportProgress(
                    phase = QAuxvImportProgress.Phase.AvatarDownloading,
                    current = 0, total = toDownload.size,
                )
            )
            val done = AtomicInteger(0)
            val sem = Semaphore(permits = AVATAR_CONCURRENCY)
            coroutineScope {
                for (entry in toDownload) {
                    launch {
                        sem.withPermit {
                            val uin = entry.uin
                            try {
                                val url = qqAvatarUrl(uin)
                                val bmp = avatarDownloader(url)
                                if (bmp != null) {
                                    val file = withContext(Dispatchers.IO) {
                                        Methods.saveBitmapAsAvatar(
                                            context, bmp, qqAvatarFileName(uin)
                                        )
                                    }
                                    avatarPathByUin[uin] = file.absolutePath
                                    Log.d("Tester", "avatar saved uin=$uin → ${file.absolutePath}")
                                    if (!bmp.isRecycled) bmp.recycle()
                                } else {
                                    Log.w("Tester", "avatar download returned null uin=$uin")
                                }
                            } catch (e: Exception) {
                                Log.e("Tester", "avatar download/save failed uin=$uin", e)
                            }
                            val c = done.incrementAndGet()
                            onProgress?.invoke(
                                QAuxvImportProgress(
                                    phase = QAuxvImportProgress.Phase.AvatarDownloading,
                                    current = c, total = toDownload.size,
                                )
                            )
                        }
                    }
                }
            }
        }

        return contactMutex.withLock {
            withContext(Dispatchers.IO) {
                var inserted = 0
                var replaced = 0
                var skipped = 0
                onProgress?.invoke(
                    QAuxvImportProgress(
                        phase = QAuxvImportProgress.Phase.Writing,
                        current = 0, total = decisions.size,
                    )
                )
                for ((index, decision) in decisions.withIndex()) {
                    val (entry, existingId, action) = decision
                    val localAvatar = avatarPathByUin[entry.uin]
                    try {
                        when (action) {
                            QAuxvConflictAction.Skip -> {
                                skipped++
                                Log.d("Tester", "importQAuxvFriends[$index]: skip uin=${entry.uin}")
                            }
                            QAuxvConflictAction.Replace -> {
                                val targetId = existingId?.takeIf { it > 0L }
                                if (targetId == null) {
                                    Log.w("Tester", "importQAuxvFriends[$index]: Replace w/o existingId, fallback to insert uin=${entry.uin}")
                                    insertOne(entry, localAvatar)
                                    inserted++
                                } else {
                                    replaceOne(targetId, entry, localAvatar)
                                    replaced++
                                }
                            }
                            QAuxvConflictAction.InsertAnyway -> {
                                insertOne(entry, localAvatar)
                                inserted++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Tester", "importQAuxvFriends[$index]: failed uin=${entry.uin}", e)
                        skipped++
                    }
                    onProgress?.invoke(
                        QAuxvImportProgress(
                            phase = QAuxvImportProgress.Phase.Writing,
                            current = index + 1, total = decisions.size,
                        )
                    )
                }
                QAuxvImportSummary(inserted = inserted, replaced = replaced, skipped = skipped)
            }
        }
    }

    /** 新增一条 Contact + 一条 QQ Platform 条目。 */
    private suspend fun insertOne(entry: QAuxvFriendEntry, localAvatarPath: String?) {
        val now = System.currentTimeMillis()
        val newContactId = contactCacheDao.insertContact(
            ContactCacheEntity(
                id = 0L,
                name = entry.displayName,
                avatarUrl = qqAvatarUrl(entry.uin),
                avatarPath = localAvatarPath,
                pinyinInitial = PinyinUtils.getContactPinyinInitial(entry.displayName),
                createTime = now,
                updateTime = now,
            )
        )
        contactPlatformCacheDao.insertPlatform(buildQqPlatform(newContactId, entry))
        contactCacheDao.bumpContact(newContactId)
        Log.d(
            "Tester",
            "insertOne: uin=${entry.uin} contactId=$newContactId name='${entry.displayName}' avatarPath=$localAvatarPath",
        )
    }

    /** 替换:更新已有 Contact 的 name + 头像 + QQ Platform 条目。 */
    private suspend fun replaceOne(contactId: Long, entry: QAuxvFriendEntry, localAvatarPath: String?) {
        val latest = contactCacheDao.getContactById(contactId)
        if (latest != null) {
            if (!localAvatarPath.isNullOrBlank() && !latest.avatarPath.isNullOrBlank()
                && latest.avatarPath != localAvatarPath
            ) {
                Methods.deleteAvatarFile(latest.avatarPath)
            }
            val newAvatarPath = localAvatarPath ?: latest.avatarPath
            val newPinyinInitial = if (latest.name == entry.displayName) {
                latest.pinyinInitial
            } else {
                PinyinUtils.getContactPinyinInitial(entry.displayName)
            }
            contactCacheDao.updateContact(
                latest.copy(
                    name = entry.displayName,
                    avatarUrl = qqAvatarUrl(entry.uin),
                    avatarPath = newAvatarPath,
                    pinyinInitial = newPinyinInitial,
                )
            )
            contactCacheDao.bumpContact(contactId)
        }
        contactPlatformCacheDao.insertPlatform(buildQqPlatform(contactId, entry))
        Log.d(
            "Tester",
            "replaceOne: uin=${entry.uin} contactId=$contactId name='${entry.displayName}' avatarPath=$localAvatarPath",
        )
    }

    /**
     * 规范化 pinyinInitial:只要 name 变化或当前 pinyinInitial 与按 name 重算的结果不一致,
     * 就用重算结果覆盖。空 name 退化为 '#'。
     *
     * 为什么放在 Repository 而不是 ViewModel:排序字段是 DB 一致性问题,不该依赖每个 caller
     * 记得去算。Repository 是写入最后一道关口,在此收敛契约。
     */
    private fun normalizePinyinInitial(name: String, currentPinyinInitial: String): String {
        if (name.isBlank()) return currentPinyinInitial.ifBlank { "#" }
        val expected = PinyinUtils.getContactPinyinInitial(name)
        return if (currentPinyinInitial == expected) currentPinyinInitial else expected
    }

    private fun buildQqPlatform(contactId: Long, entry: QAuxvFriendEntry): ContactPlatformCacheEntity {
        val uin = entry.uin.toString()
        val jumpLink = buildPlatformLink(QQ_PLATFORM_KEY, uin)
        return ContactPlatformCacheEntity(
            contactId = contactId,
            platformKey = QQ_PLATFORM_KEY,
            value = uin,
            displayName = entry.displayName,
            jumpLink = jumpLink,
            originalLink = null,
            avatarUrl = qqAvatarUrl(entry.uin),
        )
    }

    // ========== [V2-P5] optimisticUpdate 模板(对齐 §5.5.4 写入顺序) ==========

    /**
     * [V2-P5] 通用乐观更新模板(对应 `docs/BADGER_V2_CLIENT_PLAN.md` §5.5.4)。
     *
     * 严格按 §5.5.4 红线顺序执行:
     * ```
     * 1. snapshotBefore = ContactSnapshotter.toJsonFromCache(contactId)   // 当前态
     * 2. pendingDao.enqueue(op)                                           // 先入队,绝不丢
     * 3. historyDao.insert(history)                                       // 历史/撤销入口
     * 4. contactCacheDao.update(optimistic) + bumpContact                  // 改 cache + invalidation
     * 5. pendingUploadScheduler.kick()                                    // 触发 Worker
     * ```
     *
     * 参数:
     * - [applyContactCache] 改 Contact 主表的变换(用于 rename / bio 等)。
     * - [applyRelated] 改关联子表(platform / field / tag)的副作用。
     *
     * [修复防御]:由调用方负责 no-op 短路(名字相等 / bio 相等就别调本方法),
     * 否则会产生一条"无效 op"白占队列容量。
     *
     * @param canUndo 是否可在 P7 历史页撤销(rename/bio 可,CREATE 也可撤销=DELETE)。
     * @param canReplay 是否可"再次执行"(撤销不可逆的 DELETE 设 false)。
     */
    private suspend fun optimisticUpdate(
        contactId: Long,
        opType: String,
        payloadJson: String,
        inversePayloadJson: String,
        applyContactCache: suspend (ContactCacheEntity) -> ContactCacheEntity,
        applyRelated: suspend (ContactCacheEntity) -> Unit = {},
        canUndo: Boolean = true,
        canReplay: Boolean = false,
    ) {
        val current = contactCacheDao.getContactById(contactId) ?: run {
            Log.w("Tester", "optimisticUpdate[$opType] id=$contactId skipped: contact not found")
            return
        }
        val opId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 1. snapshotBefore(独立 try/catch:ContactSnapshotter 内部已降级兜底,这里再兜一层)
        val snapshotBefore = try {
            contactSnapshotter.toJsonFromCache(contactId, now)
        } catch (e: Exception) {
            Log.e("Tester", "optimisticUpdate[$opType] snapshot failed, fallback {}", e)
            "{}"
        }

        // 2. pendingDao.enqueue(op)
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = contactId,
                opType = opType,
                resourceVersion = current.serverVersion,
                payloadJson = payloadJson,
                createdAt = now,
                status = "PENDING",
                deviceId = deviceIdProvider.deviceId(),
            )
        )

        // 3. historyDao.insert(history)
        historyDao.insert(
            OperationHistoryEntity(
                opId = opId,
                contactId = contactId,
                opType = opType,
                opLabel = OperationTypes.labelOf(opType),
                payloadJson = payloadJson,
                snapshotBeforeJson = snapshotBefore,
                snapshotAfterJson = null,
                createdAt = now,
                opStatus = "PENDING",
                inversePayloadJson = inversePayloadJson,
                canUndo = canUndo,
                canReplay = canReplay,
            )
        )

        // 4. apply optimistic cache + 关联子表
        val optimistic = applyContactCache(current)
        contactCacheDao.updateContact(optimistic)
        applyRelated(optimistic)
        contactCacheDao.bumpContact(contactId)

        // 5. kick
        pendingUploadScheduler.kick()
        Log.d(
            "Tester",
            "optimisticUpdate[$opType] opId=${opId.take(8)} contactId=$contactId " +
                "snapshotBytes=${snapshotBefore.length}",
        )
    }

    // ========== [V2-P5] JsonObject 私有便捷封装 ==========

    /**
     * 用 Kotlin build 方式构造 JsonObject(避免重复 import com.google.gson.JsonObject.*)。
     * 这里 inline 包一层只是为了调用方少 6 行;不在公共 API 暴露。
     */
    private inline fun buildJsonObject(builder: com.google.gson.JsonObject.() -> Unit): com.google.gson.JsonObject =
        com.google.gson.JsonObject().apply(builder)
}