package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults

private const val TAG = "ImageCropDialog.ios"

/**
 * [KMP K13c] iOS actual 骨架：像素级拖拽裁剪需要像素访问层（K16 上收 common）。
 * 当前提供「原图直接确认」的可用降级：整图按 CropConfig.outputWidth 编码返回。
 */
@Composable
actual fun ImageCropDialog(
    image: PlatformImage,
    cropConfig: CropConfig,
    onConfirm: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    BadgerLog.d(TAG, "iOS 裁剪骨架：整图直出 mode=${cropConfig.mode}")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("裁剪功能即将支持", color = Color.White)
            TextButton(
                text = "使用原图",
                onClick = {
                    val bytes = ImageCodec.encodePng(image) ?: ImageCodec.encodeWebp(image)
                    if (bytes != null) onConfirm(bytes) else onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
