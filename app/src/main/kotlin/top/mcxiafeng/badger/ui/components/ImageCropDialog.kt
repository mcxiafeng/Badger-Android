package top.mcxiafeng.badger.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.scale
import kotlinx.coroutines.yield

/**
 * 图片裁剪对话框
 *
 * 坐标系统（单一真理）：
 * - 图片原始尺寸 bmpW × bmpH（像素）
 * - screenScale = min(screenW/bmpW, screenH/bmpH)，让图片最长边填满屏幕
 * - 图片"基础"渲染尺寸 = bmpW*screenScale × bmpH*screenScale，居中显示
 * - 用户操作 userScale（>= minScaleToFill），相对于基础尺寸额外缩放
 * - 用户拖动 translateX/Y（像素），相对于基础中心位置偏移
 * - 图片实际中心 = 屏幕中心 + (translateX, translateY)
 * - 图片实际渲染尺寸 = 基础尺寸 × userScale
 */
enum class CropMode {
    BANNER,
    AVATAR,
    COVER,
    COLLECTION_BG
}

data class CropConfig(
    val mode: CropMode = CropMode.BANNER,
    val outputWidth: Int = 1080,
    val outputHeight: Int = 0
)

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
    cropConfig: CropConfig = CropConfig()
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(imageUri) {
        loadFailed = false
        yield()
        sourceBitmap = loadBitmapSafely(context, imageUri)
        if (sourceBitmap == null) loadFailed = true
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
    ) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }

        if (sourceBitmap == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (loadFailed) "图片加载失败" else "加载中...",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val bmp = sourceBitmap!!
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
                    // 匹配名片夹卡片的实际视觉比例：2列网格中 (screenWidth-32dp)/2 × 200dp
                    val cardWidth = (maxWidth - 32.dp) / 2f
                    val cardHeight = 200.dp
                    val cardAspect = with(density) { cardWidth.toPx() / cardHeight.toPx() }
                    // 裁剪框尽量大但不超过屏幕可用区域，且保持卡片宽高比
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

            // screenScale：让图片最长边填满屏幕（等价于 ContentScale.Fit）
            val screenScale = min(screenW / bmpW, screenH / bmpH)
            // 图片基础渲染尺寸（fit 屏幕）
            val baseW = bmpW * screenScale
            val baseH = bmpH * screenScale

            // minScaleToFill：在基础尺寸上额外缩放，使图片覆盖裁剪框
            val minScaleToFill = max(cropW / baseW, cropH / baseH)
            // 初始 scale：覆盖 + 30% 余量
            val initScale = minScaleToFill * 1.3f

            var userScale by remember { mutableFloatStateOf(initScale) }
            var translateX by remember { mutableFloatStateOf(0f) }
            var translateY by remember { mutableFloatStateOf(0f) }

            LaunchedEffect(bmp) {
                userScale = initScale
                translateX = 0f
                translateY = 0f
            }

            // 约束：确保图片覆盖裁剪框
            fun constrain() {
                val renderedW = baseW * userScale
                val renderedH = baseH * userScale
                // 图片中心 = 屏幕中心 + translate
                // 图片左边缘 = screenW/2 + translateX - renderedW/2
                // 需要 左边缘 <= cropLeft
                val maxTx = renderedW / 2f - (screenW / 2f - cropLeft)
                val minTx = -(renderedW / 2f - ((cropLeft + cropW) - screenW / 2f))
                val maxTy = renderedH / 2f - (screenH / 2f - cropTop)
                val minTy = -(renderedH / 2f - ((cropTop + cropH) - screenH / 2f))
                translateX = translateX.coerceIn(minOf(minTx, 0f), maxOf(maxTx, 0f))
                translateY = translateY.coerceIn(minOf(minTy, 0f), maxOf(maxTy, 0f))
            }

            // 裁剪
            fun performCrop(): Bitmap? {
                // 1 屏幕像素 = 1/(screenScale * userScale) 原图像素
                val totalScale = screenScale * userScale
                val pxPerScreen = 1f / totalScale

                // 图片中心（屏幕坐标）
                val imgCX = screenW / 2f + translateX
                val imgCY = screenH / 2f + translateY

                // 裁剪框中心（屏幕坐标）
                val cropCX = cropLeft + cropW / 2f
                val cropCY = cropTop + cropH / 2f

                // 裁剪框在原图上的范围
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

                return if (srcW > 1 && srcH > 1 && srcR > srcL && srcB > srcT) {
                    try {
                        val cropped = Bitmap.createBitmap(bmp, srcL.toInt(), srcT.toInt(), srcW, srcH)
                        val outW = cropConfig.outputWidth
                        val outH = if (cropConfig.outputHeight > 0) cropConfig.outputHeight
                            else (outW.toFloat() / srcW * srcH).roundToInt().coerceAtLeast(1)
                        Log.d("ImageCropDialog", "Crop completed: mode=${cropConfig.mode}, output=${outW}x${outH}")
                        cropped.scale(outW, outH).also {
                            if (it != cropped) cropped.recycle()
                        }
                    } catch (_: Exception) { null }
                } else null
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // 图片：fillMaxSize 让布局占满屏幕，ContentScale.Fit 保持比例居中
                // graphicsLayer 在 Fit 结果之上做额外缩放和偏移
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
                                // 以裁剪框中心为锚点缩放
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

                // 遮罩层
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maskColor = Color.Black.copy(alpha = 0.55f)
                    // 上
                    drawRect(maskColor, Offset.Zero, Size(size.width, cropTop))
                    // 下
                    drawRect(maskColor, Offset(0f, cropTop + cropH), Size(size.width, size.height - cropTop - cropH))
                    // 左
                    drawRect(maskColor, Offset(0f, cropTop), Size(cropLeft, cropH))
                    // 右
                    drawRect(maskColor, Offset(cropLeft + cropW, cropTop), Size(size.width - cropLeft - cropW, cropH))
                    // 边框
                    drawRect(
                        Color.White.copy(alpha = 0.35f),
                        Offset(cropLeft, cropTop),
                        Size(cropW, cropH),
                        style = Stroke(width = 6.dp.toPx())
                    )
                }
            }

            // 顶部工具栏
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
                    Icon(Icons.Default.Close, "取消", tint = Color.White)
                }
                Text("移动和缩放图片", color = Color.White, style = MiuixTheme.textStyles.body1)
                IconButton(onClick = {
                    performCrop()?.let { onConfirm(it) }
                    onDismiss()
                }) {
                    Icon(Icons.Default.Check, "确认", tint = Color.White)
                }
            }

            // 底部提示
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
}

private fun loadBitmapSafely(context: Context, uri: Uri): Bitmap? {
    return try {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(stream)
        stream.close()
        bitmap
    } catch (e: Exception) {
        Log.e("ImageCropDialog", "Failed to load: ${e.message}", e)
        null
    }
}
