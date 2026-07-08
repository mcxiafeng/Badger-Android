package top.mcxiafeng.badger.pages.person

/**
 * 联系人列表的滚动恢复策略。
 *
 * 背景：删除联系人时 PagingSource.invalidate 会让 LazyColumn 内部的 item diff 重新
 * 映射 key，原 firstVisibleItemIndex 指向已删除的 key，Compose fallback 把滚动位置
 * 重置为 0，UI 表现为「删除后跳到顶部」。
 *
 * 修复策略：在删除动作按下瞬间主动锁存 listState 位置（不依赖 itemCount 跌到 0，
 * 那个假设对 Paging 3 不成立），等 itemCount 出现一次"减少"后下一次再变化（或
 * 不变）时把滚动位置恢复回去。越界（list 缩短导致位置不合法）则归零。
 *
 * 把判定逻辑抽成纯函数，方便 JVM 单元测试覆盖四个分支：
 *   1. lockNow == true → 锁存 currentIndex/Offset，等后续 itemCount 变化恢复
 *   2. 锁存过 + 当前 itemCount < 锁存时的 itemCount → 数据已缩短，恢复滚动位置
 *   3. 锁存过 + itemCount 已稳定 2 拍 → 兜底恢复（避免 Paging 用 append 偷换时不触发 #2）
 *   4. 恢复目标越界 → 归零
 */
internal object PersonScrollRestorePolicy {

    /** 决定要执行的滚动动作 */
    sealed class Action {
        /** 滚动到 [index]（[offset] 为 0 时等价于首行） */
        data class ScrollTo(val index: Int, val offset: Int) : Action()
        /** 不做任何事（保持当前状态） */
        data object Noop : Action()
    }

    /**
     * 恢复判定：锁存值已由 Composable 在删除按钮按下瞬间同步写入。
     * 本函数只负责"itemCount 减少 → 立即恢复"或"itemCount 稳定 2 拍 → 兜底恢复"两种恢复路径。
     * 恢复目标越界则 clamp 到 itemCount-1（保持用户最末视觉位置），避免归零造成跳顶。
     */
    fun decide(
        itemCount: Int,
        currentIndex: Int,
        currentOffset: Int,
        pendingIndex: Int?,
        pendingOffset: Int,
        pendingItemCount: Int,
        stableTicks: Int,
    ): Result {
        // 未锁存：保持
        if (pendingIndex == null) {
            return Result(pendingIndex, pendingOffset, pendingItemCount, stableTicks, Action.Noop)
        }

        // 分支 1：itemCount 减少（被删了一条/多条）→ 数据已缩短，恢复滚动位置
        if (itemCount in 1 until pendingItemCount) {
            return clampRestore(pendingIndex, pendingOffset, itemCount)
        }

        // 分支 2：itemCount 已经稳定 2 拍（连续两次 LaunchedEffect 触发没变化）→ 兜底恢复
        // 原因：Paging 3 的 invalidate 可能用 append 增量更新，itemCount 不一定变。
        // 如果没触发"减少"信号，就用稳定拍数兜底，确保用户感知不到归零。
        val nextStableTicks = if (itemCount == pendingItemCount) stableTicks + 1 else 0
        if (nextStableTicks >= 2) {
            return clampRestore(pendingIndex, pendingOffset, itemCount)
        }

        return Result(pendingIndex, pendingOffset, pendingItemCount, nextStableTicks, Action.Noop)
    }

    /**
     * 把锁存的 (pendingIndex, pendingOffset) 恢复到新的 itemCount 上：
     * - 目标下标越界（>= itemCount 或 < 0）→ clamp 到 [itemCount-1, 0]，而非归零
     * - 目的：删除链上正好把用户当前可见的那几项都删掉时，"保留末尾最近的有效位置"
     *   比"跳回第 0 行"更符合用户视觉期望。
     * - 恢复成功后清空锁存状态。
     */
    private fun clampRestore(
        pendingIndex: Int,
        pendingOffset: Int,
        itemCount: Int,
    ): Result {
        val target = pendingIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        val targetOffset = if (target == itemCount - 1) 0 else pendingOffset.coerceAtLeast(0)
        return Result(null, 0, 0, 0, Action.ScrollTo(target, targetOffset))
    }

    data class Result(
        val pendingIndex: Int?,
        val pendingOffset: Int,
        val pendingItemCount: Int,
        val stableTicks: Int,
        val Action: Action,
    )
}
