package top.mcxiafeng.badger.pages.person

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 删除联系人时的 filter set 时序策略。
 *
 * 实际承担三段职责：
 * 1. 用户点删除时，updateDeleteSet 把 ids 加入集合，立即让 PagingData.filter 流生效。
 * 2. DB 写完后（PagingSource.invalidate 完成），clearAfterInvalidate 清空集合，
 *    避免出现"DB 里有但 UI 不可见"的窗口期。
 * 3. 防呆：如果同一个 id 短时间内被多次加入，set 不会重复。
 *
 * 该策略不依赖 PagingData 实例，可在纯 JVM 单元测试中验证状态机。
 */
internal object PersonDeleteFilterPolicy {

    /**
     * 把要删除的 ids 累加到现有集合，返回新的集合（用于 StateFlow.value 赋值）。
     * 空 ids 直接返回原集合。
     */
    fun updateDeleteSet(current: Set<Long>, idsToDelete: List<Long>): Set<Long> {
        if (idsToDelete.isEmpty()) return current
        return current + idsToDelete.toSet()
    }

    /**
     * 判定 invalidate 等待时间是否已到，可以清空 filter 集合。
     * 用 throttle 形式让清空操作不与下一次 deleteContacts 冲突。
     */
    fun shouldClearNow(elapsedSinceLastDeleteMs: Long, thresholdMs: Long = 200L): Boolean =
        elapsedSinceLastDeleteMs >= thresholdMs
}

/**
 * 单元测试：覆盖 Policy 函数的前置/后置条件。
 */
class PersonDeleteFilterPolicyTest {

    @Test
    fun updateDeleteSet_addsNewIds() {
        val result = PersonDeleteFilterPolicy.updateDeleteSet(
            current = setOf(1L, 2L),
            idsToDelete = listOf(3L, 4L),
        )
        assertEquals(setOf(1L, 2L, 3L, 4L), result)
    }

    @Test
    fun updateDeleteSet_dedupesOverlapping() {
        val result = PersonDeleteFilterPolicy.updateDeleteSet(
            current = setOf(1L, 2L),
            idsToDelete = listOf(2L, 3L),
        )
        assertEquals(setOf(1L, 2L, 3L), result)
    }

    @Test
    fun updateDeleteSet_emptyIdsReturnsCurrent() {
        val current = setOf(1L, 2L)
        val result = PersonDeleteFilterPolicy.updateDeleteSet(current, emptyList())
        assertEquals(current, result)
    }

    @Test
    fun shouldClearNow_belowThreshold_false() {
        assertFalse(PersonDeleteFilterPolicy.shouldClearNow(199L))
    }

    @Test
    fun shouldClearNow_atThreshold_true() {
        assertTrue(PersonDeleteFilterPolicy.shouldClearNow(200L))
    }

    @Test
    fun shouldClearNow_aboveThreshold_true() {
        assertTrue(PersonDeleteFilterPolicy.shouldClearNow(500L))
    }
}
