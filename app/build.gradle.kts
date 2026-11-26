import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.androidApplication)
    // AGP 9 起内置 Kotlin 编译，org.jetbrains.kotlin.android 插件不再应用（KGP 2.3.0 由根 buildscript classpath 声明）
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
        // miuix 0.9.5-rc01 的 inline 函数为 JVM 21 字节码，项目 target 必须对齐（构建机 JDK 21）
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
    // miuix-nav 0.9.5-rc01 的 inline 函数为 JVM 21 字节码，项目 target 必须对齐（构建机 JDK 21）
    compileSdkMinor = 0
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// AGP 9.1.1 起可直接使用 compose 1.13 家族与 miuix 0.9.5-rc01 的原生依赖声明，不再需要版本固定策略。
androidComponents {
    onVariants { variant ->
        val appName = "Badger"
        val date = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        )
        variant.outputs.forEach { output ->
            // AGP 9 新 Variant API 移除了 APK 改名能力（issuetracker.google.com/issues/480062612），
            // 官方未补前暂用 impl 类；升级 AGP 时需回归此块。
            val impl = output as? com.android.build.api.variant.impl.VariantOutputImpl ?: return@forEach
            val abi = impl.filters.firstOrNull {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            val version = "${impl.versionName.get()}-${impl.versionCode.get()}"
            impl.outputFileName.set("${appName}-${version}-${abi}-${date}.apk")
        }
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
