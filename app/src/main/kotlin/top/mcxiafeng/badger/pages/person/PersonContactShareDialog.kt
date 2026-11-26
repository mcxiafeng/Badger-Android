package top.mcxiafeng.badger.pages.person

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.PlatformIconPager
import top.mcxiafeng.badger.ui.designsystem.BadgerDesignColors
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "PersonContactShareDialogTester"
private const val INVERTED_ROTATION = 180f
private const val SHARE_AVATAR_SIZE = 48
private const val CONTACT_NAME_MAX_LINES = 2

@Composable
internal fun PersonContactShareDialog(contact: Contact, viewModel: PersonViewModel, onDismiss: () -> Unit) {
    var inverted by remember(contact.id) { mutableStateOf(false) }
    val platformsResult by produceState<Result<List<ContactSharePlatform>>?>(null, contact.id, viewModel) {
        value = viewModel.loadContactSharePlatforms(contact.id)
    }
    // Read the theme and image account from the screen that owns this window.
    val accountContext = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val dark = colors.background.luminance() < colors.onBackground.luminance()
    val qrBackground = if (dark) BadgerDesignColors.contactShareQrDarkBackground else Color.White
    WindowDialog(
        show = true,
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = BadgerSpacing.xs)
            .wrapContentHeight(if (inverted) Alignment.Top else Alignment.Bottom)
            .graphicsLayer { rotationZ = if (inverted) INVERTED_ROTATION else 0f },
        outsideMargin = DpSize(BadgerSpacing.md, BadgerSpacing.xs),
        insideMargin = DpSize(BadgerSpacing.lg, BadgerSpacing.md),
        onDismissRequest = onDismiss,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().semantics { paneTitle = "分享联系人" },
        ) {
            val qrSize = minOf(maxWidth, maxHeight * BadgerSize.contactShareQrHeightFraction, BadgerSize.contactShareQrMaxSize)
            CompositionLocalProvider(LocalContext provides accountContext) {
                ContactShareContent(contact, platformsResult, viewModel, qrSize, qrBackground, onRotate = {
                    inverted = !inverted
                    Log.d(TAG, "Rotated contact share dialog; inverted=$inverted")
                })
            }
        }
    }
}

@Composable
private fun ContactShareContent(
    contact: Contact,
    platformsResult: Result<List<ContactSharePlatform>>?,
    viewModel: PersonViewModel,
    qrSize: Dp,
    qrBackground: Color,
    onRotate: () -> Unit,
) {
    val platforms = platformsResult?.getOrNull().orEmpty()
    val pagerState = rememberPagerState { platforms.size }
    val platformKeys = remember(platforms) { platforms.map { it.key } }
    // Update the account and QR together after the platform carousel settles.
    val platform = platforms.getOrNull(pagerState.settledPage)
    LaunchedEffect(platform) {
        if (platform != null) Log.d(TAG, "Contact share platform settled at page ${pagerState.settledPage}")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        ContactShareHeader(contact, platform, onRotate)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(top = BadgerSpacing.scrollContentTopGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        ) {
            when {
                platformsResult == null -> CircularProgressIndicator()
                platformsResult.isFailure -> Text("无法读取联系人平台资料，请重试")
                platform == null -> Text("此联系人暂无可分享的平台资料")
                else -> {
                    PlatformIconPager(
                        platformKeys = platformKeys,
                        pagerState = pagerState,
                        pagerModifier = Modifier.testTag("contact_share_platform_pager"),
                    )
                    key(platform) { ContactShareQr(platform, viewModel, qrSize, qrBackground) }
                }
            }
        }
    }
}

@Composable
private fun ContactShareHeader(contact: Contact, platform: ContactSharePlatform?, onRotate: () -> Unit) {
    val context = LocalContext.current
    val name = contact.name.ifBlank { "未命名联系人" }
    val accountId = platform?.accountId
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
    ) {
        ContactAvatar(
            name = name,
            avatarUrl = contact.avatarUrl,
            avatarPath = contact.avatarPath,
            size = SHARE_AVATAR_SIZE,
            modifier = Modifier.clickable(onClickLabel = "旋转分享卡片", onClick = onRotate),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
            Text(name, style = MiuixTheme.textStyles.title4, maxLines = CONTACT_NAME_MAX_LINES, overflow = TextOverflow.Ellipsis)
            if (accountId != null) SelectionContainer {
                Text(accountId, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        IconButton(
            onClick = { platform?.let { shareContactPlatform(context, name, it) } },
            enabled = platform != null,
            minWidth = BadgerSize.minimumTouchTarget,
            minHeight = BadgerSize.minimumTouchTarget,
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = "分享联系方式",
                tint = if (platform != null) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
            )
        }
    }
}

private fun shareContactPlatform(context: Context, name: String, platform: ContactSharePlatform) {
    val text = buildString {
        appendLine(name)
        append("${platform.label}：${platform.accountId ?: platform.qrContent}")
        if (platform.accountId != null && platform.accountId != platform.qrContent) {
            append('\n')
            append(platform.qrContent)
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, null))
        Log.d(TAG, "Opened Android chooser for selected contact platform")
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, "Android sharing chooser is unavailable", error)
        Toast.makeText(context, "无法打开系统分享", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ContactShareQr(platform: ContactSharePlatform, viewModel: PersonViewModel, size: Dp, backgroundColor: Color) {
    val result by produceState<Result<Bitmap>?>(null, viewModel, backgroundColor) {
        value = viewModel.renderContactShareQr(platform.qrContent, backgroundColor.toArgb())
    }
    val bitmap = result?.getOrNull()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            result == null -> CircularProgressIndicator()
            bitmap == null -> Text("无法生成二维码", textAlign = TextAlign.Center)
            else -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${platform.label}二维码",
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
