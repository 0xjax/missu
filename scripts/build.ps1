# 构建 missu 原生工程：把需要的环境变量一次配好，避免每次重新推导。
# 位置：  scripts\build.ps1（仓库根 = 本文件的父目录）
# 用法：  .\scripts\build.ps1            （只构建 debug APK）
#         .\scripts\build.ps1 install    （构建 + 装到已连接的设备）
#         .\scripts\build.ps1 release    （构建 release APK，沿用 debug 签名）
# 环境：  MISSU_NO_DAEMON=1       （不保留 Gradle daemon；自动化采集输出时必须开，
#                                  否则 daemon 会一直占着 stdout，管道等不到 EOF）
# NOTE: 本文件必须保存为 UTF-8 **带 BOM**——Windows PowerShell 5.1 对无 BOM 的 UTF-8 按 ANSI 解码，
#       中文全角字符会吞掉行尾换行，把下一行代码并进注释里（曾导致 JAVA_HOME 从未被设置）。
param([string]$Task = "assembleDebug")

$ErrorActionPreference = "Stop"
# NOTE: 本脚本在 scripts/ 下，仓库根（= Gradle 根）是它的父目录；gradlew、app/、.tmp-gradle 都在那边。
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

# NOTE: GRADLE_USER_HOME 不设，走标准位置 %USERPROFILE%\.gradle（实测可用，5 GB 缓存已迁到那里）。
# NOTE: TMP/TEMP 不再覆盖，用系统默认目录。实测（2026-11-14）`gradlew :app:assembleDebug --rerun-tasks
# --no-build-cache` 在默认 %TEMP% 下 BUILD SUCCESSFUL——早先「%TEMP% 被安全软件拦」的结论只对 adb 成立
# （adb server 要写 %TEMP%\adb.log），Gradle 本身不需要，详见 docs/android-native.md「已知坑」。
$env:ANDROID_USER_HOME = Join-Path $root '.local\android-home'
New-Item -ItemType Directory -Force $env:ANDROID_USER_HOME | Out-Null

if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw ("JAVA_HOME 里找不到 java.exe：{0}（候选：{1}）" -f $env:JAVA_HOME, ($jdkCandidates -join ' / '))
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
