plugins {
    alias(libs.plugins.kotlin.jvm)
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
    api(libs.okhttp)
    implementation(libs.jsoup)
    api(libs.coroutines.core)

    // 站点有 JSON 接口（登录、收藏、片单）。core 是纯 JVM，不能用 Android 自带的 org.json，
    // 也不需要编译器插件——只用 Json.parseToJsonElement 这类运行时 API。
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
}
