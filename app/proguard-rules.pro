-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations
-keepattributes Signature

# ZXing - QR generation entry point (internally uses reflection in codec paths)
-keep class com.google.zxing.qrcode.QRCodeWriter { *; }

# ML Kit - text recognition (uses reflection for model loading)
-keep class com.google.mlkit.vision.text.TextRecognizer { *; }
-keep class com.google.mlkit.vision.text.Text { *; }
-keep class com.google.mlkit.vision.text.Text$TextBlock { *; }
-keep class com.google.mlkit.vision.text.Text$Line { *; }
-keep class com.google.mlkit.vision.text.Text$Element { *; }
-dontwarn com.google.mlkit.**

# OpenCV + WeChatQRCode
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
-keep class com.king.wechat.qrcode.** { *; }
