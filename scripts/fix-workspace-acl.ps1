# 一次性修复 DSH Windows 沙箱对本工作区的写权限。
#
# 背景：DSH 在每个工作区根目录上放一条 S-1-4-* 的允许 ACE + Low 完整性标签，受限子进程
# （bun / git / expo）靠这两样东西写文件。它们只对「授权之后新建」的对象生效；工作区里
# 早于授权就存在的文件既没有 ACE 也没有 Low 标签，于是子进程报 EPERM / 拒绝访问，而
# 编辑器类工具（不受 ACL 限制）却一切正常。
#
# 关键点：Windows 在给目录重新设置安全描述符时，会把可继承项（ACL 和 Low 标签）传播给
# 已经存在的子对象。所以本脚本只处理根目录，秒级完成；逐个遍历 node_modules 之类的
# 十几万个对象既慢又没必要。极少数传播失败的情况用 -Full 兜底。
#
# NOTE: node_modules 里仍有约 20% 的目录继承了「关闭」的 SACL，标签传播不进去；那里
# 只有「原地改写旧依赖文件」会失败，而删掉依赖目录重装（新对象会自动继承父目录的标签）
# 比 -Full 逐对象打标签更快也更干净。所以 -Full 只在确实要原地改 node_modules 时才需要。
#
# 做法（幂等，可重复执行，普通权限即可，不需要管理员）：
#   1. 根目录上补当前用户的显式完全控制（改 SACL 里的完整性标签需要 WRITE_OWNER，
#      而 Windows 对属主只隐式给 READ_CONTROL 和 WRITE_DAC）；
#   2. 根目录上补沙箱 SID 的允许 ACE；
#   3. 根目录及整棵树设成 Low 完整性标签；
#   4. 抽样复验，失败则非零退出。
#
# 用法（在仓库根目录）：
#   bun run acl:fix
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/fix-workspace-acl.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/fix-workspace-acl.ps1 -Full

param(
    [string]$Path = (Split-Path -Parent $PSScriptRoot),
    [switch]$Full
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Path)) {
    throw "工作区不存在: $Path"
}
$root = (Resolve-Path -LiteralPath $Path).Path

# 从根目录 ACL 里找出 DSH 放的沙箱 SID（S-1-4-*）。找不到说明授权本身没建立：
# 先在 DSH 里随便跑一条命令（哪怕失败）让授权落地，再重复本脚本。
$rootAclText = (& icacls $root) -join "`n"
$sidMatch = [regex]::Match($rootAclText, 'S-1-4-[\d-]+')
if (-not $sidMatch.Success) {
    throw "根目录上找不到 S-1-4-* 沙箱授权。先在 DSH 里随便跑一条命令让授权建立，再重跑本脚本。"
}
$sid = $sidMatch.Value
$user = "$env:USERDOMAIN\$env:USERNAME"
Write-Output "沙箱 SID: $sid"
Write-Output "工作区:   $root"
Write-Output "执行用户: $user"

$sidObj = New-Object System.Security.Principal.SecurityIdentifier($sid)

function New-WorkspaceRules {
    # 目录用可继承规则，文件上继承标记没有意义。
    param([switch]$ForFile)
    $inherit = if ($ForFile) { 'None' } else { 'ContainerInherit, ObjectInherit' }
    $sandbox = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $sidObj,
        [System.Security.AccessControl.FileSystemRights]'Delete, Write, DeleteSubdirectoriesAndFiles, Synchronize',
        $inherit, 'None',
        [System.Security.AccessControl.AccessControlType]::Allow
    )
    $owner = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $user,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        $inherit, 'None',
        [System.Security.AccessControl.AccessControlType]::Allow
    )
    return @{ Sandbox = $sandbox; Owner = $owner }
}

$sw = [System.Diagnostics.Stopwatch]::StartNew()

# 第 1、2 步：根目录一次性设置，Windows 会把两条可继承项传播给已存在的子对象。
Write-Output '第 1、2 步：根目录补 ACL（可继承，自动传播到已存在对象）'
$rules = New-WorkspaceRules
$rootAcl = Get-Acl -LiteralPath $root
$rootAcl.AddAccessRule($rules.Sandbox)
$rootAcl.AddAccessRule($rules.Owner)
Set-Acl -LiteralPath $root -AclObject $rootAcl
Write-Output ("  完成，耗时 {0:n1}s" -f $sw.Elapsed.TotalSeconds)

# 第 3 步：Low 完整性标签。不带 /T 只处理根目录，靠继承传播；-Full 时逐对象兜底。
Write-Output '第 3 步：Low 完整性标签'
if ($Full) {
    $labelOut = & icacls $root /T /C /Q /setintegritylevel '(OI)(CI)Low' 2>&1
} else {
    $labelOut = & icacls $root /C /Q /setintegritylevel '(OI)(CI)Low' 2>&1
}
$labelExit = $LASTEXITCODE
$labelDenied = @($labelOut | Where-Object { $_ -match 'Access is denied|拒绝访问' }).Count
Write-Output ("  exit=$labelExit  denied=$labelDenied  耗时 {0:n1}s" -f $sw.Elapsed.TotalSeconds)
$labelOut | Select-Object -Last 2 | ForEach-Object { Write-Output "  $_" }

# 第 4 步：抽样复验。挑的是历史上最容易漏的对象：根目录文件、源码、临时目录、依赖。
Write-Output '第 4 步：抽样复验'
$checks = @(
    (Join-Path $root 'expo-env.d.ts'),
    (Join-Path $root 'package.json'),
    (Join-Path $root 'src'),
    (Join-Path $root '.local'),
    (Join-Path $root 'scripts'),
    (Join-Path $root 'node_modules')
)
$failures = 0
foreach ($check in $checks) {
    if (-not (Test-Path -LiteralPath $check)) { continue }
    $acl = Get-Acl -LiteralPath $check
    $hasAce = @($acl.Access | Where-Object { $_.IdentityReference -eq $sid -and $_.AccessControlType -eq 'Allow' }).Count -gt 0
    $isLow = @((& icacls $check 2>&1) | Where-Object { $_ -match 'Mandatory Label' }).Count -gt 0
    $state = if ($hasAce -and $isLow) { 'OK  ' } else { $failures++; 'FAIL' }
    Write-Output ("  {0} ACE={1,-5} Low={2,-5} {3}" -f $state, $hasAce, $isLow, $check)
}

Write-Output ("总耗时 {0:n1}s" -f $sw.Elapsed.TotalSeconds)

if ($failures -gt 0 -or $labelDenied -gt 0 -or $labelExit -ne 0) {
    Write-Output '仍有对象没修好。先试 -Full 逐个兜底；仍失败再用管理员身份重跑。'
    exit 1
}

Write-Output '完成。受限子进程（bun / git / expo）现在可以正常写这个工作区。'
