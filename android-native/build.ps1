# 构建 missu 原生工程：一次把 5 个环境变量都配好，避免每次重新推导。
# 用法：  .\build.ps1            （只构建 debug APK）
#         .\build.ps1 install    （构建 + 装到已连接的设备）
#         .\build.ps1 release    （构建 release APK，沿用 debug 签名）
param([string]$Task = "assembleDebug")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# JDK / SDK：本机工具链，不在系统 PATH。
$env:JAVA_HOME = 'D:\missu\.local\toolchain\jdk-tmp\jdk-17.0.2'
$env:ANDROID_HOME = 'D:\missu\.local\toolchain\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

# NOTE: 用户目录下的 ~/.gradle 与 %TEMP% 会被安全软件拦（native-platform.dll 加载失败、
# AccessDeniedException），所以 Gradle 主目录、Android 缓存、临时目录全部落进工作区（已在杀软排除列表）。
$env:GRADLE_USER_HOME = 'D:\missu\.local\gradle-home'
$env:ANDROID_USER_HOME = 'D:\missu\.local\android-home'
$env:TMP = 'D:\missu\.tmp-gradle'
$env:TEMP = $env:TMP
New-Item -ItemType Directory -Force $env:TMP, $env:ANDROID_USER_HOME | Out-Null

Push-Location $root
try {
    switch ($Task) {
        "install" { & .\gradlew.bat :app:assembleDebug :app:installDebug --console=plain }
        "release" { & .\gradlew.bat :app:assembleRelease --console=plain }
        default   { & .\gradlew.bat ":app:$Task" --console=plain }
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle 退出码 $LASTEXITCODE" }

    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    if (Test-Path $apk) { "APK: $apk ($([math]::Round((Get-Item $apk).Length/1MB,2)) MB)" }
} finally {
    Pop-Location
}
