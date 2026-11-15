package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 点击可以平滑滚动，连续拖动则应立即定位。 */
enum class LetterIndexInteraction { Tap, Drag }

/** 可见栏使用独立窄尺寸；触控容错交由 Compose，读屏按可用字母逐项调节。 */
@Composable
fun AlphabetIndexBar(
    letters: List<String>,
    selectedLetter: String?,
    onSelectLetter: (String, LetterIndexInteraction) -> Unit,
    modifier: Modifier = Modifier,
    enabledLetters: Set<String> = letters.toSet(),
    description: String = "字母索引",
    letterDescription: (String) -> String = { "定位到 $it" },
    unavailableDescription: String = "暂无条目"
) {
    if (letters.isEmpty()) return
    val touch = remember { IndexTouchState() }
    LaunchedEffect(touch.previewLetter, touch.dragging, touch.tapSequence) {
        if (!touch.dragging && touch.previewLetter != null) {
            delay(BadgerMotion.DURATION_BASE.toLong())
            touch.previewLetter = null
        }
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.width(BadgerSize.indexRailWidth)) {
        if (maxHeight <= BadgerSpacing.sm * 2) return@BoxWithConstraints
        val availablePx = with(density) { (maxHeight - BadgerSpacing.sm * 2).roundToPx() }
        if (availablePx < letters.size) return@BoxWithConstraints
        val preferredPx = with(density) { (BadgerSize.indexRowHeight * density.fontScale.coerceAtLeast(1f)).roundToPx() }
        // 先确定整数像素行高，避免逐项取整后最后几个字母被挤压。
        val rowHeight = with(density) { minOf(preferredPx, availablePx / letters.size).toDp() }
        val bandHeight = rowHeight * letters.size
        val controlHeight = bandHeight.coerceAtLeast(BadgerSize.minimumTouchTarget).coerceAtMost(maxHeight)
        val style = fittedIndexStyle(letters, rowHeight)
        LaunchedEffect(letters.size, rowHeight, style.fontSize) {
            Log.d(INDEX_TAG, "索引比例: letters=${letters.size}, width=${BadgerSize.indexRailWidth}, row=$rowHeight, font=${style.fontSize}")
        }
        Box(Modifier.align(Alignment.Center).height(controlHeight).fillMaxWidth()
            .indexGestures(letters, enabledLetters, rowHeight, controlHeight, touch, onSelectLetter)
            .semantics(mergeDescendants = true) {
                indexSemantics(letters, enabledLetters, selectedLetter, description, letterDescription, onSelectLetter)
            }) {
            IndexLetters(letters, enabledLetters, selectedLetter, rowHeight, style, Modifier.align(Alignment.Center))
        }
        touch.previewLetter?.let { letter ->
            IndexPreview(letter, letter in enabledLetters, unavailableDescription,
                Modifier.align(Alignment.CenterEnd).wrapContentSize(Alignment.CenterEnd, unbounded = true)
                    .offset(x = -(BadgerSize.indexRailWidth + BadgerSpacing.md)))
        }
    }
}

@Stable
private class IndexTouchState {
    var previewLetter by mutableStateOf<String?>(null)
    var dragging by mutableStateOf(false)
    var tapSequence by mutableIntStateOf(0)

    fun tap(letter: String) {
        dragging = false
        previewLetter = letter
        tapSequence++
        Log.d(INDEX_TAG, "点击字母: letter=$letter")
    }

    fun drag(letter: String) {
        dragging = true
        previewLetter = letter
        Log.d(INDEX_TAG, "拖动定位: letter=$letter")
    }

    fun finish(cancelled: Boolean) {
        dragging = false
        if (cancelled) previewLetter = null
        Log.d(INDEX_TAG, "索引拖动结束: cancelled=$cancelled")
    }
}

@Composable
private fun Modifier.indexGestures(
    letters: List<String>, enabledLetters: Set<String>, rowHeight: Dp, controlHeight: Dp,
    touch: IndexTouchState, onSelect: (String, LetterIndexInteraction) -> Unit
): Modifier {
    val density = LocalDensity.current
    val rowPx = with(density) { rowHeight.roundToPx().toFloat() }
    val controlPx = with(density) { controlHeight.roundToPx() }
    val topPx = (controlPx - rowPx * letters.size) / 2
    val dispatch by rememberUpdatedState { letter: String, interaction: LetterIndexInteraction ->
        if (letter in enabledLetters) onSelect(letter, interaction)
    }
    fun letterAt(y: Float) = letters[((y - topPx) / rowPx).toInt().coerceIn(letters.indices)]
    return pointerInput(letters, rowPx, topPx) {
        detectTapGestures(onTap = { position ->
            val letter = letterAt(position.y)
            touch.tap(letter)
            dispatch(letter, LetterIndexInteraction.Tap)
        })
    }.pointerInput(letters, rowPx, topPx) {
        detectVerticalDragGestures(
            onDragStart = { position ->
                val letter = letterAt(position.y)
                touch.drag(letter)
                dispatch(letter, LetterIndexInteraction.Drag)
            },
            onVerticalDrag = { change, _ ->
                change.consume()
                val letter = letterAt(change.position.y)
                if (letter != touch.previewLetter) {
                    touch.drag(letter)
                    dispatch(letter, LetterIndexInteraction.Drag)
                }
            },
            onDragEnd = { touch.finish(cancelled = false) },
            onDragCancel = { touch.finish(cancelled = true) }
        )
    }
}

private fun SemanticsPropertyReceiver.indexSemantics(
    letters: List<String>, enabledLetters: Set<String>, selectedLetter: String?, description: String,
    letterDescription: (String) -> String, onSelect: (String, LetterIndexInteraction) -> Unit
) {
    val available = letters.filter { it in enabledLetters }
    contentDescription = description
    stateDescription = selectedLetter?.let { "当前字母 $it" }.orEmpty()
    if (available.isEmpty()) {
        disabled()
        return
    }
    progressBarRangeInfo = ProgressBarRangeInfo(
        available.indexOf(selectedLetter).coerceAtLeast(0).toFloat(),
        0f..available.lastIndex.toFloat(), (available.size - 2).coerceAtLeast(0))
    setProgress { value ->
        if (!value.isFinite()) return@setProgress false
        val letter = available[value.roundToInt().coerceIn(available.indices)]
        onSelect(letter, LetterIndexInteraction.Tap)
        true
    }
    customActions = available.map { letter ->
        CustomAccessibilityAction(letterDescription(letter)) {
            onSelect(letter, LetterIndexInteraction.Tap)
            true
        }
    }
}

@Composable
private fun fittedIndexStyle(letters: List<String>, rowHeight: Dp): TextStyle {
    val base = MiuixTheme.textStyles.footnote2
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    return remember(letters, rowHeight, base, density, measurer) {
        // 星标使用定尺寸矢量图，避免 emoji 的字体回退把整栏字母一起缩小。
        val labels = letters.filter { it != "⭐" }.ifEmpty { listOf("A") }
        val desiredHeight = labels.maxOf { measurer.measure(it, base).size.height }.coerceAtLeast(1)
        val scale = (with(density) { rowHeight.toPx() } / desiredHeight).coerceAtMost(1f)
        base.copy(fontSize = base.fontSize * scale,
            lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * scale else base.lineHeight)
    }
}

@Composable
private fun IndexLetters(
    letters: List<String>, enabledLetters: Set<String>, selectedLetter: String?,
    rowHeight: Dp, style: TextStyle, modifier: Modifier
) {
    val symbolSize = minOf(rowHeight, BadgerSize.indexSymbolSize * LocalDensity.current.fontScale)
    Column(modifier.clearAndSetSemantics {}) {
        letters.forEach { letter ->
            val selected = letter == selectedLetter && letter in enabledLetters
            Box(Modifier.fillMaxWidth().height(rowHeight).clipToBounds(), contentAlignment = Alignment.Center) {
                if (selected) {
                    Box(Modifier.size(rowHeight).background(MiuixTheme.colorScheme.primary, CircleShape))
                }
                val color = when {
                    selected -> MiuixTheme.colorScheme.onPrimary
                    letter !in enabledLetters -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                }
                if (letter == "⭐") {
                    Icon(Icons.Outlined.Star, contentDescription = null, tint = color, modifier = Modifier.size(symbolSize))
                } else {
                    Text(letter, style = style, maxLines = 1, color = color)
                }
            }
        }
    }
}

@Composable
private fun IndexPreview(letter: String, available: Boolean, unavailableDescription: String, modifier: Modifier) {
    val previewSize = BadgerSize.indexPreviewMinSize * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
        Box(Modifier.size(previewSize)
            .clearAndSetSemantics { testTag = "alphabet_index_preview" }
            .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(BadgerRadius.inner)),
            contentAlignment = Alignment.Center) {
            Text(letter, style = MiuixTheme.textStyles.title2,
                color = if (available) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (!available) Text(unavailableDescription, style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.clearAndSetSemantics {})
    }
}

private const val INDEX_TAG = "AlphabetIndexBarTester"
