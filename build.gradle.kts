plugins {
    // NOTE: AGP 9.x 要求 Gradle >= 9.6.0（见 gradle/wrapper/gradle-wrapper.properties）。
    // NOTE: AGP 9 起 Kotlin 支持已内置（:app 不需要 kotlin 插件），
    //       但 :core 是纯 JVM 模块，必须显式应用 kotlin("jvm")。
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
}
