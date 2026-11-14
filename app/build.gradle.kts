import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    // Miuix Nav 的 rememberNavBackStack 通过 kotlinx-serialization 持久化路由栈
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "top.mcxiafeng.badger"
    compileSdk = 37

    val keystoreFile = System.getenv("KEYSTORE_FILE")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("KEY_ALIAS")
    val keyPassword = System.getenv("KEY_PASSWORD")

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "top.mcxiafeng.badger"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.0"

        buildConfigField("String", "BUILD_DATE", "\"${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))}\"")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        create("beta") {
            isMinifyEnabled = true
            isShrinkResources = true
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // miuix-nav 0.9.4 的 inline 函数为 JVM 21 字节码，项目 target 必须对齐（构建机 JDK 21）
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    buildToolsVersion = "34.0.0"
    compileSdkMinor = 0
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// [兼容约束] miuix-nav 0.9.4 声明 compose 1.12 / lifecycle 2.11，其中 compose 家族与
// lifecycle-compose 家族的 AAR 元数据要求 AGP 9.1+，项目当前 AGP 8.12 无法使用。
// 逐模块核对 AAR 元数据后的兼容组合：
// - compose 全家 -> 1.11.2（miuix-ui 0.9.3 时代基线；miuix-nav 只用稳定 API）
// - lifecycle-viewmodel / -viewmodel-savedstate -> 2.11.0（minAGP 8.1.1；miuix-nav 依赖 2.11 新增的
//   ViewModelStoreOwnerDefaults，必须保真版本）
// - lifecycle 其余模块（-runtime-compose / -viewmodel-compose 等）-> 2.10.0（minAGP 8.6.0；
//   miuix-nav 使用的 rememberLifecycleOwner / LocalLifecycleOwner 在 2.10 已存在，
//   且 2.10 代码 + 2.11 core 运行时为向后兼容方向）
// MiuixNavDisplaySmokeTest 在 Robolectric 上真实渲染 NavDisplay，保障该组合的运行时链接。
// 升级 AGP >= 9.1 后可整体移除本策略。
configurations.all {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.compose.ui",
            "androidx.compose.foundation",
            "androidx.compose.animation",
            "androidx.compose.runtime" -> if (requested.version != null) useVersion("1.11.2")
            "androidx.lifecycle" -> if (requested.version != null && requested.version!!.startsWith("2.1")) {
                // -android 后缀是 KMP 的平台变体 artifact 名，与基础名一并匹配
                val isViewmodelCore = requested.name.startsWith("lifecycle-viewmodel") &&
                    !requested.name.contains("compose")
                if (isViewmodelCore) useVersion("2.11.0") else useVersion("2.10.0")
            }
        }
    }
}

android.applicationVariants.all {
    val variant = this
    variant.outputs.all {
        val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
        val appName = "Badger"
        val version = "${variant.versionName}-${variant.versionCode}"
        val date = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        )
        val abi = output.filters.firstOrNull { it.filterType == "ABI" }?.identifier
            ?: "universal"
        output.outputFileName = "${appName}-${version}-${abi}-${date}.apk"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    // [修复防御]: JDK 17+ 默认禁止 self-attach，但 mockk 通过 ByteBuddy 用 self-attach 安装 javaagent。
    // 没有这个 flag，所有用到 mockk 的单元测试在 setup() 阶段就抛 IllegalStateException 崩溃。
    systemProperty("jdk.attach.allowAttachSelf", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.nav)

    implementation(libs.zxing.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.mlkit.chinese)

    implementation(libs.wechat.qrcode.opencv)
    implementation(libs.wechat.qrcode.opencv.armv64)
    implementation(libs.wechat.qrcode.opencv.armv7a)
    implementation(libs.wechat.qrcode.opencv.x64)
    implementation(libs.wechat.qrcode)

    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.security.crypto)
    implementation(libs.exifinterface)
    implementation(libs.palette)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.runtime)
    testImplementation(libs.room.ktx)
    testImplementation(libs.room.testing)
    testImplementation(libs.gson)
    testImplementation(libs.zxing.core)
    kspTest(libs.room.compiler)
}
