import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64 {
        binaries {
            framework {
                baseName = "shared"
                isStatic = true
            }
        }
    }
    iosSimulatorArm64 {
        binaries {
            framework {
                baseName = "shared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.coroutines.core)
            implementation(libs.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.core)
            implementation(libs.datastore.preferences.core)
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation(libs.kotlinx.datetime)
            implementation(libs.atomicfu)
            implementation(libs.qrcode.kotlin)
            implementation("org.jetbrains.compose.ui:ui:1.11.1")
            implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation(libs.cmp.window.size)
            implementation(libs.koin.core)
            api(libs.miuix.ui)
            api(libs.miuix.preference)
            api(libs.miuix.blur)
            api(libs.coil.compose)
            api(libs.icons.lucide)
            api(libs.koin.compose.viewmodel)
            api(libs.lifecycle.kmp.runtime.compose)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.okhttp)
            implementation(libs.coroutines.core)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.room.ktx)
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
            implementation(libs.exifinterface)
            implementation(libs.zxing.core)
            implementation(libs.palette)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.core.ktx)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.coil.network.ktor3)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.androidx.sqlite.bundled.jvm)
            implementation(libs.coroutines.test)
        }
    }
}

dependencies {
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
