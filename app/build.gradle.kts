plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.missu"
    // NOTE: Compose BOM alpha 2026.11 带 Compose 1.13-rc01 + material3 1.5.0-rc01（全套 M3E），
    // 它要求 compileSdk >= 37.1；本机装的是 37.1（小版本命名）。
    // targetSdk 仍保持 36——编译用新 API、运行时行为按 36 走，两者可以分开。
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "app.missu"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "2.1.0"
        // NOTE: 所有变体（debug / release）都只打 arm64-v8a。包里唯一的 native 库是
        // androidx.graphics.path（每个 ABI 7–12 KB），所以这不是为了省体积，而是让**产物只有
        // 一个确定的架构**：归档名里的 `-arm64-v8a` 必须和包内容对得上，不能靠改名冒充。
        // NOTE: 真机 V2507A 的 `ro.product.cpu.abilist` 只有 arm64-v8a；模拟器 / x86_64 设备
        // 装不上（当前没有这种用法，需要时改这里或加按变体的过滤）。
        ndk { abiFilters.add("arm64-v8a") }
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

    // NOTE: 用 alpha BOM 而不是 2026.09.00：全套 M3E 组件（FloatingToolbar / ButtonGroup /
    // LoadingIndicator / 柔性顶栏）只在 material3 1.5.0 线；1.4.0 稳定线只有「部分 Expressive」。
    // WARNING: 这是预发布栈（Compose 1.13-rc01 + material3 1.5.0-rc01），升级需按 README 的
    // 「升级 SDK 环境依赖前」清单复核，并逐页真机复查。
    implementation(platform("androidx.compose:compose-bom-alpha:2026.11.00"))
    implementation("androidx.compose.material3:material3")
    // 底部导航的图标（Home / List / Search / Person）来自 icons-core，避免引入 extended 的体积。
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    // NOTE: ProcessLifecycleOwner 用来判断「应用已经完全不可见」——桌面图标换色只在那之后做，
    // 在前台禁用当前启动入口会结束它所在的 task（见 LauncherIcons）。
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // 网络：:core 已用 api 暴露 okhttp/coroutines，这里显式列出便于阅读；
    // Jsoup 只在 :core 里用，app 不再声明。
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // 图片：Coil 3（Compose 原生 API + 自带内存/磁盘缓存）。
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // 播放：Media3（ExoPlayer）。
    // NOTE: Media3 1.x 把 HLS 拆成独立模块——只加 media3-exoplayer 会在 setMediaItem 时
    // 抛 ClassNotFoundException: HlsMediaSource$Factory（已实测踩过一次）。
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    // NOTE: 不再需要 media3-ui（View 版 PlayerView）——画面输出用 TextureView + setVideoTextureView，
    // 控件用下面的 Compose 模块，旧 PlayerView 已无任何引用。
    // 播放器控件用官方 Material3 组件（PlayerDefaults.CenterControls/BottomControls），
    // 自带 MD3 的按钮尺寸、进度条、渐变底与无障碍语义，比手搓文字字形可控也更一致。
    implementation("androidx.media3:media3-ui-compose-material3:1.11.1")
    // NOTE: 让 ExoPlayer 走 OkHttp 而不是默认的 HttpURLConnection——媒体主机对后者的 TLS 指纹返回 403（实测）。
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
