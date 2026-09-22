package top.mcxiafeng.badger.page.social.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.platform.QrCodeGenerator
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

private const val TAG = "QrCodeCard"
private const val QR_IMAGE_SIZE_PX = 512

@Composable
fun QrCodeCard(
    content: String,
    userName: String? = null,
    platformName: String? = null,
    platformValue: String? = null,
    avatarPath: String? = null,
    externalShowDialog: Boolean = false,
    onDialogDismiss: (() -> Unit)? = null,
) {
    var colorIndex by remember { mutableIntStateOf(0) }
    var showQrDialogInternal by remember { mutableStateOf(false) }

    val showQrDialog = externalShowDialog || showQrDialogInternal

    fun dismiss() {
        showQrDialogInternal = false
        onDialogDismiss?.invoke()
    }

    val primaryColor = MiuixTheme.colorScheme.primary
    val currentColor = Methods.qrColors[colorIndex % Methods.qrColors.size]
    val fgColor = if (colorIndex == 0) primaryColor else currentColor

    val qrBytes = remember(content, fgColor) {
        QrCodeGenerator.generateBytes(
            content = content,
            sizePx = QR_IMAGE_SIZE_PX,
            foregroundColor = fgColor.toArgb(),
            backgroundColor = 0x00000000,
        )
    }

    BackHandler(enabled = showQrDialog) { dismiss() }

    val qrDialogVisible = remember { mutableStateOf(false) }
    SideEffect { qrDialogVisible.value = showQrDialog }

    var isInverted by remember { mutableStateOf(false) }

    @Composable
    fun QrDialogContent(inverted: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    BadgerLog.d(TAG, "QrCode dialog close")
                    dismiss()
                },
            contentAlignment = if (inverted) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationZ = if (inverted) 180f else 0f }
                    .clickable { }
                    .background(
                        MiuixTheme.colorScheme.surface,
                        if (inverted) RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                if (!inverted) {
                    val statusBarBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                    Spacer(modifier = Modifier.height(with(LocalDensity.current) { statusBarBottom.toDp() }))
                }
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.clickable {
                        isInverted = !inverted
                        BadgerLog.d(TAG, "QrCode invert toggle: $inverted -> $isInverted")
                    }
                ) {
                    ContactAvatar(
                        name = userName ?: "?",
                        avatarPath = avatarPath,
                        size = 72
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (!userName.isNullOrBlank()) {
                    Text(
                        text = userName,
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!platformName.isNullOrBlank() || !platformValue.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!platformName.isNullOrBlank()) {
                            Text(
                                text = platformName,
                                style = MiuixTheme.textStyles.footnote1,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MiuixTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (!platformValue.isNullOrBlank()) {
                            Text(
                                text = platformValue,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(miuixShape(BadgerRadius.inner))
                            .padding(BadgerSpacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBytes != null) {
                            AsyncImage(
                                model = qrBytes,
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击头像可切换方向",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (inverted) {
                    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current)
                    Spacer(modifier = Modifier.height(with(LocalDensity.current) { statusBarTop.toDp() }))
                }
            }
        }
    }

    DialogLayout(
        visible = qrDialogVisible,
        enableWindowDim = true,
        enterTransition = fadeIn(tween(BadgerMotion.DURATION_BASE)) + slideInVertically(tween(BadgerMotion.DURATION_BASE)) { if (isInverted) -it else it },
        exitTransition = fadeOut(tween(BadgerMotion.DURATION_FAST)) + slideOutVertically(tween(BadgerMotion.DURATION_FAST)) { if (isInverted) -it else it },
        renderInRootScaffold = true,
    ) {
        AnimatedContent(
            targetState = isInverted,
            transitionSpec = {
                val direction = if (targetState) -1 else 1
                BadgerLog.d(TAG, "QrCode invert animate: direction=$direction (targetState=$targetState)")
                (slideInVertically(tween(BadgerMotion.DURATION_BASE)) { direction * it } + fadeIn(tween(BadgerMotion.DURATION_BASE))) togetherWith
                        (slideOutVertically(tween(BadgerMotion.DURATION_FAST)) { -direction * it } + fadeOut(tween(BadgerMotion.DURATION_FAST)))
            },
            label = "QrInvertTransition"
        ) { inverted ->
            QrDialogContent(inverted = inverted)
        }
    }
}
