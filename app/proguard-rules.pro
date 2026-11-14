-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature

# OpenCV JNI — narrowed to actually used entry classes
-keep class org.opencv.core.Mat { *; }
-keep class org.opencv.imgproc.Imgproc { *; }
-dontwarn org.opencv.**