package top.mcxiafeng.badger.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.X

private const val TAG = "ImageCropDialog"

@Composable
actual fun ImageCropDialog(
    image: PlatformImage,
    cropConfig: CropConfig,
    onConfirm: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val bmp = image.bitmap

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
    ) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val cropW: Float
        val cropH: Float
        val cropLeft: Float
        val cropTop: Float

        when (cropConfig.mode) {
            CropMode.BANNER -> {
                cropW = with(density) { (maxWidth - 32.dp).toPx() }
                cropH = with(density) { 160.dp.toPx() }
                cropLeft = (screenW - cropW) / 2f
                cropTop = (screenH - cropH) / 2f - with(density) { 30.dp.toPx() }
            }
            CropMode.AVATAR -> {
                val size = with(density) { 240.dp.toPx() }
                cropW = size
                cropH = size
                cropLeft = (screenW - cropW) / 2f
                cropTop = (screenH - cropH) / 2f
            }
            CropMode.COVER -> {
                cropW = with(density) { (maxWidth - 32.dp).toPx() }
                cropH = with(density) { 220.dp.toPx() }
                cropLeft = (screenW - cropW) / 2f
                cropTop = (screenH - cropH) / 2f - with(density) { 20.dp.toPx() }
            }
            CropMode.COLLECTION_BG -> {
                
                val cardWidth = (maxWidth - 32.dp) / 2f
                val cardHeight = 200.dp
                val cardAspect = with(density) { cardWidth.toPx() / cardHeight.toPx() }
                
                val maxCropW = with(density) { (maxWidth - 48.dp).toPx() }
                val maxCropH = with(density) { 300.dp.toPx() }
                val fitByHeight = maxCropH
                val fitByWidthFromH = fitByHeight * cardAspect
                if (fitByWidthFromH <= maxCropW) {
                    cropH = fitByHeight
                    cropW = fitByWidthFromH
                } else {
                    cropW = maxCropW
                    cropH = cropW / cardAspect
                }
                cropLeft = (screenW - cropW) / 2f
                cropTop = (screenH - cropH) / 2f
            }
        }

        
        val screenScale = min(screenW / bmpW, screenH / bmpH)
        
        val baseW = bmpW * screenScale
        val baseH = bmpH * screenScale

        
        val minScaleToFill = max(cropW / baseW, cropH / baseH)
        
        val initScale = minScaleToFill * 1.3f

        var userScale by remember { mutableFloatStateOf(initScale) }
        var translateX by remember { mutableFloatStateOf(0f) }
        var translateY by remember { mutableFloatStateOf(0f) }

        
        fun constrain() {
            val renderedW = baseW * userScale
            val renderedH = baseH * userScale
            
            
            
            val maxTx = renderedW / 2f - (screenW / 2f - cropLeft)
            val minTx = -(renderedW / 2f - ((cropLeft + cropW) - screenW / 2f))
            val maxTy = renderedH / 2f - (screenH / 2f - cropTop)
            val minTy = -(renderedH / 2f - ((cropTop + cropH) - screenH / 2f))
            translateX = translateX.coerceIn(minOf(minTx, 0f), maxOf(maxTx, 0f))
            translateY = translateY.coerceIn(minOf(minTy, 0f), maxOf(maxTy, 0f))
        }

        
        fun performCrop(): ByteArray? {
            
            val totalScale = screenScale * userScale
            val pxPerScreen = 1f / totalScale

            
            val imgCX = screenW / 2f + translateX
            val imgCY = screenH / 2f + translateY

            
            val cropCX = cropLeft + cropW / 2f
            val cropCY = cropTop + cropH / 2f

            
            val relX = (cropCX - imgCX) * pxPerScreen
            val relY = (cropCY - imgCY) * pxPerScreen
            val halfW = (cropW / 2f) * pxPerScreen
            val halfH = (cropH / 2f) * pxPerScreen
            val srcCX = bmpW / 2f + relX
            val srcCY = bmpH / 2f + relY

            val srcL = (srcCX - halfW).coerceIn(0f, bmpW)
            val srcT = (srcCY - halfH).coerceIn(0f, bmpH)
            val srcR = (srcCX + halfW).coerceIn(0f, bmpW)
            val srcB = (srcCY + halfH).coerceIn(0f, bmpH)
            val srcW = (srcR - srcL).roundToInt().coerceAtLeast(1)
            val srcH = (srcB - srcT).roundToInt().coerceAtLeast(1)

            if (!(srcW > 1 && srcH > 1 && srcR > srcL && srcB > srcT)) return null
            return try {
                val cropped = Bitmap.createBitmap(bmp, srcL.toInt(), srcT.toInt(), srcW, srcH)
                val outW = cropConfig.outputWidth
                val outH = if (cropConfig.outputHeight > 0) cropConfig.outputHeight
                    else (outW.toFloat() / srcW * srcH).roundToInt().coerceAtLeast(1)
                BadgerLog.d(TAG, "Crop completed: mode=${cropConfig.mode}, output=${outW}x${outH}")
                val scaled = cropped.scale(outW, outH)
                if (scaled !== cropped) cropped.recycle()
                try {
                    ImageCodec.encodeWebp(PlatformImage(scaled), AVATAR_WEBP_QUALITY)
                } finally {
                    if (!scaled.isRecycled) scaled.recycle()
                }
            } catch (e: Exception) {
                BadgerLog.w(TAG, "图片裁剪失败", e)
                null
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            
            
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            translateX += dragAmount.x
                            translateY += dragAmount.y
                            constrain()
                        }
                    }
                    .pointerInput(minScaleToFill) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (userScale * zoom).coerceIn(minScaleToFill, 15f)
                            
                            val ccx = cropLeft + cropW / 2f
                            val ccy = cropTop + cropH / 2f
                            val scx = screenW / 2f
                            val scy = screenH / 2f
                            val f = newScale / userScale
                            translateX = ccx - scx - (ccx - scx - translateX) * f
                            translateY = ccy - scy - (ccy - scy - translateY) * f
                            userScale = newScale
                            translateX += pan.x
                            translateY += pan.y
                            constrain()
                        }
                    }
                    .graphicsLayer {
                        scaleX = userScale
                        scaleY = userScale
                        translationX = translateX
                        translationY = translateY
                    }
            )

            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maskColor = Color.Black.copy(alpha = 0.55f)
                
                drawRect(maskColor, Offset.Zero, Size(size.width, cropTop))
                
                drawRect(maskColor, Offset(0f, cropTop + cropH), Size(size.width, size.height - cropTop - cropH))
                
                drawRect(maskColor, Offset(0f, cropTop), Size(cropLeft, cropH))
                
                drawRect(maskColor, Offset(cropLeft + cropW, cropTop), Size(size.width - cropLeft - cropW, cropH))
                
                drawRect(
                    Color.White.copy(alpha = 0.35f),
                    Offset(cropLeft, cropTop),
                    Size(cropW, cropH),
                    style = Stroke(width = 6.dp.toPx())
                )
            }
        }

        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp)
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Lucide.X, "取消", tint = Color.White)
            }
            Text("移动和缩放图片", color = Color.White, style = MiuixTheme.textStyles.body1)
            IconButton(onClick = {
                performCrop()?.let { onConfirm(it) }
                onDismiss()
            }) {
                Icon(Lucide.Check, "确认", tint = Color.White)
            }
        }

        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Text(
                "双指缩放 · 单指拖动",
                color = Color.White.copy(alpha = 0.35f),
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val AVATAR_WEBP_QUALITY = 60
