package top.mcxiafeng.badger.pages.person.contact


import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.components.AlphabetIndexBar
import top.mcxiafeng.badger.ui.components.LetterIndexInteraction
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ManualPlatformList(
    state: AddPlatformUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(top = BadgerSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        TextField(state.query, onQueryChange, label = "搜索平台或首字母", singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                { IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除搜索")
                } }
            } else null,
            modifier = Modifier.fillMaxWidth())
        Card { ArrowPreference(title = "自定义平台", onClick = { onSelect("") }) }
        if (state.platforms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有找到匹配的平台", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            IndexedPlatformList(state, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun IndexedPlatformList(state: AddPlatformUiState, onSelect: (String) -> Unit, modifier: Modifier) {
    val groups = remember(state.platforms) { state.platforms.groupBy { it.initial } }
    val positions = remember(groups) {
        var position = 0
        groups.mapValues { (_, items) -> position.also { position += items.size + 1 } }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumpLetter by rememberSaveable(state.query) { mutableStateOf<String?>(null) }
    val isDragging by listState.interactionSource.collectIsDraggedAsState()
    val scrolledLetter by remember(positions, listState) {
        derivedStateOf { positions.entries.lastOrNull { it.value <= listState.firstVisibleItemIndex }?.key }
    }
    LaunchedEffect(isDragging) {
        if (isDragging) {
            jumpLetter = null
            Log.d("ManualPlatformListTester", "手动滚动平台列表，字母指示跟随列表")
        } else Log.d("ManualPlatformListTester", "Spring viewport clipped below fixed search and custom action")
    }
    val activeLetter = jumpLetter?.takeIf { it in positions } ?: scrolledLetter
    Row(modifier) {
        // The stationary parent owns clipping; only its scrolling child is translated.
        Box(Modifier.weight(1f).clipToBounds().testTag("manual_platform_viewport")) {
            IndexedPlatformRows(state, groups, listState, onSelect)
        }
        AlphabetIndexBar(positions.keys.toList(), activeLetter,
            modifier = Modifier.fillMaxHeight(), description = "平台字母索引",
            letterDescription = { "定位到 $it 开头的平台" },
            onSelectLetter = { letter, interaction ->
                jumpLetter = letter
                Log.d("ManualPlatformListTester", "定位平台首字母: letter=$letter, interaction=$interaction")
                scope.launch {
                    val position = positions.getValue(letter)
                    if (interaction == LetterIndexInteraction.Drag) listState.scrollToItem(position)
                    else listState.animateScrollToItem(position)
                }
            })
    }
}

@Composable
private fun IndexedPlatformRows(
    state: AddPlatformUiState, groups: Map<String, List<PlatformListItem>>, listState: LazyListState,
    onSelect: (String) -> Unit,
) {
    val pendingKeys = state.additions.keys
    LazyColumn(Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(top = BadgerSpacing.scrollContentTopGap, bottom = BadgerSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        groups.forEach { (initial, platforms) ->
            item(key = "heading_$initial", contentType = "heading") {
                PlatformSectionTitle(initial, platformItemMotion().padding(top = BadgerSpacing.md))
            }
            items(platforms, key = { it.definition.fieldKey }, contentType = { "platform" }) { item ->
                ManualPlatformRow(item, item.definition.fieldKey in pendingKeys, onSelect, platformItemMotion())
            }
        }
    }
}

@Composable
private fun ManualPlatformRow(
    item: PlatformListItem, pending: Boolean, onSelect: (String) -> Unit, modifier: Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = BadgerSize.coverIconSize)
            .clickable(enabled = !item.isAdded, role = Role.Button) { onSelect(item.definition.fieldKey) }
            .padding(vertical = BadgerSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)
    ) {
        PlatformListAvatar(item.definition.fieldKey)
        Text(item.definition.displayName, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
        if (item.isAdded) {
            Text(if (pending) "待添加" else "已添加", style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
