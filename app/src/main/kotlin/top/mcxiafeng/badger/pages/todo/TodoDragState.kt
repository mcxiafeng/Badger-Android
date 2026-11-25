package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing

internal class TodoDragSection(rows: List<TodoRowState>) {
    val ids = rows.map { it.item.id }
    val index = ids.withIndex().associate { (index, id) -> id to index }
}

internal data class TodoDropTarget(val id: String, val after: Boolean)

private data class TodoDrag(
    val id: String, val section: TodoDragSection, val grabOffset: Float, val rowHeight: Int, val pointerY: Float,
    val target: TodoDropTarget? = null,
)

internal class TodoDragState(private val list: LazyListState, private val bottomInset: Float, private val edge: Float) {
    private var drag by mutableStateOf<TodoDrag?>(null)
    val activeId get() = drag?.id
    val target get() = drag?.target
    val translationY: Float get() {
        val current = drag ?: return 0f
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == current.id } ?: return 0f
        val top = (current.pointerY - current.grabOffset).coerceIn(0f, (bottom - item.size).coerceAtLeast(0f))
        return top - topOf(item)
    }
    private val bottom get() = (list.layoutInfo.viewportSize.height - bottomInset).coerceAtLeast(0f)

    fun itemAt(y: Float): String? = list.layoutInfo.visibleItemsInfo.firstOrNull {
        y >= topOf(it) && y < topOf(it) + it.size && y < bottom
    }?.key as? String

    fun start(id: String, section: TodoDragSection, grabY: Float, pointerY: Float) {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        drag = TodoDrag(id, section, grabY - topOf(item), item.size, pointerY)
        update(pointerY)
        Log.d(TAG, "Started vertical task reorder")
    }

    fun update(y: Float) {
        val current = drag ?: return
        val rowCenter = y - current.grabOffset + current.rowHeight / 2f
        val closest = list.layoutInfo.visibleItemsInfo.asSequence()
            .filter { item ->
                val id = item.key as? String
                id != null && id in current.section.index && topOf(item) < bottom && topOf(item) + item.size > 0f
            }
            .minByOrNull { abs(rowCenter - (topOf(it) + it.size / 2f)) }
        val anchor = closest?.key as? String
        val target = if (anchor != null && anchor != current.id) {
            TodoDropTarget(anchor, current.section.index.getValue(anchor) > current.section.index.getValue(current.id))
        } else null
        drag = current.copy(pointerY = y, target = target)
    }

    fun finish(commit: Boolean, onMove: (String, String, Boolean) -> Unit) {
        val current = drag ?: return
        drag = null
        val target = current.target
        if (commit && target != null) onMove(current.id, target.id, target.after)
        Log.d(TAG, "Finished task reorder; moved=${commit && target != null}")
    }

    suspend fun autoScroll() {
        while (drag != null) {
            withFrameNanos { }
            val current = drag ?: return
            val delta = edgeScroll(current)
            if (delta != 0f && list.scrollBy(delta) != 0f) drag?.let { update(it.pointerY) }
        }
    }

    private fun edgeScroll(current: TodoDrag): Float {
        val visible = list.layoutInfo.visibleItemsInfo
        val first = visible.find { it.key == current.section.ids.first() }
        val last = visible.find { it.key == current.section.ids.last() }
        val speed = edge / SCROLL_SPEED_DIVISOR
        return when {
            current.pointerY < edge && (first == null || topOf(first) < 0f) ->
                -speed * (1f - current.pointerY / edge).coerceIn(0f, 1f)
            current.pointerY > bottom - edge && (last == null || topOf(last) + last.size > bottom) ->
                speed * ((current.pointerY - (bottom - edge)) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    private fun topOf(item: LazyListItemInfo) = (item.offset - list.layoutInfo.viewportStartOffset).toFloat()
}

@Composable
internal fun Modifier.todoDragInput(
    drag: TodoDragState, pending: TodoDragSection?, completed: TodoDragSection?, onMove: (String, String, Boolean) -> Unit,
): Modifier {
    val start = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sections by rememberUpdatedState(listOfNotNull(pending, completed))
    val move by rememberUpdatedState(onMove)
    return pointerInput(drag, start) {
        val handleEdge = BadgerSize.minimumTouchTarget.toPx()
        val padding = BadgerSpacing.xl.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val x = if (start) down.position.x else size.width - down.position.x
            if (x < padding || x >= padding + handleEdge) return@awaitEachGesture
            val id = drag.itemAt(down.position.y) ?: return@awaitEachGesture
            val section = sections.find { it.ids.size > 1 && id in it.index } ?: return@awaitEachGesture
            val first = awaitDragSlop(down) ?: return@awaitEachGesture
            drag.start(id, section, down.position.y, first.position.y)
            var finished = false
            try {
                finished = trackDrag(first, drag)
            } finally {
                drag.finish(finished, move)
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitDragSlop(down: PointerInputChange): PointerInputChange? {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.find { it.id == down.id } ?: return null
        if (!change.pressed || change.isConsumed) return null
        val delta: Offset = change.position - down.position
        if (abs(delta.x) > slop && abs(delta.x) >= abs(delta.y)) return null
        if (abs(delta.y) > slop) {
            change.consume()
            return change
        }
    }
}

private suspend fun AwaitPointerEventScope.trackDrag(first: PointerInputChange, drag: TodoDragState): Boolean {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.find { it.id == first.id } ?: return false
        if (change.isConsumed) return false
        change.consume()
        drag.update(change.position.y)
        if (!change.pressed) return true
    }
}

private const val SCROLL_SPEED_DIVISOR = 5f
private const val TAG = "TodoDragStateTester"
