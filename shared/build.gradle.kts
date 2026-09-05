import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    // [KMP K10] CameraPreviewSlot expect/actual @Composable 需要 Compose 编译器
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // [K02 spike] iOS target：Windows 本地交叉编译；真机/模拟器运行留 CI macos runner（K03）
    // [KMP K13] iosX64 移除——CMP 1.11 与 miuix-ui 0.9.3 均无 iosX64 变体（JetBrains 已弃 Intel
    // 模拟器 target）；CI macos-15 为 arm64，Intel 模拟器不在 K16 目标形态内
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // [KMP K08] entity/DTO @Serializable 需要透出给 app（ContactMapper 直接用 serializer()）
            api(libs.kotlinx.serialization.json)
            api(libs.coroutines.core)
            implementation(libs.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            // [Q2 裁决] OkHttp 5.4.0 实测无 iOS native 变体 → common 网络层选 Ktor
            implementation(libs.ktor.client.core)
            // [KMP K05] DataStore Preferences 跨端存储 + expect 路径工厂
            implementation(libs.datastore.preferences.core)
            // [KMP K08] data/model 使用 @Immutable：compose.runtime 跨端
            // [KMP K13] CMP 坐标统一切 1.11.1 线（= miuix-ui 0.9.3 实际依赖；
            // android 变体映射 androidx.compose 1.11.2，Gradle 原本就已解析到该版本）
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            // [KMP K08] nowMs iOS actual 用 kotlinx-datetime
            implementation(libs.kotlinx.datetime)
            // [KMP K08-B] atomicfu：common 原子变量（PrefsStore 快照）
            implementation(libs.atomicfu)
            // [KMP K13] UI 层进 commonMain 的完整 CMP 面（foundation 内含 animation/layout；
            // material3 供 SwipeToDismissBox/Checkbox 等少量组件；与 app 的 BOM 冲突解析由 Gradle 收敛）
            implementation("org.jetbrains.compose.ui:ui:1.11.1")
            implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
            // material3 无 1.11 稳定线（仅 alpha）——用 1.9.0（= haze-materials 1.7.2 要求的版本）
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            // [KMP K13b] KoinComponentBy（静态 get 助手）迁 commonMain，需 koin-core（KMP）
            implementation(libs.koin.core)
            // [KMP K13c] UI 层进 shared：Miuix 全家 + Haze（CMP 坐标，iOS target 自带变体）
            api(libs.miuix.ui)
            api(libs.miuix.preference)
            api(libs.miuix.blur)
            api(libs.haze)
            api(libs.haze.materials)
            // [KMP K13c] Coil 3 KMP（AsyncImage；iOS 网络引擎 K16 换 ktor fetcher）
            api(libs.coil.compose)
            // [KMP K13c] Lucide 图标（pages/components 消费；U03 选型）
            api(libs.icons.lucide)
            // [KMP K15] CMP 版 koinViewModel()（pages 全部调用点）
            api(libs.koin.compose.viewmodel)
            api(libs.lifecycle.kmp.runtime.compose)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
            // [KMP K06] HttpUtil（OkHttp 传输层）留在 androidMain；common 侧是 KtorHttpCore
            implementation(libs.okhttp)
            implementation(libs.coroutines.core)
            // [KMP K09] Outbox 调度链（OutboxScheduler/Worker/Store）迁 shared androidMain
            implementation(libs.androidx.work.runtime.ktx)
            // [KMP K13b] dbTransaction actual：room-ktx withTransaction（ContactWriter/TagRepositoryImpl 原路径）
            implementation(libs.room.ktx)
            // [KMP K10] 相机扫码面 actual：CameraX + WeChatQRCode(OpenCV) + ML Kit 中文 + Compose UI
            // （[KMP K13] compose ui/foundation 由 commonMain 的 CMP 坐标 android 变体提供，不再单独钉 1.7.6）
            implementation(libs.camera.core)
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
            implementation(libs.mlkit.chinese)
            // OpenCV 核心 + ABI 原生工件（.so 随 shared 传递打包进 APK；K3 冒烟修复：
            // 缺 ABI 工件时 APK 无 libopencv_java4.so，扫码首帧 UnsatisfiedLinkError 崩溃）
            implementation(libs.wechat.qrcode.opencv)
            implementation(libs.wechat.qrcode.opencv.armv64)
            implementation(libs.wechat.qrcode.opencv.armv7a)
            implementation(libs.wechat.qrcode.opencv.x64)
            implementation(libs.wechat.qrcode)
            implementation(libs.exifinterface)
            // [KMP K13c] 平台边界 actual：二维码生成（ZXing，与原 Methods 同源）+ 返回键/权限（activity-compose）
            implementation(libs.zxing.core)
            // [KMP K13c] 主色提取（extractDominantColor actual）
            implementation(libs.palette)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // [KMP K12] PlatformServices 的 FileProvider
            implementation(libs.androidx.core.ktx)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            // JVM 单测需要 bundled-jvm 变体（含 sqliteJni native）；android 变体的 native 由系统装载
            implementation(libs.androidx.sqlite.bundled.jvm)
            implementation(libs.coroutines.test)
        }
    }
}

dependencies {
    // KSP2 KMP：[Kotlin 2.4] 顶层 ksp() 配置已弃用，改按 target 配置（字符串形式，
    // 因 target 配置在脚本编译期尚未创建，无类型安全访问器）
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

android {
    namespace = "top.mcxiafeng.badger.shared"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
