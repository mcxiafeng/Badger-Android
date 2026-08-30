package top.mcxiafeng.badger.pages.scanner

import androidx.compose.ui.geometry.Offset

/**
 * 跨帧边界框平滑器
 *
 * 使用指数移动平均（EMA）对每帧检测到的 QR 码和 OCR 文字框坐标进行平滑，
 * 消除帧间抖动/乱飘现象。
 *
 * - QR 框按 content 字符串跨帧匹配
 * - 文字框按中心点空间距离跨帧匹配
 */
class BoundingBoxSmoother(
    private val qrAlpha: Float = 0.65f,
    private val textAlpha: Float = 0.55f
) {
    /** 上一帧平滑后的 QR 框，按 content 索引 */
    private var prevQrCorners: Map<String, List<Offset>> = emptyMap()

    /** 上一帧平滑后的文字框中心点列表 */
    private var prevTextCorners: List<List<Offset>> = emptyList()

    /**
     * 平滑 QR 码边界框
     *
     * 按内容字符串匹配当前帧与上一帧的框，对匹配到的角点应用 EMA。
     */
    fun smoothQrBoxes(rawBoxes: List<QrBoundingBox>): List<QrBoundingBox> {
        val smoothed = mutableListOf<QrBoundingBox>()
        val newPrevQr = mutableMapOf<String, List<Offset>>()

        for (box in rawBoxes) {
            if (box.corners.size < 4) {
                smoothed.add(box)
                continue
            }
            val prevCorners = prevQrCorners[box.content]
            val smoothedCorners = if (prevCorners != null && prevCorners.size == box.corners.size) {
                box.corners.mapIndexed { i, raw ->
                    Offset(
                        lerp(raw.x, prevCorners[i].x, qrAlpha),
                        lerp(raw.y, prevCorners[i].y, qrAlpha)
                    )
                }
            } else {
                box.corners
            }
            newPrevQr[box.content] = smoothedCorners
            smoothed.add(box.copy(corners = smoothedCorners))
        }
        prevQrCorners = newPrevQr
        return smoothed
    }

    /**
     * 平滑 OCR 文字边界框
     *
     * 文字框没有稳定 ID，使用中心点欧氏距离进行跨帧匹配。
     * 匹配阈值取较小边长的 40%，匹配到的框应用 EMA。
     */
    fun smoothTextBoxes(rawBoxes: List<QrBoundingBox>): List<QrBoundingBox> {
        val smoothed = mutableListOf<QrBoundingBox>()
        val usedPrev = mutableSetOf<Int>()
        val newPrevText = mutableListOf<List<Offset>>()

        for (box in rawBoxes) {
            if (box.corners.size < 4) {
                smoothed.add(box)
                continue
            }
            val center = boxCenter(box.corners)
            val minSide = minOf(
                box.corners[1].x - box.corners[0].x,
                box.corners[2].y - box.corners[1].y
            ).coerceAtLeast(1f)
            val threshold = minSide * 0.4f

            var bestIdx = -1
            var bestDist = Float.MAX_VALUE
            for ((idx, prevCorners) in prevTextCorners.withIndex()) {
                if (idx in usedPrev) continue
                if (prevCorners.size < 4) continue
                val prevCenter = boxCenter(prevCorners)
                val dist = distance(center, prevCenter)
                if (dist < threshold && dist < bestDist) {
                    bestDist = dist
                    bestIdx = idx
                }
            }

            val smoothedCorners = if (bestIdx >= 0) {
                val prevCorners = prevTextCorners[bestIdx]
                usedPrev.add(bestIdx)
                box.corners.mapIndexed { i, raw ->
                    Offset(
                        lerp(raw.x, prevCorners[i].x, textAlpha),
                        lerp(raw.y, prevCorners[i].y, textAlpha)
                    )
                }
            } else {
                box.corners
            }
            newPrevText.add(smoothedCorners)
            smoothed.add(box.copy(corners = smoothedCorners))
        }
        prevTextCorners = newPrevText
        return smoothed
    }

    /** 重置平滑状态（模式切换时调用） */
    fun clear() {
        prevQrCorners = emptyMap()
        prevTextCorners = emptyList()
    }

    private fun lerp(raw: Float, prev: Float, alpha: Float): Float = alpha * raw + (1 - alpha) * prev

    private fun boxCenter(corners: List<Offset>): Offset {
        val cx = corners.map { it.x }.average().toFloat()
        val cy = corners.map { it.y }.average().toFloat()
        return Offset(cx, cy)
    }

    private fun distance(a: Offset, b: Offset): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
