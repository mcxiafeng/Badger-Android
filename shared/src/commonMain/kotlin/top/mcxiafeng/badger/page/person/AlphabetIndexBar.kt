package top.mcxiafeng.badger.page.person

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AlphabetIndexBar(
    alphabet: List<Char>,
    modifier: Modifier = Modifier,
    selectedLetter: Char? = null,
    onLetterSelected: (Char) -> Unit,
    onDraggingChanged: ((Boolean) -> Unit)? = null,
) {
    var totalHeightPx by remember { mutableFloatStateOf(0f) }

    fun selectLetterAt(y: Float) {
        if (totalHeightPx > 0f && alphabet.isNotEmpty()) {
            val index = (y / totalHeightPx * alphabet.size).toInt().coerceIn(0, alphabet.lastIndex)
            onLetterSelected(alphabet[index])
        }
    }

    Column(
        modifier = modifier
            .background(
                color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                shape = CircleShape,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .onGloballyPositioned { coordinates ->
                totalHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(alphabet) {
                detectTapGestures(
                    onPress = { offset ->
                        onDraggingChanged?.invoke(true)
                        selectLetterAt(offset.y)
                        tryAwaitRelease()
                        onDraggingChanged?.invoke(false)
                    },
                )
            }
            .pointerInput(alphabet) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onDraggingChanged?.invoke(true)
                        selectLetterAt(offset.y)
                    },
                    onDragEnd = {
                        onDraggingChanged?.invoke(false)
                    },
                    onDragCancel = {
                        onDraggingChanged?.invoke(false)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        selectLetterAt(change.position.y)
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        alphabet.forEach { letter ->
            val isSelected = letter == selectedLetter
            Text(
                text = letter.toString(),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}
