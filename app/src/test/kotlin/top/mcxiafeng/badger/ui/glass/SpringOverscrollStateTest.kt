package top.mcxiafeng.badger.ui.glass

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.testutil.MainDispatcherRule

class SpringOverscrollStateTest {
    private val max = 252f

    @get:Rule val main = MainDispatcherRule()

    private fun state() = SpringOverscrollState(max, CoroutineScope(main.testDispatcher))

    @Test
    fun leftoverDeltaBecomesVisibleTravelAndAccumulatesInTheSameDirection() = runTest {
        val state = state()

        state.stretch(40f)
        val first = state.offset
        assertThat(first).isGreaterThan(0f)

        state.stretch(40f)
        assertThat(state.offset).isGreaterThan(first)
    }

    @Test
    fun repeatedPullsNeverExceedTheCap() = runTest {
        val state = state()
        repeat(200) { state.stretch(50f) }
        assertThat(state.offset).isAtMost(max)

        val down = state()
        repeat(200) { down.stretch(-50f) }
        assertThat(down.offset).isAtLeast(-max)
    }

    @Test
    fun pullingBackReleasesTravelBeforeTheListMoves() = runTest {
        val state = state()
        state.stretch(100f)
        val held = state.offset

        val released = state.unwind(-30f)
        // The band gives back travel rather than letting the list scroll first.
        assertThat(released).isLessThan(0f)
        assertThat(state.offset).isLessThan(held)
        assertThat(state.offset).isGreaterThan(0f)
    }

    @Test
    fun unwindConsumesOnlyUntilTheBandIsGoneThenReturnsZero() = runTest {
        val state = state()
        state.stretch(30f)
        val held = state.offset

        assertThat(state.unwind(-held - 10f)).isWithin(0.001f).of(-held)
        assertThat(state.offset).isEqualTo(0f)
        // Nothing left to release: the remaining delta must reach the list.
        assertThat(state.unwind(-10f)).isEqualTo(0f)
    }

    @Test
    fun stretchingAgainstExistingTravelIsIgnoredBecauseUnwindOwnsThatDirection() = runTest {
        val state = state()
        state.stretch(60f)
        val held = state.offset

        state.stretch(-20f)
        assertThat(state.offset).isEqualTo(held)
    }

    @Test
    fun shortContentStillReachesMaximumTravelBecauseItDoesNotDependOnScrollRange() = runTest {
        // A single short task never scrolls, so every unit of pull is leftover delta.
        val state = state()
        repeat(30) { state.stretch(20f) }
        assertThat(state.offset).isGreaterThan(max * 0.5f)
    }
}
