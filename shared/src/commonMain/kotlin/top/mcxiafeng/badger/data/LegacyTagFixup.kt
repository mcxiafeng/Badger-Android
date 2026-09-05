package top.mcxiafeng.badger.data

import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * 一次性启动副作用:补齐 v4→v5 迁移时遗留 Tag 的 pinyinInitial。
 *
 * 背景:SQLite 无法在纯 SQL 里算汉字拼音。Migration 4_5 把老 scan_results.styleColor
 * 转成 `遗留样式_${colorHex}_${contactName}` Tag 行时,pinyinInitial 留空,
 * 导致 PersonPage 侧边字母索引把这些 tag 排到 '#' 桶或非字母桶,排序视觉混乱。
 *
 * 实现:在 App 启动时(BadgerApplication),遍历 `source == "legacy" && pinyinInitial.isBlank()`
 * 的 tag,调 [TagRepository.recomputePinyinInitial] 用 Kotlin 端的
 * [top.mcxiafeng.badger.utils.PinyinUtils.getContactPinyinInitial] 重算。
 *
 * 注:[TagRepository.renameTag] 已对改名操作同步重算 pinyinInitial,
 * 但用户在 TagManagerDialog 改名是低频事件;启动期一次性 batch 重算可让"从未改名"
 * 的历史遗留 tag 立即落入正确桶位。
 *
 * 异常保护:任何失败都不影响主流程,只记录日志。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::LegacyTagFixup)`。
 */
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
                    // [修复防御]: 单条失败不应中断整体流程,记录失败 id 供排查。
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
