package top.mcxiafeng.badger.pages.person

import android.util.Log
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.ui.components.AlphabetIndexBar

@Composable
internal fun ContactLetterIndex(
    letterCounts: List<LetterCount>, listState: LazyListState, fixedItemCount: Int,
    profileIndex: Int, modifier: Modifier = Modifier, showProfile: Boolean = true, onJump: () -> Unit = {}
) {
    val targets = remember(letterCounts, fixedItemCount, profileIndex, showProfile) {
        buildMap {
            if (showProfile) put(MY_PROFILE_LETTER, profileIndex)
            var offset = fixedItemCount
            letterCounts.forEach { group ->
                put(group.letter, offset)
                offset += group.count
            }
        }
    }
    // A picker-style list without the profile header must not offer the star jump at all.
    val letters = remember(showProfile) { if (showProfile) CONTACT_LETTERS else CONTACT_LETTERS.drop(1) }
    val fallbackLetter = if (showProfile) MY_PROFILE_LETTER else null
    var jumpLetter by rememberSaveable { mutableStateOf<String?>(null) }
    val draggingList by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(draggingList) {
        if (draggingList) {
            jumpLetter = null
            Log.d("ContactLetterIndexTester", "联系人列表手动滚动，字母高亮跟随列表")
        }
    }
    val scrolledLetter by remember(targets, listState, fallbackLetter) {
        derivedStateOf {
            targets.entries.lastOrNull { it.value <= listState.firstVisibleItemIndex }?.key ?: fallbackLetter
        }
    }
    val scope = rememberCoroutineScope()
    AlphabetIndexBar(letters, jumpLetter?.takeIf { it in targets } ?: scrolledLetter,
        modifier = modifier, enabledLetters = targets.keys, description = "联系人字母索引",
        letterDescription = { if (it == MY_PROFILE_LETTER) "定位到我的名片" else "定位到 $it 开头的联系人" },
        unavailableDescription = "暂无联系人", onSelectLetter = { letter, interaction ->
            jumpLetter = letter
            Log.d("ContactLetterIndexTester", "联系人索引定位: letter=$letter, interaction=$interaction")
            onJump()
            scope.launch { listState.scrollToItem(targets.getValue(letter)) }
        })
}

private const val MY_PROFILE_LETTER = "⭐"
private val CONTACT_LETTERS = listOf(MY_PROFILE_LETTER, "#") + ('A'..'Z').map(Char::toString)
