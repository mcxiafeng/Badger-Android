package top.mcxiafeng.badger.pages.person.contact

import android.graphics.Bitmap
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLocale
import top.mcxiafeng.badger.data.PersonFieldDisplay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference as PreferenceArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date

/**
 * ContactDetail page content and field-oriented sections.
 *
 * The page coordinator owns state and side effects; this file owns the field/list
 * presentation and keeps the section hierarchy independent from action handling.
 */
@Composable
internal fun ContactDetailPageContent(
    isLoading: Boolean,
    contact: Contact?,
    contentModifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    avatarBitmap: Bitmap?,
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
    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        contact == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "联系人不存在",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        else -> ContactDetailList(
            contact = contact,
            contentModifier = contentModifier,
            paddingValues = paddingValues,
            avatarBitmap = avatarBitmap,
            systemFields = systemFields,
            customFields = customFields,
            platformFields = platformFields,
            bio = bio,
            tags = tags,
            onAvatarClick = onAvatarClick,
            onEditNameClick = onEditNameClick,
            onFieldClick = onFieldClick,
            onFieldLongPress = onFieldLongPress,
            onPlatformClick = onPlatformClick,
            onPlatformLongPress = onPlatformLongPress,
            onAddPlatformClick = onAddPlatformClick,
            onBatchImportClick = onBatchImportClick,
            onBioClick = onBioClick,
            onTagsClick = onTagsClick,
            onAiTagsClick = onAiTagsClick,
            onBasicInfoCellClick = onBasicInfoCellClick,
        )
    }
}

@Composable
private fun ContactDetailList(
    contact: Contact,
    contentModifier: Modifier,
    paddingValues: PaddingValues,
    avatarBitmap: Bitmap?,
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
    onBatchImportClick: () -> Unit,
    onBioClick: () -> Unit,
    onTagsClick: () -> Unit,
    onAiTagsClick: () -> Unit,
    onBasicInfoCellClick: (String, String?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(contentModifier),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            bottom = paddingValues.calculateBottomPadding().coerceAtLeast(BadgerSpacing.xxl),
        ),
    ) {
        item(key = "header") {
            ContactDetailHeader(
                contact = contact,
                avatarBitmap = avatarBitmap,
                onAvatarClick = onAvatarClick,
                onEditNameClick = onEditNameClick,
            )
        }

        item(key = "basic_info") {
            BasicInfoCard(
                fields = systemFields,
                onCellClick = onBasicInfoCellClick,
            )
        }

        val additionalSystemFields = systemFields.filter {
            it.fieldKey != null &&
                it.fieldKey !in BASIC_INFO_FIELD_KEYS &&
                it.fieldKey !in PLATFORM_FIELD_KEYS
        }
        if (additionalSystemFields.isNotEmpty()) {
            item(key = "system_section") {
                ContactFieldSection(
                    title = "联系方式",
                    fields = additionalSystemFields,
                    onClick = onFieldClick,
                    onLongPress = onFieldLongPress,
                )
            }
        }

        item(key = "platforms_section") {
            ContactDetailPlatformsSection(
                platformFields = platformFields,
                onPlatformClick = onPlatformClick,
                onPlatformLongPress = onPlatformLongPress,
                onAddPlatformClick = onAddPlatformClick,
                onBatchImportClick = onBatchImportClick,
            )
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
            ContactDetailBioSection(
                bio = bio,
                onBioClick = onBioClick,
            )
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

@Composable
private fun ContactDetailHeader(
    contact: Contact,
    avatarBitmap: Bitmap?,
    onAvatarClick: () -> Unit,
    onEditNameClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BadgerSpacing.xl, vertical = BadgerSpacing.lgx),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clickable(onClick = onAvatarClick),
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = "头像",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = contact.name.take(1),
                        style = MiuixTheme.textStyles.title1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = "更换头像",
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(BadgerSpacing.md))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onEditNameClick)
                .padding(horizontal = BadgerSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = contact.name,
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(BadgerSpacing.xs))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "编辑姓名",
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        if (!contact.note.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            Text(
                text = contact.note,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }

        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        val dateStr = SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            LocalLocale.current.platformLocale,
        ).format(Date(contact.createTime))
        Text(
            text = "创建于 $dateStr",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ContactDetailPlatformsSection(
    platformFields: List<Pair<String, PlatformEntry>>,
    onPlatformClick: (String, PlatformEntry) -> Unit,
    onPlatformLongPress: (String, PlatformEntry) -> Unit,
    onAddPlatformClick: () -> Unit,
    onBatchImportClick: () -> Unit,
) {
    SectionCard(title = "社交平台") {
        platformFields.forEachIndexed { index, (fieldKey, entry) ->
            val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
            val summary = buildString {
                when {
                    !entry.displayName.isNullOrBlank() -> {
                        append(entry.displayName)
                        if (!entry.value.isNullOrBlank()) append("（${entry.value}）")
                    }
                    !entry.value.isNullOrBlank() -> append(entry.value)
                    else -> append(entry.jumpLink)
                }
            }
            LongPressArrowPreference(
                title = displayName,
                summary = summary,
                onClick = { onPlatformClick(fieldKey, entry) },
                onLongClick = { onPlatformLongPress(fieldKey, entry) },
            )
            if (index < platformFields.lastIndex) ThinDivider()
        }

        if (platformFields.isNotEmpty()) ThinDivider()
        LongPressArrowPreference(
            title = "添加社交平台",
            summary = "添加对方的社交账号",
            onClick = onAddPlatformClick,
            onLongClick = {},
        )
        ThinDivider()
        LongPressArrowPreference(
            title = "批量导入",
            summary = "粘贴多个链接一次性导入",
            onClick = onBatchImportClick,
            onLongClick = {},
        )
    }
}

@Composable
private fun ContactDetailBioSection(
    bio: String?,
    onBioClick: () -> Unit,
) {
    SectionCard(title = "个人介绍") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .padding(BadgerSpacing.lg),
            contentAlignment = Alignment.Center,
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
                        modifier = Modifier.clickable(onClick = onBioClick),
                    )
                }
            } else {
                Text(
                    text = bio,
                    style = MiuixTheme.textStyles.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onBioClick),
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
                .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
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
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI 推荐标签",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (tags.isEmpty()) {
            PreferenceArrowPreference(
                title = "添加标签",
                summary = "暂未添加",
                onClick = onTagsClick,
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTagsClick)
                    .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = tag.name,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BadgerSpacing.sm))
                            .background(Color(tag.color).copy(alpha = 0.25f))
                            .padding(horizontal = BadgerSpacing.md, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private val BASIC_INFO_FIELD_KEYS = setOf("gender", "birthday", "country", "region")
