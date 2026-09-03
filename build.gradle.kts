plugins {
    alias(libs.plugins.androidApplication) apply false
    // [KMP K02] androidLibrary 同理需在根项目钉住版本（AGP 单 jar 含全部插件类）
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    // [KMP K02] 必须在根项目 apply false 钉住版本：KGP 单 jar 内含全部插件类，
    // 不在此声明则 :shared 解析 kotlinMultiplatform 时报「already on the classpath with an unknown version」
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ksp) apply false
}
