plugins {
    id("org.jetbrains.kotlin.jvm")
}

// NOTE: 这个模块是纯 JVM——不许出现 android.* / androidx.* 依赖。
// 它是「非 UI 层」的唯一归属：App 的 :app 模块、将来可能的服务端都复用同一份解析与测试。
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // api：:app 的 AndroidCookieJar 要实现 okhttp3.CookieJar，类型必须对外可见。
    api("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jsoup:jsoup:1.24.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // 站点有 JSON 接口（登录、收藏、片单）。core 是纯 JVM，不能用 Android 自带的 org.json，
    // 也不需要编译器插件——只用 Json.parseToJsonElement 这类运行时 API。
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")

    testImplementation("junit:junit:4.13.2")
}
