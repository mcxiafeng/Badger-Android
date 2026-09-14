package top.mcxiafeng.badger.pages.scanner

import androidx.compose.ui.geometry.Offset

class BoundingBoxSmoother(
    private val qrAlpha: Float = 0.65f,
    private val textAlpha: Float = 0.55f
) {
    
    private var prevQrCorners: Map<String, List<Offset>> = emptyMap()

    
    private var prevTextCorners: List<List<Offset>> = emptyList()

    

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
