plugins {
    // NOTE: AGP 9.x 要求 Gradle >= 9.6.0（见 gradle/wrapper/gradle-wrapper.properties）。
    // NOTE: AGP 9 起 Kotlin 支持已内置（:app 不需要 kotlin 插件），
    //       但 :core 是纯 JVM 模块，必须显式应用 kotlin("jvm")。
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
