import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    // [KMP K07] MigrationChainTest：schema JSON 挂 debug assets（Robolectric 读 mergeDebugAssets，
    // 见 unit_test_config assets= 指向；仅 debug 受影响，release 不打包 schema）
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")

    buildToolsVersion = "37.0.0"
    compileSdkMinor = 0
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")

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
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    // [修复防御]: JDK 17+ 默认禁止 self-attach,但 mockk 通过 ByteBuddy 用 self-attach 安装 javaagent。
    // 没有这个 flag,所有用到 mockk 的单元测试在 setup() 阶段就抛 IllegalStateException 崩溃。
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
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)

    implementation(libs.zxing.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
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

    implementation(project(":shared"))
    implementation(libs.okhttp)
    // [KMP K05] DataStore Preferences（SharedPreferences → DataStore 迁移）
    implementation(libs.datastore.preferences)

    // [KMP K07] Robolectric JVM 测试需要 bundled-jvm 变体（jar 内含 sqliteJni native，
    // Android 变体的 native 由系统装载；Room KMP bundled driver 在 JVM 跑 Robolectric 必需）
    testImplementation(libs.androidx.sqlite.bundled.jvm)
    // [KMP K04] Gson → kotlinx.serialization：主源集已零 Gson；
    // Gson 仅保留在 testImplementation 供 JsonMigrationParityTest 做双实现对照 oracle
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.exifinterface)
    implementation(libs.palette)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // [§14.2] Koin 接入:替代 Hilt 的 pure-Kotlin DI 容器
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // [V2-P4] WorkManager:PendingUpload 队列 + 30s 恢复窗口兜底
    implementation(libs.androidx.work.runtime.ktx)
    // [§14.2] hilt-work + hilt-compiler (KSP) 已移除 ——
    // WorkerFactory 现在直接调 Koin GlobalContext.get(),不依赖 hilt-work 桥接。

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.runtime)
    testImplementation(libs.room.ktx)
    // [KMP K07] room-testing-android：KMP 版 MigrationTestHelper（SQLiteDriver 构造器），
    // 普通 room-testing 坐标只透出 SupportSQLite 变体
    testImplementation("androidx.room:room-testing-android:2.8.4")
    testImplementation(libs.gson) // [K04] 对照 oracle：主源集零 Gson，仅 JsonMigrationParityTest 用
    testImplementation(libs.zxing.core)
    testImplementation(libs.koin.test)
    kspTest(libs.room.compiler)
}
