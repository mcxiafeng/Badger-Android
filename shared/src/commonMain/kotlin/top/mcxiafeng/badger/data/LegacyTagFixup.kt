package top.mcxiafeng.badger.data

import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.utils.BadgerLog

class LegacyTagFixup(
    private val tagRepository: TagRepository
) {
    suspend fun runOnce() {
        try {
            val pending = tagRepository.getAllTagsOnce()
                .filter { it.source == "legacy" && it.pinyinInitial.isBlank() }
            if (pending.isEmpty()) {
                BadgerLog.d(TAG, "runOnce: 无遗留待补")
                return
            }
            BadgerLog.d(TAG, "runOnce: 开始重算 ${pending.size} 个 legacy tag 的 pinyinInitial")
            var success = 0
            for (tag in pending) {
                try {
                    tagRepository.recomputePinyinInitial(tag.id)
                    success++
                } catch (e: Exception) {
                    
                    BadgerLog.w(TAG, "runOnce: 重算失败 id=${tag.id} name='${tag.name}'", e)
                }
            }
            BadgerLog.d(TAG, "runOnce: 完成,成功 $success/${pending.size}")
        } catch (e: Exception) {
            BadgerLog.w(TAG, "runOnce: 失败(可忽略,不影响主流程)", e)
        }
    }

    companion object {
        private const val TAG = "LegacyTagFixup"
    }
}
