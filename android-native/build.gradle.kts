plugins {
    // NOTE: AGP 9.x 要求 Gradle >= 9.6.0（见 gradle/wrapper/gradle-wrapper.properties）。
    // NOTE: AGP 9 起 Kotlin 支持已内置，不再需要 org.jetbrains.kotlin.android 插件，
    //       只需要 Compose 编译器插件（Kotlin 2.x 起由它承担 compose 编译）。
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
