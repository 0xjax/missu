plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.missu"
    // NOTE: Compose BOM 2026.09 的 AndroidX 组件要求 compileSdk >= 37；本机装的是 37.0（小版本命名）。
    // targetSdk 仍保持 36——编译用新 API、运行时行为按 36 走，两者可以分开。
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "app.missu"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // NOTE: 沿用 debug 签名，仅自用侧载；换正式 keystore 后旧包必须先卸载。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    // 底部导航的图标（Home / List / Search / Person）来自 icons-core，避免引入 extended 的体积。
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // 网络：:core 已用 api 暴露 okhttp/coroutines，这里显式列出便于阅读；
    // Jsoup 只在 :core 里用，app 不再声明。
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // 图片：Coil 3（Compose 原生 API + 自带内存/磁盘缓存）。
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // 播放：Media3（ExoPlayer）。最小集先给 PlayerView 自带控件。
    // NOTE: Media3 1.x 把 HLS 拆成独立模块——只加 media3-exoplayer 会在 setMediaItem 时
    // 抛 ClassNotFoundException: HlsMediaSource$Factory（已实测踩过一次）。
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    // 播放器控件用官方 Material3 组件（PlayerDefaults.CenterControls/BottomControls），
    // 自带 MD3 的按钮尺寸、进度条、渐变底与无障碍语义，比手搓文字字形可控也更一致。
    implementation("androidx.media3:media3-ui-compose-material3:1.11.1")
    // NOTE: 让 ExoPlayer 走 OkHttp 而不是默认的 HttpURLConnection——媒体主机对后者的 TLS 指纹返回 403（实测）。
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
