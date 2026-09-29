package top.mcxiafeng.badger.page.social.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.platform.QrCodeGenerator
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

private const val TAG = "QrCodeCard"
private const val QR_IMAGE_SIZE_PX = 512
private val QR_CARD_WIDTH = 300.dp
private val QR_IMAGE_SIZE_DP = 200.dp
private val QR_CARD_PADDING_H = 20.dp
private val QR_CARD_PADDING_V = 16.dp
private const val QR_CARD_AVATAR_SIZE = 64

@Composable
fun QrCodeCard(
    userName: String? = null,
    platformName: String? = null,
    platformValue: String? = null,
    avatarPath: String? = null,
    externalShowDialog: Boolean = false,
    onDialogDismiss: (() -> Unit)? = null,
    contentSlot: @Composable () -> Unit = {},
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

    val qrBytes = remember(platformValue, fgColor) {
        QrCodeGenerator.generateBytes(
            content = platformValue.toString(),
            sizePx = QR_IMAGE_SIZE_PX,
            foregroundColor = fgColor.toArgb(),
            backgroundColor = 0x00000000,
        )
    }

    BackHandler(enabled = showQrDialog) { dismiss() }

    val qrDialogVisible = remember { mutableStateOf(false) }
    SideEffect { qrDialogVisible.value = showQrDialog }

    var isInverted by remember { mutableStateOf(false) }
    val cardRotation by animateFloatAsState(
        targetValue = if (isInverted) 180f else 0f,
        animationSpec = tween(BadgerMotion.DURATION_BASE),
        label = "QrCardRotation",
    )

    DialogLayout(
        visible = qrDialogVisible,
        enableWindowDim = true,
        enterTransition = fadeIn(tween(BadgerMotion.DURATION_BASE)),
        exitTransition = fadeOut(tween(BadgerMotion.DURATION_FAST)),
        renderInRootScaffold = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    BadgerLog.d(TAG, "QrCode dialog close")
                    dismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(QR_CARD_WIDTH)
                    .graphicsLayer { rotationZ = cardRotation }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { }
                    .background(
                        MiuixTheme.colorScheme.surface,
                        RoundedCornerShape(BadgerRadius.card)
                    )
                    .padding(horizontal = QR_CARD_PADDING_H, vertical = QR_CARD_PADDING_V),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.clickable {
                        isInverted = !isInverted
                        BadgerLog.d(TAG, "QrCode invert toggle: rotation -> $cardRotation")
                    }
                ) {
                    ContactAvatar(
                        name = userName ?: "?",
                        avatarPath = avatarPath,
                        avatarUrl = avatarPath,
                        size = QR_CARD_AVATAR_SIZE,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (!userName.isNullOrBlank()) {
                    Text(
                        text = userName,
                        style = MiuixTheme.textStyles.title4,
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
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(QR_IMAGE_SIZE_DP),
//                        .clip(miuixShape(BadgerRadius.inner))
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击头像可切换方向",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                if (!isInverted) {
                    contentSlot()
                }
            }
        }
    }
}
