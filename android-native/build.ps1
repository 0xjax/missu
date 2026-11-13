# 构建 missu 原生工程：一次把 5 个环境变量都配好，避免每次重新推导。
# 用法：  .\build.ps1            （只构建 debug APK）
#         .\build.ps1 install    （构建 + 装到已连接的设备）
#         .\build.ps1 release    （构建 release APK，沿用 debug 签名）
# 环境：  MISSU_NO_DAEMON=1       （不保留 Gradle daemon；CI/自动化采集输出时必须开，
#                                  否则 daemon 会一直占着 stdout，管道等不到 EOF）
# NOTE: 本文件必须保存为 UTF-8 **带 BOM**——Windows PowerShell 5.1 对无 BOM 的 UTF-8 按 ANSI 解码，
#       中文全角字符会吞掉行尾换行，把下一行代码并进注释里（曾导致 JAVA_HOME 从未被设置）。
param([string]$Task = "assembleDebug")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# JDK / SDK：全局安装位置（不在项目里），与 AS 的 Android SDK Location 一致。
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

# NOTE: 用户目录下的 ~/.gradle 与 %TEMP% 会被安全软件拦（native-platform.dll 加载失败、
# AccessDeniedException），所以 Gradle 主目录、Android 缓存、临时目录全部落进工作区（已在杀软排除列表）。
$env:GRADLE_USER_HOME = 'D:\gradle-home'
$env:ANDROID_USER_HOME = 'D:\missu\.local\android-home'
$env:TMP = 'D:\missu\.tmp-gradle'
$env:TEMP = $env:TMP
New-Item -ItemType Directory -Force $env:TMP, $env:ANDROID_USER_HOME | Out-Null

if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw "JAVA_HOME 里找不到 java.exe：$env:JAVA_HOME"
}

$extra = @('--console=plain')
if ($env:MISSU_NO_DAEMON -eq '1') { $extra += '--no-daemon' }

Push-Location $root
try {
    switch ($Task) {
        "install" { & .\gradlew.bat :app:assembleDebug :app:installDebug @extra }
        "release" { & .\gradlew.bat :app:assembleRelease @extra }
        default   { & .\gradlew.bat ":app:$Task" @extra }
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle 退出码 $LASTEXITCODE" }

    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    if (Test-Path $apk) { "APK: $apk ($([math]::Round((Get-Item $apk).Length/1MB,2)) MB)" }
} finally {
    Pop-Location
}