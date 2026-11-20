# missu 开发入口：Android CLI 负责构建/装机/运行/预览/分析，Gradle 负责具体任务。
# 位置：  scripts\build.ps1（仓库根 = 本文件的父目录）
# 用法：  .\scripts\build.ps1            （只构建 debug APK）
#         .\scripts\build.ps1 install    （构建 + 装到已连接的设备）
#         .\scripts\build.ps1 run        （构建 + 装机 + 启动 MainActivity）
#         .\scripts\build.ps1 release    （构建并归档 release APK，沿用 debug 签名）
#         .\scripts\build.ps1 check      （核心/应用测试 + lint + debug 构建）
#         .\scripts\build.ps1 preview    （渲染 Compose Preview 到 .local\previews）
#         .\scripts\build.ps1 analyze    （构建并分析 debug APK）
#         .\scripts\build.ps1 analyze-release（构建并分析 release APK）
#         .\scripts\build.ps1 emulator   （创建/headless 启动 Android 17.2 x86_64 模拟器）
#         .\scripts\build.ps1 layout     （输出模拟器/设备 UI 布局树）
#         .\scripts\build.ps1 screen     （标注截图到 .local\device）
#         .\scripts\build.ps1 tap 'text=搜索'
#         .\scripts\build.ps1 text 'SONE' 'class=EditText'
#         .\scripts\build.ps1 trace      （采集 10 秒启动 Perfetto trace）
#         .\scripts\build.ps1 heapdump   （采集应用 HPROF）
#         .\scripts\build.ps1 hub        （打开 Device Hub）
#         .\scripts\build.ps1 describe   （输出 CLI 识别到的变体与产物路径）
# 环境：  MISSU_NO_DAEMON=1       （自动化采集输出时给 Gradle 加 --no-daemon）
#         ANDROID_SERIAL=<serial> （选择真机/指定设备；不设置则使用模拟器）
# NOTE: 本文件必须保存为 UTF-8 **带 BOM**——Windows PowerShell 5.1 对无 BOM 的 UTF-8 按 ANSI 解码，
#       中文全角字符会吞掉行尾换行，把下一行代码并进注释里（曾导致 JAVA_HOME 从未被设置）。
param([string]$Task = "assembleDebug", [string]$Text, [string]$Target)

$ErrorActionPreference = "Stop"
# NOTE: 本脚本在 scripts/ 下，仓库根（= Gradle 根）是它的父目录；gradlew、app/、core/ 都在那边。
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

# JDK：目录名带版本号（D:\Android\jdk-21），但脚本**自动发现**——升级时解压出 jdk-25 即可，
# 这里会挑 D:\Android 下 major 版本最高的 jdk-N，无需改脚本。找不到才回落到 AS 自带 JBR。
# NOTE: 历史 JDK 17（D:\Android\jdk-17）曾被安全软件按可执行文件拦截；换 JDK 后要确认其 java.exe 能正常跑。
# SDK：全局安装位置，与 AS 的 Android SDK Location 及 local.properties 的 sdk.dir 一致。
$jdkCandidates = @(
    Get-ChildItem 'D:\Android' -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^jdk-(\d+)$' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } |
        Sort-Object -Property @{ Expression = { [int]([regex]::Match($_.Name, '\d+$').Value) }; Descending = $true } |
        ForEach-Object { $_.FullName }
) + @('C:\Program Files\Android\Android Studio\jbr')
$jdk = $jdkCandidates | Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
if (-not $jdk) { throw "找不到可用的 JDK，尝试过：$($jdkCandidates -join ' / ')" }
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

$androidCommand = Get-Command android.exe -ErrorAction SilentlyContinue
$android = if ($androidCommand) {
    $androidCommand.Source
} else {
    Join-Path $env:USERPROFILE 'AppData\AndroidCLI\android.exe'
}
if (-not (Test-Path $android)) { throw "找不到 Android CLI：$android；请先按 docs/android-native.md 安装" }

# NOTE: GRADLE_USER_HOME、ANDROID_USER_HOME、TMP/TEMP 均走用户默认位置。
# 不要把 Android CLI 或其运行包放进 Low IL 工作区，否则无法写系统临时目录与 SDK 锁文件。

if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw ("JAVA_HOME 里找不到 java.exe：{0}（候选：{1}）" -f $env:JAVA_HOME, ($jdkCandidates -join ' / '))
}

$gradleArgs = @('--console=plain')
if ($env:MISSU_NO_DAEMON -eq '1') { $gradleArgs += '--no-daemon' }

$avd = 'medium_phone'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$deviceTasks = @('emulator', 'install', 'run', 'layout', 'screen', 'tap', 'text', 'trace', 'heapdump', 'hub')
if ($Task -in $deviceTasks -and -not $env:ANDROID_SERIAL) {
    $avds = @(& $android --no-metrics emulator list)
    if ($LASTEXITCODE -ne 0) { throw '无法列出 Android 模拟器' }
    if ($avds -notcontains $avd) {
        $avdHome = Join-Path $env:USERPROFILE '.android\avd'
        $avdPath = Join-Path $avdHome "$avd.avd"
        New-Item -ItemType Directory -Force $avdPath | Out-Null
        Copy-Item (Join-Path $root 'scripts\emulator\medium-phone.config.ini') (Join-Path $avdPath 'config.ini') -Force
        $avdIni = "avd.ini.encoding=UTF-8`r`npath=$avdPath`r`npath.rel=avd\$avd.avd`r`ntarget=android-37.2`r`n"
        [System.IO.File]::WriteAllText((Join-Path $avdHome "$avd.ini"), $avdIni, [System.Text.UTF8Encoding]::new($false))
    }
    & $android --no-metrics emulator start $avd --headless
    if ($LASTEXITCODE -ne 0) { throw "模拟器启动失败：$avd" }
    $emulatorLine = & $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' } | Select-Object -First 1
    if (-not $emulatorLine) { throw '模拟器已启动，但 adb 未发现在线 emulator serial' }
    $env:ANDROID_SERIAL = ($emulatorLine -split '\s+')[0]
}
$deviceArgs = @()
if ($env:ANDROID_SERIAL) { $deviceArgs += "--device=$env:ANDROID_SERIAL" }

Push-Location $root
try {
    if ($Task -in @('trace', 'heapdump')) {
        & $android --no-metrics build :app@release -- @gradleArgs
        if ($LASTEXITCODE -ne 0) { throw "Android CLI release 构建退出码 $LASTEXITCODE" }
        $profileApk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
        & $android --no-metrics install @deviceArgs "--apks=$profileApk"
        if ($LASTEXITCODE -ne 0) { throw '性能分析用 release 安装失败' }
    }
    switch ($Task) {
        "assembleDebug"   { & $android --no-metrics build :app@debug -- @gradleArgs }
        "assembleRelease" { & $android --no-metrics build :app@release -- @gradleArgs }
        "install" { & $android --no-metrics install :app@debug @deviceArgs -- @gradleArgs }
        "run"     { & $android --no-metrics run :app@debug @deviceArgs -- @gradleArgs }
        "release" { & $android --no-metrics build :app@release -- @gradleArgs }
        "check"   { & $android --no-metrics build :app@debug -- :core:test :app:testDebugUnitTest :app:lintDebug @gradleArgs }
        "analyze" {
            & $android --no-metrics build :app@debug -- @gradleArgs
            if ($LASTEXITCODE -ne 0) { throw "Android CLI 构建退出码 $LASTEXITCODE" }
            & $android --no-metrics analyze (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')
        }
        "analyze-release" {
            & $android --no-metrics build :app@release -- @gradleArgs
            if ($LASTEXITCODE -ne 0) { throw "Android CLI 构建退出码 $LASTEXITCODE" }
            & $android --no-metrics analyze (Join-Path $root 'app\build\outputs\apk\release\app-release.apk')
        }
        "preview" {
            $previewDir = Join-Path $root '.local\previews'
            New-Item -ItemType Directory -Force $previewDir | Out-Null
            $previews = @(& $android --no-metrics preview list :app -- @gradleArgs |
                Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' })
            if ($LASTEXITCODE -ne 0) { throw "Android CLI 预览发现退出码 $LASTEXITCODE" }
            if ($previews.Count -eq 0) { throw '没有找到 Compose @Preview' }
            $previewArgs = @($previews | ForEach-Object { "--fqn=$_" })
            $previewLog = Join-Path $previewDir 'render.log'
            $previewErrorLog = Join-Path $previewDir 'render-error.log'
            $renderArgs = @('--no-metrics', 'preview', 'render', ':app', '--daemon', '--component-tree', "--output=$previewDir") +
                $previewArgs + @('--') + $gradleArgs
            $renderProcess = Start-Process -FilePath $android -ArgumentList $renderArgs -Wait -PassThru `
                -RedirectStandardOutput $previewLog -RedirectStandardError $previewErrorLog
            if ($renderProcess.ExitCode -ne 0) { throw "预览渲染失败，日志：$previewErrorLog" }
            "Previews: $previewDir"
        }
        "emulator" { "Emulator: $env:ANDROID_SERIAL ($avd)" }
        "layout" {
            $deviceDir = Join-Path $root '.local\device'
            New-Item -ItemType Directory -Force $deviceDir | Out-Null
            $layout = Join-Path $deviceDir 'layout.json'
            & $android --no-metrics layout @deviceArgs --full --pretty "-o=$layout"
            if ($LASTEXITCODE -eq 0) { "Layout: $layout" }
        }
        "screen" {
            $screenDir = Join-Path $root '.local\device'
            New-Item -ItemType Directory -Force $screenDir | Out-Null
            $screen = Join-Path $screenDir 'missu-annotated.png'
            & $android --no-metrics screen capture @deviceArgs --annotate "-o=$screen"
            if ($LASTEXITCODE -eq 0) { "Screenshot: $screen" }
        }
        "tap" {
            if (-not $Text) { throw 'tap 需要选择器，例如 text=搜索' }
            & $android --no-metrics input @deviceArgs tap $Text
        }
        "text" {
            if (-not $Target -or -not $Text) { throw 'text 需要文本与选择器，例如 build.ps1 text SONE class=EditText' }
            & $android --no-metrics input @deviceArgs text $Text $Target --submit
        }
        "trace" {
            $profileDir = Join-Path $root '.local\profiler'
            New-Item -ItemType Directory -Force $profileDir | Out-Null
            $trace = Join-Path $profileDir 'startup.zip'
            & $android --no-metrics profiler systemtrace start @deviceArgs --package=app.missu --launch --enable=compose --duration=10 --wait "-o=$trace"
            if ($LASTEXITCODE -eq 0) { "Trace: $trace" }
        }
        "heapdump" {
            $profileDir = Join-Path $root '.local\profiler'
            New-Item -ItemType Directory -Force $profileDir | Out-Null
            $heap = Join-Path $profileDir 'app.hprof'
            & $android --no-metrics profiler heapdump capture @deviceArgs --process=app.missu --type=hprof --gc "-o=$heap"
            if ($LASTEXITCODE -eq 0) { "Heap dump: $heap" }
        }
        "hub" { & $android --no-metrics device hub @deviceArgs }
        "describe" { & $android --no-metrics describe "--project_dir=$root" }
        default    { & .\gradlew.bat ":app:$Task" @gradleArgs }
    }
    if ($LASTEXITCODE -ne 0) { throw "命令退出码 $LASTEXITCODE" }

    $apk = if ($Task -in @('release', 'analyze-release', 'assembleRelease')) {
        Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
    } else {
        Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    }
    $noApkTasks = @('preview', 'describe', 'emulator', 'layout', 'screen', 'tap', 'text', 'trace', 'heapdump', 'hub')
    if ($Task -notin $noApkTasks -and (Test-Path $apk)) {
        "APK: $apk ($([math]::Round((Get-Item $apk).Length/1MB,2)) MB)"
    }
    if ($Task -eq 'release') {
        $raw = Get-Content (Join-Path $root 'app\build.gradle.kts') -Raw
        $version = [regex]::Match($raw, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
        if (-not $version) { throw '无法从 app/build.gradle.kts 读取 versionName' }
        $archiveDir = Join-Path $root '.local\releases'
        New-Item -ItemType Directory -Force $archiveDir | Out-Null
        $archive = Join-Path $archiveDir "Missu-$version-arm64-v8a.apk"
        Copy-Item $apk $archive -Force
        "Archive: $archive"
        "SHA256: $((Get-FileHash $archive -Algorithm SHA256).Hash)"
    }
} finally {
    Pop-Location
}
