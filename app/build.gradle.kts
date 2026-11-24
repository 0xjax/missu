plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

// NOTE: 1.13.0-rc01 的 ui-test Android 构件未发布；应用 implementation 的 alpha BOM 也会约束
// androidTest，所以测试构件固定到最近的稳定 BOM；这些依赖不进入应用主 UI。
configurations.configureEach {
    resolutionStrategy.force(
        "androidx.compose.ui:ui-test:${libs.versions.composeTestVersion.get()}",
        "androidx.compose.ui:ui-test-android:${libs.versions.composeTestVersion.get()}",
        "androidx.compose.ui:ui-test-junit4:${libs.versions.composeTestVersion.get()}",
        "androidx.compose.ui:ui-test-junit4-android:${libs.versions.composeTestVersion.get()}",
        "androidx.compose.ui:ui-test-manifest:${libs.versions.composeTestVersion.get()}",
        "androidx.compose.ui:ui-test-manifest-android:${libs.versions.composeTestVersion.get()}",
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
        //noinspection OldTargetApi — 行为仍按 36 验收；compileSdk 37.1 只为新编译 API。
        targetSdk = 36
        versionCode = 10
        versionName = "3.0.0-rc.1"
        buildConfigField(
            "String",
            "LITERATURE_PACK_BASE_URL",
            "\"https://github.com/0xjax/missu/releases/download/v$versionName\"",
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // NOTE: 真机用 ARM64；本机 x86_64 模拟器需要同名 ABI。release 仍只交付 ARM64。
            ndk { abiFilters.addAll(listOf("arm64-v8a", "x86_64")) }
        }
        release {
            ndk { abiFilters.add("arm64-v8a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // NOTE: 沿用 debug 签名，仅自用侧载；换正式 keystore 后旧包必须先卸载。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core"))

    // NOTE: 用 alpha BOM 而不是 2026.09.00：全套 M3E 组件（FloatingToolbar / ButtonGroup /
    // LoadingIndicator / 柔性顶栏）只在 material3 1.5.0 线；1.4.0 稳定线只有「部分 Expressive」。
    // WARNING: 这是预发布栈（Compose 1.13-rc01 + material3 1.5.0-rc01），升级需按 README 的
    // 「升级 SDK 环境依赖前」清单复核，并逐页用模拟器/真机复查。
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    // 全界面统一使用官方 Outlined 图标；release 由 R8 移除未使用字形。
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime.tracing)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    // 搜索工具框与底部导航的液态玻璃只负责绘制材质，控件与交互仍由本工程实现。
    implementation(libs.haze.core)
    implementation(libs.haze.glass)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    // NOTE: ProcessLifecycleOwner 用来判断「应用已经完全不可见」——桌面图标换色只在那之后做，
    // 在前台禁用当前启动入口会结束它所在的 task（见 LauncherIcons）。
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.profileinstaller)

    // 网络：:core 已用 api 暴露 okhttp/coroutines，这里显式列出便于阅读；
    // Jsoup 只在 :core 里用，app 不再声明。
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    // 本地诗文库：Room 3 + bundled SQLite，预打包数据库与全文索引不依赖系统 SQLite 版本。
    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)
    implementation(libs.sqlite.bundled)

    // 图片：Coil 3（Compose 原生 API + 自带内存/磁盘缓存）。
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 播放：Media3（ExoPlayer）。
    // NOTE: Media3 1.x 把 HLS 拆成独立模块——只加 media3-exoplayer 会在 setMediaItem 时
    // 抛 ClassNotFoundException: HlsMediaSource$Factory（已实测踩过一次）。
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    // 原生 Compose 画面与进度状态；控件外观仍由工程自绘。
    implementation(libs.media3.ui.compose)
    // 只保留官方 PlayerDefaults.ErrorOverlay；画面输出与播放控件由本工程自绘。
    implementation(libs.media3.ui.compose.material3)
    // NOTE: 让 ExoPlayer 走 OkHttp 而不是默认的 HttpURLConnection——媒体主机对后者的 TLS 指纹返回 403（实测）。
    implementation(libs.media3.datasource.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.robolectric)

    debugImplementation(libs.compose.ui.tooling)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
