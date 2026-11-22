package top.mcxiafeng.badger.pages.person

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import top.mcxiafeng.badger.data.PersonContactAnchor
import top.mcxiafeng.badger.data.PersonContactRow

/** Counts are bounds only; the database resolves the bookmark's current sort position. */
internal object PersonScrollRestorePolicy {
    data class Target(val index: Int, val offset: Int)
    fun target(rank: Int, itemCount: Int, fixedItems: Int, offset: Int): Target? {
        if (itemCount == 0) return null
        val row = rank.coerceIn(0, itemCount - 1)
        return Target(fixedItems + row, if (rank >= itemCount) 0 else offset.coerceAtLeast(0))
    }
}

@Stable
internal class PersonScrollAnchor {
    var last: PersonContactAnchor? = null
    var pending: PersonContactAnchor? by mutableStateOf(null)
    fun clear() { last = null; pending = null }
}

@Composable
internal fun rememberPersonScrollAnchor(
    rows: LazyPagingItems<PersonContactRow>, listState: LazyListState, fixedItems: Int,
    resolve: suspend (PersonContactAnchor) -> Int,
): PersonScrollAnchor {
    val anchor = remember(rows) { PersonScrollAnchor() }
    ObserveVisibleBookmark(rows, listState, fixedItems, anchor)
    RestoreBookmarkAfterRefresh(rows, listState, fixedItems, anchor, resolve)
    return anchor
}

@Composable
private fun ObserveVisibleBookmark(rows: LazyPagingItems<PersonContactRow>, listState: LazyListState,
    fixedItems: Int, anchor: PersonScrollAnchor) {
    LaunchedEffect(rows, listState, fixedItems) {
        snapshotFlow {
            val index = listState.firstVisibleItemIndex - fixedItems
            val row = if (index in 0 until rows.itemCount) rows.peek(index) else null
            val offset = listState.firstVisibleItemScrollOffset
            val ready = anchor.pending == null && rows.loadState.refresh is LoadState.NotLoading
            Triple(row?.bookmark(offset), index, ready)
        }.collect { (bookmark, index, ready) ->
            if (ready) {
                if (index < 0) anchor.last = null
                else if (bookmark != null) anchor.last = bookmark
            }
        }
    }
}

@Composable
private fun RestoreBookmarkAfterRefresh(rows: LazyPagingItems<PersonContactRow>, listState: LazyListState,
    fixedItems: Int, anchor: PersonScrollAnchor, resolve: suspend (PersonContactAnchor) -> Int) {
    LaunchedEffect(rows, listState, fixedItems) {
        snapshotFlow { rows.loadState.refresh }.collectLatest { refresh ->
            if (refresh is LoadState.Loading) {
                anchor.pending = anchor.pending ?: anchor.last
                Log.d("PersonScrollRestorePolicyTester", "Refresh loading; pendingId=${anchor.pending?.contactId}, offset=${anchor.pending?.offset}, count=${rows.itemCount}")
            } else if (refresh is LoadState.NotLoading) {
                val bookmark = anchor.pending ?: return@collectLatest
                try {
                    val rank = resolve(bookmark)
                    val target = PersonScrollRestorePolicy.target(rank, rows.itemCount, fixedItems, bookmark.offset)
                    Log.d("PersonScrollRestorePolicyTester", "Resolved id=${bookmark.contactId}, rank=$rank, count=${rows.itemCount}, target=$target")
                    if (target != null) restoreLoadedBookmark(rows, listState, fixedItems, anchor, bookmark, target)
                    if (anchor.pending == bookmark) anchor.pending = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("PersonScrollRestorePolicyTester", "Bookmark resolution failed; preserve current position", error)
                    anchor.pending = null
                }
            }
        }
    }
}

private suspend fun restoreLoadedBookmark(
    rows: LazyPagingItems<PersonContactRow>, listState: LazyListState, fixedItems: Int,
    anchor: PersonScrollAnchor, bookmark: PersonContactAnchor, target: PersonScrollRestorePolicy.Target,
) {
    val rowIndex = target.index - fixedItems
    // The first access positions the viewport and fetches a dropped page or starts a jump.
    // Its placeholder is not a completed restore: key tracking can still move the viewport.
    rows[rowIndex]
    Log.d("PersonScrollRestorePolicyTester", "Position viewport index=${target.index}, offset=${target.offset}, loadedId=${rows.peek(rowIndex)?.contact?.id}")
    // Refresh can arrive during layout; defer the measure instead of forcing a nested measure.
    listState.requestScrollToItem(target.index, target.offset)
    val loaded = snapshotFlow {
        val cancelled = anchor.pending != bookmark
        val row = if (rowIndex in 0 until rows.itemCount) rows.peek(rowIndex) else null
        row to cancelled
    }.first { (row, cancelled) -> cancelled || row != null }
    if (loaded.second) {
        Log.d("PersonScrollRestorePolicyTester", "Bookmark superseded before target row loaded")
        return
    }
    val row = checkNotNull(loaded.first)
    Log.d("PersonScrollRestorePolicyTester", "Target row loaded id=${row.contact.id}, request next measure index=${target.index}")
    // Apply to the next measure with the real key, after the new paging window is present.
    listState.requestScrollToItem(target.index, target.offset)
    snapshotFlow {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target.index }
        val positioned = listState.firstVisibleItemIndex == target.index &&
            listState.firstVisibleItemScrollOffset == target.offset
        anchor.pending != bookmark || (item?.key == row.key && (positioned || !listState.canScrollForward))
    }.first { it }
    Log.d("PersonScrollRestorePolicyTester", "Bookmark measured at index=${listState.firstVisibleItemIndex}, offset=${listState.firstVisibleItemScrollOffset}, target=${target.index}")
}

internal fun PersonContactRow.bookmark(offset: Int) = PersonContactAnchor(
    contact.id, groupId, sectionOrder, sortLetter, contact.name, offset,
)
