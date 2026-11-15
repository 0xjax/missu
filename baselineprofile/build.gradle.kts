import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.android.test)
}

configurations.configureEach {
    attributes.attribute(
        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID),
    )
    attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
}

android {
    namespace = "app.missu.baselineprofile"
    targetProjectPath = ":app"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.test.ext.junit)
    implementation(libs.test.runner)
}
