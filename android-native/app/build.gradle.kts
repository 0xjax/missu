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
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
