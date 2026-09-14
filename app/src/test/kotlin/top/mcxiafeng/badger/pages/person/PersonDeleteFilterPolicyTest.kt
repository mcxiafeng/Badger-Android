package top.mcxiafeng.badger.pages.person

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal object PersonDeleteFilterPolicy {

    

    fun updateDeleteSet(current: Set<Long>, idsToDelete: List<Long>): Set<Long> {
        if (idsToDelete.isEmpty()) return current
        return current + idsToDelete.toSet()
    }

    

    fun shouldClearNow(elapsedSinceLastDeleteMs: Long, thresholdMs: Long = 200L): Boolean =
        elapsedSinceLastDeleteMs >= thresholdMs
}

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
