buildscript {
    dependencies {
        // AGP 9 内置 Kotlin 默认 KGP 2.2.10；显式沿用 2.3.0（与 KSP 2.3.6、compose/serialization 编译器插件同版）
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ksp) apply false
}
