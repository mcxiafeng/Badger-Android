package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.platform.CameraMode
import top.mcxiafeng.badger.utils.miuixShape

/**
 * 可滑动的模式标签栏
 *
 * 胶囊指示器跟随手指实时滑动，过阈值切换。
 * indicatorFraction: 0f=拍照, 1f=扫描, 中间值为过渡状态
 */
@Composable
internal fun SwipeableModeTab(
    indicatorFraction: Float,
    onModeClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(miuixShape(20.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(3.dp)
    ) {
        Layout(
            content = {
                // 胶囊指示器
                Box(
                    modifier = Modifier
                        .layoutId("capsule")
                        .clip(miuixShape(16.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                )
                CapsuleModeItem(
                    icon = Icons.Outlined.CameraAlt,
                    label = "拍照",
                    isSelected = indicatorFraction < 0.5f,
                    onClick = { onModeClick(0) }
                )
                CapsuleModeItem(
                    icon = Icons.Outlined.QrCodeScanner,
                    label = "扫描",
                    isSelected = indicatorFraction >= 0.5f,
                    onClick = { onModeClick(1) }
                )
            },
            measurePolicy = { measurables, constraints ->
                val capsuleM = measurables.first { it.layoutId == "capsule" }
                val itemMs = measurables.filter { it.layoutId != "capsule" }

                // 测量两个选项（wrap content 宽度）
                val itemConstraints = constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth)
                val items = itemMs.map { it.measure(itemConstraints) }
                val h = items.maxOf { it.height }.coerceAtLeast(constraints.minHeight)
                // 容器宽度 = 两个选项紧邻排列的自然宽度
                val contentW = items.sumOf { it.width }
                val totalW = contentW.coerceIn(constraints.minWidth, constraints.maxWidth)

                // 胶囊宽度 = 单个选项的宽度（取较大值），不超过容器的一半
                val capsuleW = items.maxOf { it.width }.coerceAtMost(totalW / 2)
                val capsuleP = capsuleM.measure(
                    constraints.copy(minWidth = capsuleW, maxWidth = capsuleW, minHeight = h, maxHeight = h)
                )

                layout(totalW, h) {
                    // 居中放置两个选项
                    val offsetX = (totalW - contentW) / 2
                    val item0X = offsetX
                    val item1X = offsetX + items[0].width

                    // 胶囊在两个选项之间滑动
                    val slideRange = items[0].width.toFloat()
                    val capsuleX = item0X + (slideRange * indicatorFraction).toInt()

                    capsuleP.place(capsuleX, 0)
                    items[0].place(item0X, 0)
                    items[1].place(item1X, 0)
                }
            }
        )
    }
}

/**
 * 模式选项（无背景，背景由滑动胶囊指示器提供）
 */
@Composable
private fun CapsuleModeItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val textAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.7f,
        animationSpec = tween(durationMillis = 150),
        label = "capsule_text"
    )
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = textAlpha),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = textAlpha)
        )
    }
}

/**
 * 扫描线覆盖层
 */
@Composable
internal fun ScanLineOverlay(modifier: Modifier = Modifier) {
    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }
    val progress = animProgress.value

    val density = LocalDensity.current
    val boxSizePx = with(density) { 250.dp.toPx() }
    val cornerLenPx = with(density) { 24.dp.toPx() }
    val cornerStrokePx = with(density) { 3.dp.toPx() }
    val lineStrokePx = with(density) { 1.dp.toPx() }
    val offsetTop = with(density) { 50.dp.toPx() }

    val accentColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = (size.width - boxSizePx) / 2
            val top = (size.height - boxSizePx) / 2 - offsetTop
            val right = left + boxSizePx
            val bottom = top + boxSizePx

            // 遮罩 + 直角镂空
            drawRect(color = Color.Black.copy(alpha = 0.5f), size = size)
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSizePx, boxSizePx),
                blendMode = BlendMode.Clear
            )

            // 四角直角线条
            val cornerStyle = Stroke(
                width = cornerStrokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            drawPath(Path().apply { moveTo(left, top + cornerLenPx); lineTo(left, top); lineTo(left + cornerLenPx, top) }, accentColor, style = cornerStyle)
            drawPath(Path().apply { moveTo(right - cornerLenPx, top); lineTo(right, top); lineTo(right, top + cornerLenPx) }, accentColor, style = cornerStyle)
            drawPath(Path().apply { moveTo(left, bottom - cornerLenPx); lineTo(left, bottom); lineTo(left + cornerLenPx, bottom) }, accentColor, style = cornerStyle)
            drawPath(Path().apply { moveTo(right - cornerLenPx, bottom); lineTo(right, bottom); lineTo(right, bottom - cornerLenPx) }, accentColor, style = cornerStyle)

            // 扫描线
            val lineY = top + (bottom - top) * progress
            val lineColors: List<Color> = listOf(
                accentColor.copy(alpha = 0.0f),
                accentColor.copy(alpha = 0.6f),
                accentColor,
                accentColor.copy(alpha = 0.6f),
                accentColor.copy(alpha = 0.0f),
            )
            drawRect(
                brush = Brush.horizontalGradient(colors = lineColors),
                topLeft = Offset(left, lineY - lineStrokePx),
                size = Size(boxSizePx, lineStrokePx * 2)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "将二维码/条形码放入框内", color = Color.White, style = MiuixTheme.textStyles.body2)
            Text(text = "即可自动扫描", color = Color.White.copy(alpha = 0.7f), style = MiuixTheme.textStyles.footnote2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ========== 多码模式 Overlay 组件 ==========

/**
 * 全屏水平扫描线（多码模式）
 *
 * 无遮罩，相机画面完全可见，扫描线从上到下循环。
 */
@Composable
internal fun HorizontalScanLine(modifier: Modifier = Modifier) {
    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }
    val progress = animProgress.value

    val density = LocalDensity.current
    val lineStrokePx = with(density) { 0.5.dp.toPx() }
    val accentColor = MiuixTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxSize()) {
        val lineY = size.height * progress
        val lineColors = listOf(
            accentColor.copy(alpha = 0.0f),
            accentColor.copy(alpha = 0.3f),
            accentColor.copy(alpha = 0.8f),
            accentColor,
            accentColor.copy(alpha = 0.8f),
            accentColor.copy(alpha = 0.4f),
            accentColor.copy(alpha = 0.0f),
        )
        drawRect(
            brush = Brush.verticalGradient(colors = lineColors),
            topLeft = Offset(0f, lineY - lineStrokePx * 2),
            size = Size(size.width, lineStrokePx * 4)
        )
    }
}

/**
 * QR码动态框选覆盖层
 *
 * 每个QR码绘制4个圆点 + 四边形连线，圆点位于二维码的4个真实角点位置。
 * 角点顺序直接采用 WeChatQRCodeDetector 返回的顺序（顺时针），不再做排序。
 */
@Composable
internal fun QrBoundingBoxOverlay(
    boundingBoxes: List<QrBoundingBox>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { 5.dp.toPx() }
    val dotInnerPx = with(density) { 2.dp.toPx() }
    val lineStrokePx = with(density) { 1.5.dp.toPx() }
    val accentColor = MiuixTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxSize()) {
        val lineStyle = Stroke(
            width = lineStrokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        for (box in boundingBoxes) {
            if (box.corners.size < 4) continue

            val corners = box.corners
            // [修复防御]: 完全不再在 Canvas 内打 Log.d —— Canvas onDraw 由
            // BoundingBoxSmoother 每帧推一次 (60fps),任何一次 var 赋值/比较都在
            // hot path 里,且 reminder:之前那次只在「内容变化」时打的版本实测还是
            // 刷屏 —— 因为 corners 每帧都在变,即便 content 字符串没变,绘图循环
            // 也会被逐帧触发。直接把日志移到调用方 (ScannerPage.kt 的 onQrCodesWithBounds
            // LaunchedEffect 里,那里按帧节流到 200ms 已经天然安全)。

            // 四边形连线（沿探测器返回的角点顺序围成四边形）
            val path = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                for (i in 1 until 4) {
                    lineTo(corners[i].x, corners[i].y)
                }
                close()
            }
            drawPath(path, accentColor, style = lineStyle)

            // 四角画圆点（外圈主题色 + 内圈白色点）
            for (corner in corners) {
                drawCircle(
                    color = accentColor,
                    radius = dotRadiusPx,
                    center = corner
                )
                drawCircle(
                    color = Color.White,
                    radius = dotInnerPx,
                    center = corner
                )
            }
        }
    }
}

/**
 * 累积码数计数徽章
 */
@Composable
internal fun QrCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    Box(
        modifier = modifier
            .clip(miuixShape(16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$count 个码",
                color = Color.White,
                style = MiuixTheme.textStyles.footnote1
            )
        }
    }
}

/**
 * OCR 文字段数计数徽章
 */
@Composable
internal fun TextCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    Box(
        modifier = modifier
            .clip(miuixShape(16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.TextFields,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "识别到 $count 段文字",
                color = Color.White,
                style = MiuixTheme.textStyles.footnote1
            )
        }
    }
}

/**
 * OCR 文字区域框选覆盖层
 *
 * 半透明绿色矩形框，与 QR 角括号样式区分，不遮挡内容。
 */
@Composable
internal fun TextBoundingBoxOverlay(
    textBoundingBoxes: List<QrBoundingBox>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val strokePx = with(density) { 1.5.dp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val textStyle = Stroke(width = strokePx, cap = StrokeCap.Round)
        val textColor = Color(0xFF4CAF50).copy(alpha = 0.55f) // 绿色半透明

        for (box in textBoundingBoxes) {
            if (box.corners.size < 4) continue
            val p0 = box.corners[0] // 左上
            val p2 = box.corners[2] // 右下

            drawRect(
                color = textColor,
                topLeft = p0,
                size = Size(p2.x - p0.x, p2.y - p0.y),
                style = textStyle
            )
        }
    }
}

/**
 * 多码模式组合覆盖层
 */
@Composable
internal fun MultiQrScanOverlay(
    boundingBoxes: List<QrBoundingBox>,
    accumulatedCount: Int,
    textBoundingBoxes: List<QrBoundingBox> = emptyList(),
    textBlockCount: Int = 0,
    aiOcrEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 全屏扫描线
        HorizontalScanLine()
        // OCR 文字区域框（QR 下方绘制，避免遮挡 QR 框）
        if (aiOcrEnabled && textBoundingBoxes.isNotEmpty()) {
            TextBoundingBoxOverlay(textBoundingBoxes)
        }
        // QR码框选
        QrBoundingBoxOverlay(boundingBoxes)
        // 计数徽章
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QrCountBadge(count = accumulatedCount)
            if (aiOcrEnabled) {
                TextCountBadge(count = textBlockCount)
            }
        }
        // 底部提示
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "对准二维码即可自动识别",
                color = Color.White,
                style = MiuixTheme.textStyles.body2
            )
            Text(
                text = "点击下方按钮确认收集",
                color = Color.White.copy(alpha = 0.7f),
                style = MiuixTheme.textStyles.footnote2,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
