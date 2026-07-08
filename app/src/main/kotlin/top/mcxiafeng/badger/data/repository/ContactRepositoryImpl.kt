package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
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
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.ContactFtsDao
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactPlatformDao
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.PinyinUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val contactFieldDao: ContactFieldDao,
    private val customFieldDao: CustomFieldDao,
    private val contactFieldValueDao: ContactFieldValueDao,
    private val scanResultDao: ScanResultDao,
    private val contactPlatformDao: ContactPlatformDao,
    private val contactFtsDao: ContactFtsDao
) : ContactRepository {

    private val contactMutex = Mutex()

    // ========== 联系人基本操作 ==========

    override fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

    override fun getAllContactsPagingSource(): PagingSource<Int, Contact> =
        contactDao.getAllContactsPagingSource()

    override fun searchContactsPagingSource(query: String): Flow<PagingData<Contact>> {
        val ftsQuery = escapeFtsQuery(query)
        Log.d("Tester", "searchContactsPagingSource: raw='$query', fts='$ftsQuery'")
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            if (ftsQuery.isNotEmpty()) {
                contactFtsDao.searchContactsCombinedPagingSource(ftsQuery, query)
            } else {
                // FTS 查询为空（纯特殊字符），退化为仅 LIKE 搜索
                contactDao.searchContactsByNameLikePagingSource(query)
            }
        }.flow
    }

    override fun getLetterIndex(): Flow<List<LetterCount>> =
        contactDao.getLetterIndex()

    override suspend fun getContactById(id: Long): Contact? = withContext(Dispatchers.IO) {
        contactDao.getContactById(id)
    }

    override fun getAllContactsWithFields(): Flow<List<ContactWithFields>> {
        return contactDao.getAllContacts().map { contacts ->
            contacts.map { contact ->
                ContactWithFields(contact, emptyList())
            }
        }
    }

    override suspend fun getContactWithFieldsById(id: Long): ContactWithFields? = withContext(Dispatchers.IO) {
        val contact = contactDao.getContactById(id) ?: return@withContext null
        val fieldValues = contactFieldValueDao.getFieldValuesByContactOnce(id)

        val fieldIds = fieldValues.mapNotNull { it.fieldId }.distinct()
        val customFieldIds = fieldValues.mapNotNull { it.customFieldId }.distinct()

        val fieldMap = if (fieldIds.isNotEmpty()) {
            contactFieldDao.getFieldsByIds(fieldIds).filter { it.isEnabled }.associateBy { it.id }
        } else emptyMap()
        val customFieldMap = if (customFieldIds.isNotEmpty()) {
            customFieldDao.getCustomFieldsByIds(customFieldIds).filter { it.isEnabled }.associateBy { it.id }
        } else emptyMap()

        val fields = fieldValues.mapNotNull { value ->
            if (value.fieldId != null) {
                val field = fieldMap[value.fieldId] ?: return@mapNotNull null
                ContactFieldDisplay(
                    valueId = value.id,
                    fieldId = field.id,
                    customFieldId = null,
                    fieldName = field.fieldName,
                    fieldKey = field.fieldKey,
                    icon = field.icon,
                    fieldType = null,
                    value = value.value,
                    sortOrder = field.sortOrder
                )
            } else if (value.customFieldId != null) {
                val customField = customFieldMap[value.customFieldId] ?: return@mapNotNull null
                ContactFieldDisplay(
                    valueId = value.id,
                    fieldId = null,
                    customFieldId = customField.id,
                    fieldName = customField.fieldName,
                    fieldKey = null,
                    icon = null,
                    fieldType = customField.fieldType,
                    value = value.value,
                    sortOrder = customField.sortOrder
                )
            } else null
        }.sortedBy { it.sortOrder }

        ContactWithFields(contact, fields)
    }

    override suspend fun insertContact(contact: Contact): Long = withContext(Dispatchers.IO) {
        val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
            contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
        } else contact
        contactDao.insertContact(withPinyin)
    }

    override suspend fun updateContact(contact: Contact) = withContext(Dispatchers.IO) {
        val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
            contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
        } else contact
        contactDao.updateContact(withPinyin)
        // 兜底触发 PagingSource/Flow 重发，处理 Room 同值更新不通知下游的问题
        contactDao.bumpContact(withPinyin.id)
    }

    override suspend fun deleteContact(contact: Contact) = withContext(Dispatchers.IO) {
        contactDao.deleteContact(contact)
    }

    override suspend fun deleteByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        Log.d("Tester", "ContactRepositoryImpl.deleteByIds: count=${ids.size}")
        contactDao.deleteByIds(ids)
    }

    override fun searchContacts(query: String): Flow<List<Contact>> {
        return if (query.isBlank()) {
            contactDao.getAllContacts()
        } else {
            val ftsQuery = escapeFtsQuery(query)
            Log.d("Tester", "searchContacts: raw='$query', fts='$ftsQuery'")
            if (ftsQuery.isNotEmpty()) {
                contactFtsDao.searchContactsCombined(ftsQuery, query)
            } else {
                // FTS 查询为空，退化到 LIKE 搜索 name + field values
                contactDao.searchContacts(query)
            }
        }
    }

    // ========== 联系人社交平台操作 ==========

    override suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        contactMutex.withLock {
            withContext(Dispatchers.IO) {
                if (entry.jumpLink.isBlank() && entry.value.isNullOrBlank()) {
                    contactPlatformDao.deleteByContactAndKey(contactId, fieldKey)
                } else {
                    contactPlatformDao.insertPlatform(
                        ContactPlatform(
                            contactId = contactId,
                            platformKey = fieldKey,
                            value = entry.value,
                            displayName = entry.displayName,
                            jumpLink = entry.jumpLink,
                            originalLink = entry.originalLink,
                            avatarUrl = entry.avatarUrl
                        )
                    )
                }
            }
        }
    }

    override suspend fun removeContactPlatform(contactId: Long, fieldKey: String) = contactMutex.withLock {
        withContext(Dispatchers.IO) {
            contactPlatformDao.deleteByContactAndKey(contactId, fieldKey)
        }
    }

    override suspend fun getAllContactPlatformsGrouped(): Map<Long, List<ContactPlatform>> =
        withContext(Dispatchers.IO) {
            contactPlatformDao.getAllPlatforms().groupBy { it.contactId }
        }

    override suspend fun getContactPlatformKeys(contactId: Long): Set<String> =
        withContext(Dispatchers.IO) {
            contactPlatformDao.getPlatformsByContact(contactId).map { it.platformKey }.toSet()
        }

    override suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        withContext(Dispatchers.IO) {
            contactPlatformDao.getPlatformsByContact(contactId)
        }

    // ========== 重复检测 ==========

    override suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>
    ): DuplicateCheckResult = withContext(Dispatchers.IO) {
        var bestMatch: Contact? = null
        var bestScore = 0f
        var matchedFields = emptyList<String>()

        // 1. Name matching: use SQL exact match first, then Jaccard for fuzzy
        if (newContactName.isNotBlank()) {
            val exactMatches = contactDao.getContactsByName(newContactName)
            for (contact in exactMatches) {
                if (contact.name.equals(newContactName, ignoreCase = true)) {
                    if (1.0f > bestScore) {
                        bestScore = 1.0f
                        bestMatch = contact
                        matchedFields = listOf("name")
                    }
                }
            }
            // Fuzzy: search contacts starting with similar prefix
            if (bestScore < 1.0f) {
                val prefixMatches = contactDao.searchContactsByName(newContactName).first()
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
                // Use SQL to find platform duplicates directly
                val platformDuplicates = contactPlatformDao.findDuplicatesByPlatform(key, value, -1)
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
                // 组合搜索：FTS 匹配 name + LIKE 匹配字段值/二维码/OCR
                val ftsQuery = escapeFtsQuery(value)
                Log.d("Tester", "checkDuplicate: value='$value', ftsQuery='$ftsQuery'")

                val potentialDuplicates = buildList {
                    addAll(scanResultDao.findPotentialDuplicates(value, null))
                    if (ftsQuery.isNotEmpty()) {
                        addAll(contactFtsDao.searchContactsFtsOnce(ftsQuery, 5))
                    }
                }.distinctBy { it.id }

                for (potential in potentialDuplicates) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    val existingValues = contactFieldValueDao.getFieldValuesByContactOnce(potential.id)
                    for (existingValue in existingValues) {
                        val fieldId = existingValue.fieldId ?: continue
                        val field = contactFieldDao.getFieldById(fieldId)
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

    /** 转义 FTS4 查询，使用前缀匹配支持中文部分搜索 */
    private fun escapeFtsQuery(query: String): String {
        val sanitized = query.replace(Regex("[\"^~]"), "").trim()
        if (sanitized.isBlank()) return ""
        Log.d("Tester", "escapeFtsQuery: raw='$query', sanitized='$sanitized'")
        return "$sanitized*"
    }

    // ========== QAuxv 导入 ==========

    companion object {
        private const val QQ_PLATFORM_KEY = "qq"
        /** QQ 头像源（与 QqAdapter / PlatformIdExtractor 一致）。 */
        private const val QQ_AVATAR_URL_TEMPLATE = "https://q1.qlogo.cn/g?b=qq&nk=%s&s=100"
        /** 头像下载并发上限，避免 N 个 socket 同时打开。 */
        private const val AVATAR_CONCURRENCY = 6
        /** 头像下载超时（毫秒）。 */
        private const val AVATAR_TIMEOUT_MS = 5_000L

        internal fun qqAvatarUrl(uin: Long): String = QQ_AVATAR_URL_TEMPLATE.format(uin)
        internal fun qqAvatarFileName(uin: Long): String = "contact_qq_${uin}_avatar.webp"

        /**
         * 用于测试注入的下载器。默认走 HttpUtil；测试里换成 `{ null }` 跳过实际网络。
         */
        internal var avatarDownloader: suspend (String) -> android.graphics.Bitmap? = {
            HttpUtil.downloadBitmap(it, timeoutMs = AVATAR_TIMEOUT_MS)
        }
    }

    override suspend fun findExistingQQContacts(entries: List<QAuxvFriendEntry>): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext emptyMap()
            val uinStrings = entries.map { it.uin.toString() }.distinct()
            val platforms = contactPlatformDao.getPlatformsByKeyAndValues(QQ_PLATFORM_KEY, uinStrings)
            // 多个联系人可能共享同一 QQ 号（InsertAnyway 路径）；uin → 第一个 contactId。
            // 用于「已有 QQ 联系人」的标记。如果有重复 QQ 号，conflict Dialog 让用户看到全部。
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

        // Phase 1：mutex 外并发下载头像（下载是网络阻塞动作，不能占着 contactMutex）。
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

        // Phase 2：mutex 内逐条写库。avatarPathByUin 已填充（下载失败的项值为 null）。
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
                                    // 防御：UI 应保证 Replace 必有 existingContactId；缺失则降级为新增
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
        val newContactId = contactDao.insertContact(
            Contact(
                name = entry.displayName,
                avatarUrl = qqAvatarUrl(entry.uin),
                avatarPath = localAvatarPath,
                // [修复防御]: 自动填 pinyinInitial；getLetterIndex() 的 WHERE pinyinInitial != ''
                // 会过滤空值，且 ContactDao 的 ORDER BY pinyinInitial ASC 会让空值排到 # 之前，
                // 导致侧边字母索引栏不显示这类联系人。
                pinyinInitial = PinyinUtils.getContactPinyinInitial(entry.displayName),
            )
        )
        contactPlatformDao.insertPlatform(buildQqPlatform(newContactId, entry))
        // [修复防御]: insertContact 不在 @Query/observe 范围内，外部 PagingSource 不会感知新行。
        // bumpContact 通过触发 contacts 表的 UPDATE 让 PagingSource invalidation pipeline 醒来，
        // letterCounts 的 Flow 也跟着重发 → 字母索引栏立即更新。
        contactDao.bumpContact(newContactId)
        Log.d(
            "Tester",
            "insertOne: uin=${entry.uin} contactId=$newContactId name='${entry.displayName}' avatarPath=$localAvatarPath",
        )
    }

    /** 替换：更新已有 Contact 的 name + 头像 + QQ Platform 条目。 */
    private suspend fun replaceOne(contactId: Long, entry: QAuxvFriendEntry, localAvatarPath: String?) {
        val latest = contactDao.getContactById(contactId)
        if (latest != null) {
            // 下载成功才覆盖旧头像；下载失败保留旧 avatarPath。
            // 若换了新文件，清理旧文件避免孤儿。
            if (!localAvatarPath.isNullOrBlank() && !latest.avatarPath.isNullOrBlank()
                && latest.avatarPath != localAvatarPath
            ) {
                Methods.deleteAvatarFile(latest.avatarPath)
            }
            val newAvatarPath = localAvatarPath ?: latest.avatarPath
            // [修复防御]: name 变了 → pinyinInitial 也要重算（拼音首字母可能改变），
            // 否则字母索引栏会停留在旧首字母位置。
            val newPinyinInitial = if (latest.pinyinInitial.isBlank()) {
                PinyinUtils.getContactPinyinInitial(entry.displayName)
            } else latest.pinyinInitial
            contactDao.updateContact(
                latest.copy(
                    name = entry.displayName,
                    avatarUrl = qqAvatarUrl(entry.uin),
                    avatarPath = newAvatarPath,
                    pinyinInitial = newPinyinInitial,
                )
            )
            contactDao.bumpContact(contactId)
        }
        contactPlatformDao.insertPlatform(buildQqPlatform(contactId, entry))
        Log.d(
            "Tester",
            "replaceOne: uin=${entry.uin} contactId=$contactId name='${entry.displayName}' avatarPath=$localAvatarPath",
        )
    }

    private fun buildQqPlatform(contactId: Long, entry: QAuxvFriendEntry): ContactPlatform {
        val uin = entry.uin.toString()
        val jumpLink = buildPlatformLink(QQ_PLATFORM_KEY, uin)
        return ContactPlatform(
            contactId = contactId,
            platformKey = QQ_PLATFORM_KEY,
            value = uin,
            displayName = entry.displayName,
            jumpLink = jumpLink,
            originalLink = null,
            avatarUrl = qqAvatarUrl(entry.uin),
        )
    }
}
