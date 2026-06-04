-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature

# OpenCV - JNI native bindings require class/method names to remain unobfuscated
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# WeChatQRCode - entry point used directly (no reflection)
-keep class com.king.wechat.qrcode.WeChatQRCodeDetector { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.reflect.TypeToken
