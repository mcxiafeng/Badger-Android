package top.mcxiafeng.badger.pages.person.contact

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.ScanResult
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider as Divider
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

/**
 * 自绘水平分割线(0.5dp,使用主题 dividerLine 颜色)。
 *
 * 用自绘不用 Miuix HorizontalDivider 是因为后者在 Card 内经常渲染不出来。
 */
@Composable
internal fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine)
    )
}

/**
 * 联系人详情页内容组件。提取自 ContactDetailPage 以减少单文件体积。
 */
@Composable
internal fun ContactDetailPageContent(
    isLoading: Boolean,
    contact: Contact?,
    contentModifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    avatarBitmap: Bitmap?,
    systemFields: List<ContactFieldDisplay>,
    customFields: List<ContactFieldDisplay>,
    platformFields: List<Pair<String, PlatformEntry>>,
    scanResults: List<ScanResult>,
    collectionNameMap: Map<Long, String>,
    contactCollectionIds: Set<Long>,
    onAvatarClick: () -> Unit,
    onEditNameClick: () -> Unit,
    onFieldClick: (ContactFieldDisplay) -> Unit,
    onFieldLongPress: (ContactFieldDisplay) -> Unit,
    onPlatformClick: (String, PlatformEntry) -> Unit,
    onPlatformLongPress: (String, PlatformEntry) -> Unit,
    onScanResultClick: (ScanResult) -> Unit,
    onScanResultLongClick: (ScanResult) -> Unit,
    onAddPlatformClick: () -> Unit,
    onAddToCollectionClick: () -> Unit,
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
                // [修复防御]: 删除头图后,需要让 LazyColumn 顶部避开 Scaffold 注入的
                // TopAppBar 区域(top 内含 status bar + TopAppBar 高度)。否则
                // "基础信息" 标题会被 TopAppBar 覆盖,只能看到从"国家"行附近开始。
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding().coerceAtLeast(32.dp)
            )
        ) {
            // 上方：头像 + 姓名区域(从 d83b7f6 还原的 header 块)
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 头像(含相机图标提示)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable { onAvatarClick() }
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap.asImageBitmap(),
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
                        // 相机图标覆盖层
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
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
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑姓名",
                            modifier = Modifier.size(16.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                    if (!contact.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = contact.note,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    val dateStr = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        LocalLocale.current.platformLocale
                    ).format(Date(contact.createTime))
                    Text(
                        text = "创建于 $dateStr",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            // ========== 基础信息 2x2 网格(PR2) ==========
            item(key = "basic_info") {
                BasicInfoCard(
                    fields = systemFields,
                    onCellClick = onBasicInfoCellClick,
                )
            }

            // 下方：联系方式分组（支持长按）
            // [修复防御]: 删掉"长按联系方式可复制/编辑"灰色 FirstTimeHint 提示语,
            // 整页只保留简洁卡片;卡片之间靠 SectionCard 自带 12dp bottom padding + Card 边框分隔。
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

            // 社交平台(老 UI 风格:同一 Card 放平台 + 添加,无外层 Box 叠加,
            // 行间用 ThinDivider() 视觉分隔,与扫描记录一致)
            if (platformFields.isNotEmpty()) {
                item(key = "platforms_section") {
                    SectionCard(title = "社交平台") {
                        // [修复防御]:每两个 LongPressArrowPreference 之间显式插入
                        // ThinDivider(),修复 SectionCard 拆 Box padding 后 border 消失、
                        // 多行视觉重叠的问题。
                        val totalRows = platformFields.size + 1 // 平台项 + "添加社交平台"
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
                            onLongClick = { /* 无长按动作 */ },
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

            // 扫描记录
            if (scanResults.isNotEmpty()) {
                item(key = "styles") {
                    SectionCard(title = "扫描记录") {
                        scanResults.forEachIndexed { index, scanResult ->
                            val dateLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", LocalLocale.current.platformLocale)
                                .format(Date(scanResult.scannedTime))
                            LongPressArrowPreference(
                                title = "记录${index + 1}",
                                summary = dateLabel,
                                showArrow = false,
                                onClick = { onScanResultClick(scanResult) },
                                onLongClick = { onScanResultLongClick(scanResult) }
                            )
                            if (index < scanResults.lastIndex) {
                                ThinDivider()
                            }
                        }
                    }
                }
            }

            // 添加至名片夹
            item(key = "add_to_collection") {
                SectionCard(title = "名片夹") {
                    ArrowPreference(
                        title = "添加到名片夹",
                        summary = if (contactCollectionIds.isEmpty()) "未添加" else "已添加 ${contactCollectionIds.size} 个名片夹",
                        onClick = onAddToCollectionClick
                    )
                }
            }
        }
    }
}

/** 基础信息 4 个 fieldKey(与 SYSTEM_FIELDS 顺序对应) */
private val BASIC_INFO_FIELD_KEYS = setOf("gender", "birthday", "country", "region")
private val PLATFORM_FIELD_KEYS = top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS

// ========== 浮动工具栏(长按上下文菜单) ==========

/**
 * 联系人详情页底部浮动工具栏。包含三种模式:
 * 1. 长按联系方式:复制/编辑/同步/删除
 * 2. 长按扫描记录:删除
 * 3. 长按社交平台:复制/编辑/同步/删除
 */
@Composable
internal fun ContactDetailFloatingToolbars(
    showFieldToolbar: Boolean,
    selectedField: ContactFieldDisplay?,
    onFieldCopy: () -> Unit,
    onFieldEdit: () -> Unit,
    onFieldSync: () -> Unit,
    onFieldDelete: () -> Unit,
    showStyleToolbar: Boolean,
    onStyleDelete: () -> Unit,
    showPlatformToolbar: Boolean,
    selectedPlatform: Pair<String, PlatformEntry>?,
    onPlatformCopy: () -> Unit,
    onPlatformEdit: () -> Unit,
    onPlatformSync: () -> Unit,
    onPlatformDelete: () -> Unit,
) {
    // 1. 长按联系方式
    if (showFieldToolbar && selectedField != null) {
        FloatingToolbar(cornerRadius = 16.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ToolbarAction(
                    icon = Icons.Default.ContentCopy,
                    label = "复制",
                    onClick = onFieldCopy
                )
                ToolbarAction(
                    icon = Icons.Default.Edit,
                    label = "编辑",
                    onClick = onFieldEdit
                )
                // 「同步信息」按钮:对有 adapter 的平台字段显示
                if (selectedField.fieldKey != null) {
                    val platformKey = selectedField.fieldKey
                    val contactType = FIELD_DEF_MAP[platformKey]?.contactType
                    val adapter = contactType?.let { PlatformAdapterRegistry.getAdapter(it) }
                    if (adapter != null && adapter.canSync) {
                        ToolbarAction(
                            icon = Icons.Default.Person,
                            label = "同步信息",
                            onClick = onFieldSync
                        )
                    }
                }
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    label = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    onClick = onFieldDelete
                )
            }
        }
    }
    // 2. 长按扫描记录
    if (showStyleToolbar) {
        FloatingToolbar(cornerRadius = 16.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    label = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    onClick = onStyleDelete
                )
            }
        }
    }
    // 3. 长按社交平台
    if (showPlatformToolbar && selectedPlatform != null) {
        val (fieldKey, pEntry) = selectedPlatform
        val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
        FloatingToolbar(cornerRadius = 16.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ToolbarAction(
                    icon = Icons.Default.ContentCopy,
                    label = "复制",
                    onClick = onPlatformCopy
                )
                ToolbarAction(
                    icon = Icons.Default.Edit,
                    label = "编辑",
                    onClick = onPlatformEdit
                )
                // 同步信息按钮:仅对支持同步的平台显示
                val syncContactType = FIELD_DEF_MAP[fieldKey]?.contactType
                val syncAdapter = syncContactType?.let { PlatformAdapterRegistry.getAdapter(it) }
                if (pEntry.jumpLink.isNotBlank() && syncAdapter?.canSync == true) {
                    ToolbarAction(
                        icon = Icons.Default.Person,
                        label = "同步信息",
                        onClick = onPlatformSync
                    )
                }
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    label = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    onClick = onPlatformDelete
                )
            }
        }
    }
}
