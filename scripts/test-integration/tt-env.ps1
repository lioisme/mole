# tt-env.ps1 — 集成测试环境参数化唯一入口(V45 T0.5 / QA-8)
#
# 用法:`. "$PSScriptRoot\tt-env.ps1"`(必须在任何 adb 调用之前点源)。
#
# 取值优先级:环境变量(MODEV_*) > local.properties(modev.*) > 内置默认(既有硬编码值,
# 保证无配置时行为与 V44 完全一致)。目的:换机/换路径/换 adb 不再改脚本。

$ErrorActionPreference = "Continue"

function Read-ModevLocalProperties {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path $Path)) { return $map }
    # 字节级 UTF-8 读取(properties 为 ISO-8859-1/ASCII 键值,禁 Get-Content 管道改写)
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        $t = $line.Trim()
        if (-not $t -or $t.StartsWith("#") -or $t.StartsWith("!")) { continue }
        $i = $t.IndexOf("=")
        if ($i -le 0) { continue }
        $map[$t.Substring(0, $i).Trim()] = $t.Substring($i + 1).Trim().Replace("\:", ":").Replace("\\", "\")
    }
    return $map
}

# 环境变量 > properties > 默认
function Get-ModevEnv {
    param([string]$EnvKey, [string]$PropKey, [string]$Fallback)
    $fromEnv = [Environment]::GetEnvironmentVariable($EnvKey)
    if ($fromEnv) { return $fromEnv }
    if ($script:MODEV_PROPS.ContainsKey($PropKey)) { return $script:MODEV_PROPS[$PropKey] }
    return $Fallback
}

# 仓库根 = scripts/test-integration/.. /..
$script:REPO_ROOT = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:MODEV_PROPS = Read-ModevLocalProperties (Join-Path $script:REPO_ROOT "local.properties")

$script:LDPLAYER_DIR = Get-ModevEnv "MODEV_LDPLAYER_DIR" "modev.ldplayerDir" "D:\leidian\LDPlayer14"
$script:PLATFORM_TOOLS = Get-ModevEnv "MODEV_PLATFORM_TOOLS" "modev.platformTools" "D:\Android\platform-tools"

# adb:默认取 LDPlayer 自带(与其 adb server 协议版本一致,避免 version mismatch 互杀)
if (-not $script:ADB) {
    $script:ADB = Get-ModevEnv "MODEV_ADB" "modev.adb" (Join-Path $script:LDPLAYER_DIR "adb.exe")
}
if ($script:ADB -and -not (Test-Path $script:ADB)) {
    $onPath = (Get-Command adb.exe -ErrorAction SilentlyContinue)
    if ($onPath) {
        Write-Host "  [env] configured adb not found ($script:ADB), falling back to $($onPath.Source)" -ForegroundColor Yellow
        $script:ADB = $onPath.Source
    }
}

# 设备序列:脚本世界用 TCP 序列(需 adb connect);插桩世界用 emulator-5554(禁双序列同场)
if (-not $script:DEVICE) { $script:DEVICE = Get-ModevEnv "MODEV_DEVICE" "modev.device" "127.0.0.1:5555" }
if (-not $script:ADB_PORT) { $script:ADB_PORT = Get-ModevEnv "MODEV_ADB_SERVER_PORT" "modev.adbServerPort" "5038" }
$env:ANDROID_ADB_SERVER_PORT = "$script:ADB_PORT"

if (-not $script:MODEV_PKG) { $script:MODEV_PKG = Get-ModevEnv "MODEV_PKG" "modev.pkg" "com.lioisme.modev" }
if (-not $script:TARGET_PKG) { $script:TARGET_PKG = Get-ModevEnv "MODEV_TARGET_PKG" "modev.targetPkg" "com.lioisme.modev.testtarget" }

$script:SQLITE = Get-ModevEnv "MODEV_SQLITE" "modev.sqlite" (Join-Path $script:PLATFORM_TOOLS "sqlite3.exe")
$script:DNCONSOLE = Get-ModevEnv "MODEV_DNCONSOLE" "modev.dnconsole" (Join-Path $script:LDPLAYER_DIR "dnconsole.exe")
$script:DNPLAYER = Get-ModevEnv "MODEV_DNPLAYER" "modev.dnplayer" (Join-Path $script:LDPLAYER_DIR "dnplayer.exe")
$script:EXPORT_DIR = Get-ModevEnv "MODEV_EXPORT_DIR" "modev.exportDir" (Join-Path $env:TEMP "modev-export")
$script:REPORT_DIR = Join-Path $script:REPO_ROOT "docs\test-reports"

$script:MODEV_DB = "/data/user/0/$($script:MODEV_PKG)/databases/modev.db"
$script:CFG_PATH = "/data/local/tmp/modev_config_$($script:TARGET_PKG).json"

if (-not $script:TMP) { $script:TMP = Join-Path $env:TEMP "modev-tt" }
New-Item -ItemType Directory -Force -Path $script:TMP | Out-Null
New-Item -ItemType Directory -Force -Path $script:EXPORT_DIR | Out-Null

# TCP 序列在隔离 server 上必须显式 connect(雷电 emulator-5554 与 127.0.0.1:5555 是同一设备的
# 两条序列;V45 Z4 单序列纪律:插桩轮 disconnect TCP,脚本轮只留 TCP)。
& $script:ADB connect $script:DEVICE 2>&1 | Out-Null

# 环境指纹(V45 T0.5:SKIP 台账与报告可归因必须有指纹)
# 有界执行:adb 冷启动 server / 掉线时不得挂死整轮验收(Start-Job + 超时兜底)。
function Invoke-ModevAdbBounded {
    param([string]$Adb, [string]$Device, [string]$ShellCmd, [int]$TimeoutSec = 20)
    $job = Start-Job -ScriptBlock {
        param($a, $d, $c)
        & $a connect $d 2>&1 | Out-Null
        (& $a -s $d shell $c 2>&1 | ForEach-Object { "$_" }) -join "`n"
    } -ArgumentList $Adb, $Device, $ShellCmd
    $null = Wait-Job $job -Timeout $TimeoutSec
    $out = (Receive-Job $job -ErrorAction SilentlyContinue) -join "`n"
    Remove-Job $job -Force -ErrorAction SilentlyContinue
    return "$out"
}

function Get-ModevFingerprint {
    # 设备侧以 key=value 标记输出,解析按标记而非行序(su 警告/空行不得错位)
    $cmd = 'echo rel=$(getprop ro.build.version.release);' +
        ' echo sdk=$(getprop ro.build.version.sdk);' +
        ' echo abi=$(getprop ro.product.cpu.abi);' +
        ' echo model=$(getprop ro.product.model);' +
        ' echo uid=$(su -c id);' +
        ' echo boot=$(getprop sys.boot_completed)'
    $raw = Invoke-ModevAdbBounded -Adb $script:ADB -Device $script:DEVICE -TimeoutSec 30 -ShellCmd $cmd
    # 框架配置探针:PS 5.1 原生参数传递会剥掉内嵌双引号(su 收到 `su -c ls /data/adb`
    # = 在调用者 cwd 执行 ls,静默得到无关输出),故设备侧命令必须用单引号包 su 参数。
    $adb = Invoke-ModevAdbBounded -Adb $script:ADB -Device $script:DEVICE -TimeoutSec 30 -ShellCmd "su -c 'ls /data/adb'"
    $kv = @{}
    foreach ($line in ("$raw" -split "`n")) {
        $t = $line.Trim()
        $i = $t.IndexOf("=")
        if ($i -gt 0 -and $t.Substring(0, $i) -match "^(rel|sdk|abi|model|uid|boot)$") {
            $kv[$t.Substring(0, $i)] = $t.Substring($i + 1).Trim()
        }
    }
    return [pscustomobject][ordered]@{
        device         = $script:DEVICE
        adbServerPort  = $script:ADB_PORT
        adb            = $script:ADB
        androidRelease = $kv["rel"]
        sdkInt         = $kv["sdk"]
        abi            = $kv["abi"]
        model          = $kv["model"]
        rootUid        = $kv["uid"]
        framework      = (($adb -split "`n") | Where-Object { $_ -match "lspd|lsposed|edxp|riru|zygisk|shamiko" } | ForEach-Object { $_.Trim() }) -join ","
        bootCompleted  = $kv["boot"]
        reachable      = [bool]$kv["rel"]
        capturedAt     = (Get-Date).ToString("s")
    }
}
