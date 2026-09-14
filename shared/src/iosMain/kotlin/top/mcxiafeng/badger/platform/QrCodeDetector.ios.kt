package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.CoreGraphics.CGContextFillRect

@OptIn(ExperimentalForeignApi::class)
actual class QrCodeDetector {

    
    private fun ciImageOf(image: PlatformImage): CIImage? {
        val cgImage = image.uiImage.CGImage ?: return null
        return CIImage.imageWithCGImage(cgImage)
    }

    private fun detectFeatures(image: PlatformImage): List<CIQRCodeFeature> {
        val ciImage = ciImageOf(image) ?: return emptyList()
        val detector = CIDetector.detectorOfType(
            type = CIDetectorTypeQRCode,
            context = null as CIContext?,
            options = mapOf(CIDetectorAccuracy to CIDetectorAccuracyHigh)
        ) ?: return emptyList()
        return detector.featuresInImage(ciImage).filterIsInstance<CIQRCodeFeature>()
    }

    actual fun detectContents(image: PlatformImage): List<String> =
        detectFeatures(image).mapNotNull { it.messageString }.filter { it.isNotEmpty() }

    actual fun detectWithBounds(image: PlatformImage): List<QrDetection> {
        val imgWidth = image.width.toFloat()
        val imgHeight = image.height.toFloat()
        if (imgWidth <= 0f || imgHeight <= 0f) return emptyList()

        return detectFeatures(image).mapNotNull { feature ->
            val content = feature.messageString ?: return@mapNotNull null
            if (content.isEmpty()) return@mapNotNull null
            feature.bounds.useContents {
                
                val left = origin.x
                val top = imgHeight - origin.y - size.height
                val right = origin.x + size.width
                val bottom = top + size.height
                QrDetection(
                    content = content,
                    corners = listOf(
                        QrPoint(left.toFloat(), top.toFloat()),
                        QrPoint(right.toFloat(), top.toFloat()),
                        QrPoint(right.toFloat(), bottom.toFloat()),
                        QrPoint(left.toFloat(), bottom.toFloat())
                    )
                )
            }
        }
    }

    actual fun maskQrRegions(
        image: PlatformImage,
        detections: List<QrDetection>,
        paddingPx: Int
    ): PlatformImage {
        if (detections.isEmpty()) return image

        val imgWidth = image.width.toFloat()
        val imgHeight = image.height.toFloat()
        if (imgWidth <= 0f || imgHeight <= 0f) return image

        val (sizeW, sizeH) = image.uiImage.size.useContents { width to height }
        val renderer = UIGraphicsImageRenderer(size = image.uiImage.size)
        val masked = renderer.imageWithActions { _ ->
            val ctx = UIGraphicsGetCurrentContext() ?: return@imageWithActions
            image.uiImage.drawInRect(CGRectMake(0.0, 0.0, sizeW, sizeH))
            UIColor.whiteColor.set()
            for (qr in detections) {
                if (qr.corners.size < 4) continue
                val minX = (qr.corners.minOf { it.x } - paddingPx).coerceAtLeast(0f)
                val minY = (qr.corners.minOf { it.y } - paddingPx).coerceAtLeast(0f)
                val maxX = (qr.corners.maxOf { it.x } + paddingPx).coerceAtMost(imgWidth)
                val maxY = (qr.corners.maxOf { it.y } + paddingPx).coerceAtMost(imgHeight)
                
                CGContextFillRect(
                    ctx,
                    CGRectMake(minX.toDouble(), minY.toDouble(),
                        (maxX - minX).toDouble(), (maxY - minY).toDouble())
                )
            }
        }
        return PlatformImage(masked)
    }
}
