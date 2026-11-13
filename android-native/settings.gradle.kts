pluginManagement {
    repositories {
        // NOTE: 阿里云镜像放前面——dl.google.com / repo1 在国内容易超时或中断（曾把模拟器镜像传到 Premature EOF）。
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // NOTE: 不设 FAIL_ON_PROJECT_REPOS——本机 GRADLE_USER_HOME 里的 init.d/mirrors.gradle 会在
    // project 级注入阿里云镜像，那是国内加速用的，禁止它会直接让构建失败。
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "missu-native"
include(":app")
include(":core")
