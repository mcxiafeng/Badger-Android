package top.mcxiafeng.badger.pages.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import top.mcxiafeng.badger.utils.BadgerLog

@Composable
internal fun MyProfileHeader(
    profile: UserProfile?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = BadgerSpacing.xl)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MiuixTheme.colorScheme.surface,
                    shape = miuixShape(BadgerRadius.lg)
                )
                .clickable {
                    BadgerLog.d("PersonPage", "My Profile clicked!")
                    onClick()
                }
                .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ContactAvatar(name = profile?.name ?: "用户", avatarPath = profile?.avatarPath, size = 40)
                }
                Spacer(modifier = Modifier.width(BadgerSpacing.lg))
                Column {
                    Text(
                        text = "我的名片",
                        style = MiuixTheme.textStyles.body1
                    )
                    Spacer(modifier = Modifier.height(BadgerSpacing.xxs))
                    Text(
                        text = profile?.name?.let { "查看和编辑 $it 的信息" } ?: "查看和编辑个人信息",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
internal fun ContactItem(
    contact: Contact,
    showDots: List<TagCacheEntity>,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        BasicComponent(
            title = contact.name,
            summary = contact.bio?.takeIf { it.isNotBlank() },
            startAction = {
                ContactAvatar(
                    name = contact.name,
                    avatarUrl = contact.avatarUrl,
                    avatarPath = contact.avatarPath,
                    size = 44,
                )
            },
            onClick = null 
        )

        
        
        
        
        if (showDots.isNotEmpty() && !isSelectMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = BadgerSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                showDots.take(3).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(tag.color))
                    )
                }
                if (showDots.size > 3) {
                    Text(
                        text = "+${showDots.size - 3}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        
        if (isSelectMode) {
            Icon(
                imageVector = if (isSelected) Lucide.CircleCheck else Lucide.Circle,
                contentDescription = if (isSelected) "已选" else "未选",
                tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = BadgerSpacing.lg).size(24.dp)
            )
        }
    }
}

@Composable
internal fun ContactRow(
    contact: Contact,
    contactTags: Map<Long, List<TagCacheEntity>>,
    selectedIds: Set<Long>,
    isSelectMode: Boolean,
    onContactClick: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onEnterSelectMode: (Long) -> Unit,
) {
    val dots = contactTags[contact.id].orEmpty()
    val isSelected = contact.id in selectedIds
    ContactItem(
        contact = contact,
        showDots = dots,
        isSelectMode = isSelectMode,
        isSelected = isSelected,
        onClick = {
            if (isSelectMode) onToggleSelected(contact.id)
            else onContactClick(contact.id)
        },
        onLongClick = {
            if (!isSelectMode) onEnterSelectMode(contact.id)
        }
    )
}

@Composable
internal fun LetterIndexBar(
    letters: List<String>,
    onSelectLetter: (String) -> Unit,
    onDragStateChange: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(LetterIndexHitWidth)
                .padding(vertical = BadgerSpacing.sm)
                .padding(horizontal = BadgerSpacing.xs)
                .pointerInput(letters) {
                    
                    
                    
                    
                    
                    
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val index = (offset.y / (size.height / letters.size)).toInt().coerceIn(0, letters.size - 1)
                            val letter = letters[index]
                            onDragStateChange(true, letter)
                            onSelectLetter(letter)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume() 
                            val index = (change.position.y / (size.height / letters.size)).toInt().coerceIn(0, letters.size - 1)
                            val letter = letters[index]
                            onDragStateChange(true, letter)
                            onSelectLetter(letter)
                        },
                        onDragEnd = {
                            onDragStateChange(false, "")
                        },
                        onDragCancel = {
                            
                            onDragStateChange(false, "")
                        }
                    )
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable {
                            onDragStateChange(true, letter)
                            onSelectLetter(letter)
                            coroutineScope.launch {
                                delay(BadgerMotion.DURATION_BASE.toLong())
                                onDragStateChange(false, "")
                            }
                        }
                        .padding(horizontal = BadgerSpacing.xs)
                )
            }
        }
    }
}

@Composable
internal fun LetterTooltip(visible: Boolean, letter: String) {
    if (visible && letter.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {} 
                .zIndex(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.7f), miuixShape(BadgerRadius.md))
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(
                    text = letter,
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onBackground
                )
            }
        }
    }
}

internal class Ref<T>(var v: T)
