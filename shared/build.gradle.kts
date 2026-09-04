import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // [K02 spike] iOS target：Windows 本地交叉编译；真机/模拟器运行留 CI macos runner（K03）
    iosArm64()
    iosSimulatorArm64()
    iosX64()

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
            // [KMP K08] data/model 使用 @Immutable：compose.runtime 跨端（org.jetbrains.compose.runtime
            // 坐标映射 androidx.compose.runtime；CMP 完整切换在 K13）
            implementation("org.jetbrains.compose.runtime:runtime:1.7.3")
            // [KMP K08] nowMs iOS actual 用 kotlinx-datetime
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
            // [KMP K06] HttpUtil（OkHttp 传输层）留在 androidMain；common 侧是 KtorHttpCore
            implementation(libs.okhttp)
            implementation(libs.coroutines.core)
            // [KMP K09] Outbox 调度链（OutboxScheduler/Worker/Store）迁 shared androidMain
            implementation(libs.androidx.work.runtime.ktx)
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
    // KSP2 KMP：ksp() 主配置覆盖全部 target（Room KMP 官方接法）
    ksp(libs.room.compiler)
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
