-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature

# OpenCV JNI — narrowed to actually used entry classes
-keep class org.opencv.core.Mat { *; }
-keep class org.opencv.imgproc.Imgproc { *; }
-dontwarn org.opencv.**

# Haze 在 API < 31 走 RenderScriptBlurEffect，R8 在 release/beta 下会内联 RenderScript 已废弃符号。
# 关闭 R8 对该废弃 API 的优化可避免 NoSuchFieldError / NoSuchMethodError 反射调用失败。
-dontwarn android.renderscript.**
-keep class android.renderscript.** { *; }
-keep class dev.chrisbanes.haze.** { *; }
-dontwarn dev.chrisbanes.haze.**

# miuix-blur 在 R8 后会触发 miuix shader source 引用类被裁剪
-keep class top.yukonga.miuix.kmp.blur.** { *; }
-dontwarn top.yukonga.miuix.kmp.blur.**