package top.mcxiafeng.badger.pages.person

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonScrollRestorePolicyTest {

    // ==================== 未锁存时 noop ====================

    @Test
    fun noPending_noop() {
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 30,
            currentIndex = 12,
            currentOffset = 50,
            pendingIndex = null,
            pendingOffset = 0,
            pendingItemCount = 0,
            stableTicks = 0,
        )
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.Noop)
        assertEquals(0, result.stableTicks)
    }

    // ==================== itemCount 减少 → 恢复 ====================

    @Test
    fun pendingAndItemCountShrunk_restoresScrollPosition() {
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 29,
            currentIndex = 12,
            currentOffset = 50,
            pendingIndex = 12,
            pendingOffset = 50,
            pendingItemCount = 30,
            stableTicks = 0,
        )
        assertNull(result.pendingIndex)
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.ScrollTo)
        val action = result.Action as PersonScrollRestorePolicy.Action.ScrollTo
        assertEquals(12, action.index)
        assertEquals(50, action.offset)
    }

    @Test
    fun pendingAndItemCountShrunk_largeEnoughToKeepIndex() {
        // 删除前 270 条，pendingIndex=174；删除后稳定 214，仍 ≥ 174，可恢复
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 214,
            currentIndex = 0,
            currentOffset = 0,
            pendingIndex = 174,
            pendingOffset = 15,
            pendingItemCount = 270,
            stableTicks = 0,
        )
        assertNull(result.pendingIndex)
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.ScrollTo)
        val action = result.Action as PersonScrollRestorePolicy.Action.ScrollTo
        assertEquals(174, action.index)
        assertEquals(15, action.offset)
    }

    // ==================== itemCount 稳定 → 兜底恢复 ====================

    @Test
    fun pendingAndItemCountStableTwoTicks_fallsBackToRestore() {
        val firstTick = PersonScrollRestorePolicy.decide(
            itemCount = 30,
            currentIndex = 12,
            currentOffset = 50,
            pendingIndex = 12,
            pendingOffset = 50,
            pendingItemCount = 30,
            stableTicks = 0,
        )
        assertTrue(firstTick.Action is PersonScrollRestorePolicy.Action.Noop)
        assertEquals(12, firstTick.pendingIndex)
        assertEquals(1, firstTick.stableTicks)

        val secondTick = PersonScrollRestorePolicy.decide(
            itemCount = 30,
            currentIndex = 12,
            currentOffset = 50,
            pendingIndex = 12,
            pendingOffset = 50,
            pendingItemCount = 30,
            stableTicks = 1,
        )
        assertNull(secondTick.pendingIndex)
        assertTrue(secondTick.Action is PersonScrollRestorePolicy.Action.ScrollTo)
        val action = secondTick.Action as PersonScrollRestorePolicy.Action.ScrollTo
        assertEquals(12, action.index)
        assertEquals(50, action.offset)
    }

    @Test
    fun pendingAndItemCountShrunk_resetsStableTicks() {
        // itemCount 减少 → 走"减少"分支立即恢复，不走稳定拍数
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 28,
            currentIndex = 12,
            currentOffset = 50,
            pendingIndex = 12,
            pendingOffset = 50,
            pendingItemCount = 30,
            stableTicks = 1,
        )
        assertNull(result.pendingIndex)
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.ScrollTo)
    }

    // ==================== 恢复目标越界 → clamp 到末尾 ====================

    @Test
    fun restoreTargetOutOfBounds_clampsToLastItemInsteadOfResettingToZero() {
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 5,
            currentIndex = 0,
            currentOffset = 0,
            pendingIndex = 12,
            pendingOffset = 50,
            pendingItemCount = 30,
            stableTicks = 0,
        )
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.ScrollTo)
        val action = result.Action as PersonScrollRestorePolicy.Action.ScrollTo
        // 越界时不归零，而是 clamp 到 itemCount-1=4
        assertEquals(4, action.index)
        // 末尾位置 offset 清零，避免越界
        assertEquals(0, action.offset)
        assertNull(result.pendingIndex)
    }

    @Test
    fun restoreTargetExactlyAtItemCount_clampsToLastItem() {
        // pendingIndex == itemCount 时越界，clamp 到 itemCount-1
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 30,
            currentIndex = 0,
            currentOffset = 0,
            pendingIndex = 30,
            pendingOffset = 0,
            pendingItemCount = 30,
            stableTicks = 5,
        )
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.ScrollTo)
        val action = result.Action as PersonScrollRestorePolicy.Action.ScrollTo
        assertEquals(29, action.index)
    }

    @Test
    fun restoreTargetLegitimate_keepsOffset() {
        // 未越界时保留 offset（itemCount 减少一档，12 < 29 合法）
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 29,
            currentIndex = 0,
            currentOffset = 0,
            pendingIndex = 12,
            pendingOffset = 50,
            pendingItemCount = 30,
            stableTicks = 0,
        )
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.ScrollTo)
        val action = result.Action as PersonScrollRestorePolicy.Action.ScrollTo
        assertEquals(12, action.index)
        assertEquals(50, action.offset)
    }

    // ==================== itemCount 增长 → 不恢复，锁存保持 ====================

    @Test
    fun pendingButItemCountGrew_resetsStableTicks() {
        // 反常：itemCount 增长（如批量导入），不触发恢复，但保持锁存状态
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 35,
            currentIndex = 12,
            currentOffset = 50,
            pendingIndex = 12,
            pendingOffset = 50,
            pendingItemCount = 30,
            stableTicks = 1,
        )
        assertEquals(12, result.pendingIndex)
        assertEquals(0, result.stableTicks)
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.Noop)
    }

    @Test
    fun pendingButItemCountSameFirstTick_keepsPendingAndIncrementsTicks() {
        // 第一次稳定拍（itemCount 等于 pendingItemCount）
        val result = PersonScrollRestorePolicy.decide(
            itemCount = 30,
            currentIndex = 12,
            currentOffset = 50,
            pendingIndex = 12,
            pendingOffset = 50,
            pendingItemCount = 30,
            stableTicks = 0,
        )
        assertEquals(12, result.pendingIndex)
        assertEquals(1, result.stableTicks)
        assertTrue(result.Action is PersonScrollRestorePolicy.Action.Noop)
    }
}
