package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.BadgerApplication
import top.mcxiafeng.badger.ai.AiTagException
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.model.MergeChoice
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.CommitResult
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.ContactWriter
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import org.opencv.OpenCV
import com.king.wechat.qrcode.WeChatQRCodeDetector

/**
 * 扫描页 ViewModel：写路径只走 [ContactWriter]，不再向页面暴露 Repository。
 */
class ScannerViewModel : ViewModel() {

    private val contactWriter: ContactWriter = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val contactRepository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val fieldRepository: FieldRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val tagRepository: TagRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val aiTagGenerator: AiTagGenerator = top.mcxiafeng.badger.di.KoinComponentBy.get()

    /** ResultDialog 读路径（查重 / 字段 map），不用于写库。 */
    fun contactReadRepository(): ContactRepository = contactRepository
    fun fieldReadRepository(): FieldRepository = fieldRepository
    fun tagReadRepository(): TagRepository = tagRepository

    /**
     * 页面确认入口：在 viewModelScope 写库，完成后主线程回调。离开页面不会取消。
     */
    fun confirmScanAndThen(
        selectedItems: List<Pair<String, ExtractedContactInfo>>,
        existingContact: Contact?,
        conflictResolutions: Map<String, MergeChoice>,
        markerConfig: ScanMarkerConfig,
        collectionId: Long?,
        sourceType: String,
        onDone: (CommitResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = confirmScan(
                selectedItems, existingContact, conflictResolutions,
                markerConfig, collectionId, sourceType,
            )
            withContext(Dispatchers.Main) { onDone(result) }
        }
    }

    fun attachScanAndThen(
        contact: Contact,
        info: ExtractedContactInfo,
        markerConfig: ScanMarkerConfig,
        collectionId: Long?,
        onDone: (CommitResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = attachScan(contact, info, markerConfig, collectionId)
            withContext(Dispatchers.Main) { onDone(result) }
        }
    }

    /**
     * 确认保存：await 写库完成后再返回。失败不吞，调用方 Toast。
     */
    suspend fun confirmScan(
        selectedItems: List<Pair<String, ExtractedContactInfo>>,
        existingContact: Contact?,
        conflictResolutions: Map<String, MergeChoice>,
        markerConfig: ScanMarkerConfig,
        collectionId: Long?,
        sourceType: String,
    ): CommitResult {
        Log.d(TAG, "confirmScan: items=${selectedItems.size} existing=${existingContact?.id} source=$sourceType")
        val firstInfo = selectedItems.firstOrNull()?.second
            ?: return CommitResult.SentFailed("empty selection")
        val savedIds = mutableListOf<Long>()
        val isNewBatch = existingContact == null
        val result: CommitResult = if (existingContact != null) {
            val entries = contactWriter.buildMergeEntries(existingContact.id, firstInfo)
            val newName = if (firstInfo.name != null && firstInfo.name != existingContact.name) firstInfo.name else null
            val resolved = entries.map { entry ->
                val resolution = conflictResolutions[entry.fieldKey]
                if (resolution != null) entry.copy(selectedValue = resolution) else entry
            }
            val mergeResult = contactWriter.mergeScanned(
                existingContactId = existingContact.id,
                newInfo = firstInfo,
                mergeEntries = resolved,
                collectionId = collectionId ?: 0L,
                sourceType = sourceType,
                chosenName = newName,
            )
            if (mergeResult is CommitResult.Written) savedIds += mergeResult.contactId
            mergeResult
        } else {
            var last: CommitResult = CommitResult.SentFailed("no items")
            for ((_, info) in selectedItems) {
                val now = System.currentTimeMillis()
                val contact = Contact(
                    id = 0L,
                    name = info.name ?: "未知联系人",
                    avatarUrl = info.avatarUrl,
                    createTime = now,
                    updateTime = now,
                )
                last = contactWriter.saveScanned(contact, info, sourceType, collectionId)
                if (last is CommitResult.Written) {
                    savedIds += last.contactId
                } else {
                    Log.e(TAG, "confirmScan: save failed $last")
                    return last
                }
            }
            last
        }
        if (result is CommitResult.Written || result is CommitResult.SentSuccess) {
            applyMarker(savedIds, markerConfig)
            if (isNewBatch) scheduleAiTags(savedIds)
        }
        return result
    }

    suspend fun attachScan(
        contact: Contact,
        info: ExtractedContactInfo,
        markerConfig: ScanMarkerConfig,
        collectionId: Long?,
    ): CommitResult {
        Log.d(TAG, "attachScan: contactId=${contact.id} platforms=${info.platforms.keys}")
        val result = if (info.platforms.isNotEmpty() || info.phone != null || info.email != null) {
            contactWriter.attachScanned(
                existingContactId = contact.id,
                info = info,
                selectedFields = info.toFieldValues().keys.toList(),
                collectionId = collectionId,
            )
        } else {
            val fresh = contactRepository.getContactById(contact.id) ?: contact
            contactRepository.updateContact(fresh.copy(updateTime = System.currentTimeMillis()))
            CommitResult.Written(contact.id)
        }
        if (result is CommitResult.Written) {
            applyMarker(listOf(result.contactId), markerConfig)
        }
        return result
    }

    private suspend fun applyMarker(contactIds: List<Long>, markerConfig: ScanMarkerConfig) {
        if (!markerConfig.enabled || markerConfig.tagId == null) return
        contactIds.forEach { cid ->
            try {
                tagRepository.addTagToContact(cid, markerConfig.tagId)
                Log.d(TAG, "applyMarker: tagId=${markerConfig.tagId} -> contactId=$cid")
            } catch (e: Exception) {
                Log.e(TAG, "applyMarker failed cid=$cid", e)
            }
        }
    }

    private fun scheduleAiTags(contactIds: List<Long>) {
        contactIds.forEach { cid ->
            viewModelScope.launch {
                try {
                    val bio = contactRepository.getContactById(cid)?.bio
                    if (bio.isNullOrBlank()) {
                        Log.d(TAG, "后台 AI 打标跳过: contactId=$cid 无 bio")
                        return@launch
                    }
                    val existingTags = tagRepository.getAllTagsOnce()
                    val candidates = try {
                        aiTagGenerator.suggest(bio, existingTags)
                    } catch (e: AiTagException) {
                        Log.w(TAG, "后台 AI 失败,降级 fallbackLocal: cid=$cid, ${e.message}")
                        aiTagGenerator.fallbackLocal(bio, existingTags)
                    }
                    candidates.forEach { c ->
                        val tagId = if (c.matchedExisting && c.existingTagId != null) {
                            c.existingTagId
                        } else {
                            tagRepository.upsertTag(c.name, c.color, source = "ai")
                        }
                        tagRepository.addTagToContact(cid, tagId)
                    }
                    Log.d(TAG, "后台 AI 打标完成: contactId=$cid, candidates=${candidates.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "后台 AI 打标失败: contactId=$cid", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ScannerViewModel"
        private val initMutex = Mutex()
        @Volatile
        private var openCvInitialized = false

        internal fun isOpenCvInitialized(): Boolean = openCvInitialized
    }

    private suspend fun ensureOpenCvInitialized() {
        if (openCvInitialized) return
        initMutex.withLock {
            if (openCvInitialized) return@withLock
            try {
                OpenCV.initOpenCV()
                WeChatQRCodeDetector.init(BadgerApplication.getInstance())
            } catch (e: IllegalStateException) {
                Log.w(TAG, "WeChatQRCode 懒加载跳过（Application 未就绪，可能是测试环境）", e)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "WeChatQRCode 懒加载跳过（native 库未加载，可能是测试环境）", e)
            }
            openCvInitialized = true
        }
    }
}
