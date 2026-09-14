package top.mcxiafeng.badger.pages.person.contact.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.BasicInfoCard
import top.mcxiafeng.badger.ui.components.SectionCard
import top.mcxiafeng.badger.ui.components.ThinDivider
import top.mcxiafeng.badger.ui.components.ToolbarAction
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider as Divider
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.platform.LocalLocale
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.User
import top.mcxiafeng.badger.utils.formatEpochDateTime

@Composable
internal fun ContactDetailPageContent(
    isLoading: Boolean,
    contact: Contact?,
    contentModifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    avatarImageBitmap: ImageBitmap?,
    systemFields: List<PersonFieldDisplay>,
    customFields: List<PersonFieldDisplay>,
    platformFields: List<Pair<String, PlatformEntry>>,
    bio: String?,
    tags: List<Tag>,
    onAvatarClick: () -> Unit,
    onEditNameClick: () -> Unit,
    onFieldClick: (PersonFieldDisplay) -> Unit,
    onFieldLongPress: (PersonFieldDisplay) -> Unit,
    onPlatformClick: (String, PlatformEntry) -> Unit,
    onPlatformLongPress: (String, PlatformEntry) -> Unit,
    onAddPlatformClick: () -> Unit,
    onBatchImportClick: () -> Unit = {},
    onBioClick: () -> Unit,
    onTagsClick: () -> Unit,
    onAiTagsClick: () -> Unit = {},
    onBasicInfoCellClick: (fieldKey: String, currentValue: String?) -> Unit = { _, _ -> },
) {
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (contact == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text("联系人不存在", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(contentModifier),
            contentPadding = PaddingValues(
                
                
                
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding().coerceAtLeast(32.dp)
            )
        ) {
            
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable { onAvatarClick() }
                    ) {
                        if (avatarImageBitmap != null) {
                            Image(
                                bitmap = avatarImageBitmap,
                                contentDescription = "头像",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.take(1),
                                    style = MiuixTheme.textStyles.title1,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Camera,
                                contentDescription = "更换头像",
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onEditNameClick() }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = contact.name,
                            style = MiuixTheme.textStyles.title1,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Lucide.Pencil,
                            contentDescription = "编辑姓名",
                            modifier = Modifier.size(16.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                    val note = contact.note
                    if (!note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = note,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    val dateStr = formatEpochDateTime(contact.createTime)
                    Text(
                        text = "创建于 $dateStr",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            
            item(key = "basic_info") {
                BasicInfoCard(
                    fields = systemFields,
                    onCellClick = onBasicInfoCellClick,
                )
            }

            
            
            
            if (systemFields.any { it.fieldKey != null && it.fieldKey !in BASIC_INFO_FIELD_KEYS && it.fieldKey !in PLATFORM_FIELD_KEYS }) {
                item(key = "system_section") {
                    ContactFieldSection(
                        title = "联系方式",
                        fields = systemFields.filter {
                            it.fieldKey != null && it.fieldKey !in BASIC_INFO_FIELD_KEYS && it.fieldKey !in PLATFORM_FIELD_KEYS
                        },
                        onClick = onFieldClick,
                        onLongPress = onFieldLongPress,
                    )
                }
            }

            
            
            if (platformFields.isNotEmpty()) {
                item(key = "platforms_section") {
                    SectionCard(title = "社交平台") {
                        
                        
                        
                        val totalRows = platformFields.size + 2 
                        platformFields.forEachIndexed { index, (fieldKey, entry) ->
                            val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                            val summary = buildString {
                                if (!entry.displayName.isNullOrBlank()) {
                                    append(entry.displayName)
                                    if (!entry.value.isNullOrBlank()) append("（${entry.value}）")
                                } else if (!entry.value.isNullOrBlank()) {
                                    append(entry.value)
                                } else {
                                    append(entry.jumpLink)
                                }
                            }
                            LongPressArrowPreference(
                                title = displayName,
                                summary = summary,
                                onClick = { onPlatformClick(fieldKey, entry) },
                                onLongClick = { onPlatformLongPress(fieldKey, entry) }
                            )
                            if (index < totalRows - 1) {
                                ThinDivider()
                            }
                        }
                        LongPressArrowPreference(
                            title = "添加社交平台",
                            summary = "添加对方的社交账号",
                            onClick = onAddPlatformClick,
                            onLongClick = {  },
                        )
                        ThinDivider()
                        LongPressArrowPreference(
                            title = "批量导入",
                            summary = "粘贴多个链接一次性导入",
                            onClick = onBatchImportClick,
                            onLongClick = { },
                        )
                    }
                }
            } else {
                item(key = "platforms_add") {
                    SectionCard(title = "社交平台") {
                        LongPressArrowPreference(
                            title = "添加社交平台",
                            summary = "添加对方的社交账号",
                            onClick = onAddPlatformClick,
                            onLongClick = { },
                        )
                        ThinDivider()
                        LongPressArrowPreference(
                            title = "批量导入",
                            summary = "粘贴多个链接一次性导入",
                            onClick = onBatchImportClick,
                            onLongClick = { },
                        )
                    }
                }
            }

            if (customFields.isNotEmpty()) {
                item(key = "custom_section") {
                    ContactFieldSection(
                        title = "自定义信息",
                        fields = customFields,
                        onClick = onFieldClick,
                        onLongPress = onFieldLongPress,
                    )
                }
            }

            
            
            
            item(key = "bio") {
                SectionCard(title = "个人介绍") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bio.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "该人暂无介绍，",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    text = "点击添加",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onBioClick() },
                                )
                            }
                        } else {
                            Text(
                                text = bio,
                                style = MiuixTheme.textStyles.body1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onBioClick() },
                            )
                        }
                    }
                }
            }

            
            item(key = "tags") {
                ContactTagsCard(
                    tags = tags,
                    onTagsClick = onTagsClick,
                    onAiTagsClick = onAiTagsClick,
                )
            }
        }
    }
}

private val BASIC_INFO_FIELD_KEYS = setOf("gender", "birthday", "country", "region")
private val PLATFORM_FIELD_KEYS = top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS

@Composable
internal fun ContactDetailFloatingToolbars(
    showFieldToolbar: Boolean,
    selectedField: PersonFieldDisplay?,
    onFieldCopy: () -> Unit,
    onFieldEdit: () -> Unit,
    onFieldSync: () -> Unit,
    onFieldDelete: () -> Unit,
    showPlatformToolbar: Boolean,
    selectedPlatform: Pair<String, PlatformEntry>?,
    onPlatformCopy: () -> Unit,
    onPlatformEdit: () -> Unit,
    onPlatformSync: () -> Unit,
    onPlatformDelete: () -> Unit,
) {
    
    if (showFieldToolbar && selectedField != null) {
        FloatingToolbar(cornerRadius = BadgerRadius.card) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ToolbarAction(
                    icon = Lucide.Copy,
                    label = "复制",
                    onClick = onFieldCopy
                )
                ToolbarAction(
                    icon = Lucide.Pencil,
                    label = "编辑",
                    onClick = onFieldEdit
                )
                
                val selectedFieldKey = selectedField.fieldKey
                if (selectedFieldKey != null) {
                    val platformKey = selectedFieldKey
                    
                    if (platformKey.kindCanSync) {
                        ToolbarAction(
                            icon = Lucide.User,
                            label = "同步信息",
                            onClick = onFieldSync
                        )
                    }
                }
                ToolbarAction(
                    icon = Lucide.Trash2,
                    label = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    onClick = onFieldDelete
                )
            }
        }
    }
    
    if (showPlatformToolbar && selectedPlatform != null) {
        val (fieldKey, pEntry) = selectedPlatform
        val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
        FloatingToolbar(cornerRadius = BadgerRadius.card) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ToolbarAction(
                    icon = Lucide.Copy,
                    label = "复制",
                    onClick = onPlatformCopy
                )
                ToolbarAction(
                    icon = Lucide.Pencil,
                    label = "编辑",
                    onClick = onPlatformEdit
                )
                
                if (pEntry.jumpLink.isNotBlank() && fieldKey.kindCanSync) {
                    ToolbarAction(
                        icon = Lucide.User,
                        label = "同步信息",
                        onClick = onPlatformSync
                    )
                }
                ToolbarAction(
                    icon = Lucide.Trash2,
                    label = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    onClick = onPlatformDelete
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContactTagsCard(
    tags: List<Tag>,
    onTagsClick: () -> Unit,
    onAiTagsClick: () -> Unit,
) {
    
    
    SectionCard(title = "") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "标签",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onAiTagsClick,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Lucide.Sparkles,
                    contentDescription = "AI 推荐标签",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (tags.isEmpty()) {
            ArrowPreference(
                title = "添加标签",
                summary = "暂未添加",
                onClick = onTagsClick,
            )
        } else {
            
            
            
            
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTagsClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = tag.name,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(tag.color).copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
