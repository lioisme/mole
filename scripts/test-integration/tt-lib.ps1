# tt-lib.ps1 — Modev × ModevTestTarget 集成测试共享库
# 用法: . "$PSScriptRoot\tt-lib.ps1"

$script:ADB = "D:\leidian\LDPlayer14\adb.exe"
$script:DEVICE = "127.0.0.1:5555"
# 独立 adb server 端口:隔离其他工具(platform-tools/IDE)的 server 互杀导致的掉线
$env:ANDROID_ADB_SERVER_PORT = "5038"
$script:MODEV_PKG = "com.lioisme.modev"
$script:TARGET_PKG = "com.lioisme.modev.testtarget"
$script:MODEV_DB = "/data/user/0/com.lioisme.modev/databases/modev.db"
$script:CFG_PATH = "/data/local/tmp/modev_config_$($script:TARGET_PKG).json"
$script:TMP = Join-Path $env:TEMP "modev-tt"
New-Item -ItemType Directory -Force -Path $script:TMP | Out-Null

$script:PASS = 0; $script:FAIL = 0; $script:SKIP = 0
$script:RESULTS = New-Object System.Collections.ArrayList
# adb 的 stderr 警告(如 top-most instance)不得终止脚本;真实失败由显式检查兜底
$ErrorActionPreference = "Continue"

function Reconnect {
    & $script:ADB connect $script:DEVICE 2>$null | Out-Null
}

# 等待设备可达且启动完成;连续失联则冷重启 dnplayer(LDPlayer VM 偶发死亡自愈)
function WaitDevice([int]$timeoutSec = 420) {
    $ErrorActionPreference = "Continue"
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    $miss = 0
    while ((Get-Date) -lt $deadline) {
        Reconnect
        $st = (& $script:ADB -s $script:DEVICE shell "getprop sys.boot_completed" 2>&1 | ForEach-Object { "$_" }) -join ""
        if ("$st".Trim() -eq "1") { return $true }
        if ("$st" -match "not found|offline|closed|refused") { $miss++ } else { $miss = 0 }
        if ($miss -ge 8) {
            Write-Host "  [VM] device lost, cold relaunching dnplayer..." -ForegroundColor Yellow
            Get-Process dnplayer,Ld9BoxHeadless,VBoxNetDHCP -ErrorAction SilentlyContinue | Stop-Process -Force
            Start-Sleep -Seconds 6
            Start-Process "D:\leidian\LDPlayer14\dnplayer.exe" -ArgumentList "-index","0" -WorkingDirectory "D:\leidian\LDPlayer14"
            $miss = 0
            $deadline = (Get-Date).AddSeconds(480)
        }
        Start-Sleep -Seconds 5
    }
    return $false
}

# 批量 kind 计数:单条查询返回 recordType|kind|count 全表(sqlite list 模式天然 | 分隔)
function KindCountsAll {
    $rows = @(Q "SELECT recordType, kind, COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' GROUP BY 1,2;")
    $map = @{}
    foreach ($r in $rows) {
        $p = $r -split '\|'
        if ($p.Count -ge 3 -and $p[2] -match "^\d+$") { $map["$($p[0])|$($p[1])"] = [int]$p[2] }
    }
    return $map
}

function AdbShell([string]$cmd) {
    $ErrorActionPreference = "Continue"
    $out = ""
    for ($i = 0; $i -lt 5; $i++) {
        Reconnect
        $out = ((& $script:ADB -s $script:DEVICE shell $cmd 2>&1 | ForEach-Object { "$_" }) -join "`n")
        if ($out -notmatch "error: closed|device not found|device offline|unable to connect") { return $out }
        Start-Sleep -Seconds 2
    }
    return $out
}

function SuShell([string]$cmd) {
    $ErrorActionPreference = "Continue"
    $out = ""
    for ($i = 0; $i -lt 5; $i++) {
        Reconnect
        $out = ((& $script:ADB -s $script:DEVICE shell "su -c '$cmd'" 2>&1 | ForEach-Object { "$_" }) -join "`n")
        if ($out -notmatch "error: closed|device not found|device offline|unable to connect") { return $out }
        Start-Sleep -Seconds 2
    }
    return $out
}

function PushScript([string]$name, [string]$body) {
    $ErrorActionPreference = "Continue"
    $local = Join-Path $script:TMP $name
    [IO.File]::WriteAllText($local, ($body -replace "`r", ""), (New-Object System.Text.UTF8Encoding($false)))
    for ($i = 0; $i -lt 5; $i++) {
        Reconnect
        $r = (& $script:ADB -s $script:DEVICE push $local "/data/local/tmp/$name" 2>&1 | ForEach-Object { "$_" }) -join "`n"
        if ($r -match "bytes pushed|error: closed|device") { if ($r -match "bytes pushed") { return } }
        Start-Sleep -Seconds 2
    }
}

# run-as 直查宿主 DB(debuggable 包):零 su 开销。SQL 经文件 .read 执行,
# 规避 PowerShell→adb→sh 三层引号剥离;输出行以 | 分隔(调用方约定)。
function Q([string]$sql) {
    $local = Join-Path $script:TMP "ttq.sql"
    [IO.File]::WriteAllText($local, ($sql -replace "`r", ""), (New-Object System.Text.UTF8Encoding($false)))
    for ($i = 0; $i -lt 5; $i++) {
        if ($i -ge 2) { WaitDevice 180 | Out-Null }
        Reconnect
        & $script:ADB -s $script:DEVICE push $local /data/local/tmp/ttq.sql 2>&1 | Out-Null
        $out = AdbShell "run-as $script:MODEV_PKG sqlite3 databases/modev.db '.read /data/local/tmp/ttq.sql'"
        if ($out -notmatch "error: closed|device not found|device offline|unable to connect|cannot connect") {
            return @($out -split "`n" | Where-Object { $_ -ne "" })
        }
        Start-Sleep -Seconds 2
    }
    throw "adb unstable during query: $out"
}

function ClearDb {
    Q "DELETE FROM crypto_records;" | Out-Null
}

# 状态重置:推配置 → 重启靶 → 起宿主 → 空跑用例触发旧 spool 回放 → 排空 → 清库。
# 消除「旧轮未回放记录污染新轮断言」的时序缺陷。
function ResetState([string]$profileJson) {
    WaitDevice | Out-Null
    if ($null -ne $profileJson) { PushConfig $profileJson }
    RestartTarget
    EnsureHost
    RunCase "list" "REGISTRY" 4
    WaitSpoolDrain | Out-Null
    ClearDb
}

function PushConfig([string]$json) {
    $local = Join-Path $script:TMP "cfg.json"
    [IO.File]::WriteAllText($local, $json, (New-Object System.Text.UTF8Encoding($false)))
    Reconnect
    & $script:ADB -s $script:DEVICE push $local $script:CFG_PATH 2>&1 | Out-Null
}

function RemoveConfig { AdbShell "rm -f $($script:CFG_PATH)" | Out-Null }

function RestartTarget {
    AdbShell "am force-stop $script:TARGET_PKG" | Out-Null
    Start-Sleep -Seconds 2
}

function EnsureHost {
    $p = AdbShell "pidof $script:MODEV_PKG"
    if (-not ($p -match "\d")) {
        AdbShell "am start -n $script:MODEV_PKG/.ui.MainActivity" | Out-Null
        Start-Sleep -Seconds 6
    }
}

function RunCase([string]$domain, [string]$case, [int]$waitSec = 5, [string]$extra = "") {
    # 不加 -S:靶进程跨用例复用(TriggerActivity.onNewIntent 再分发),
    # 避免逐用例杀进程与异步记录投递竞态(记录在内存队列即随进程死)
    AdbShell "am start -n $script:TARGET_PKG/.TriggerActivity --es domain $domain --es case $case $extra" | Out-Null
    Start-Sleep -Seconds $waitSec
}

function GetTt([string]$case) {
    $ErrorActionPreference = "Continue"
    for ($i = 0; $i -lt 5; $i++) {
        Reconnect
        $lines = & $script:ADB -s $script:DEVICE shell "logcat -d -s TT_RESULT" 2>&1 | ForEach-Object { "$_" }
        $joined = $lines -join "`n"
        if ($joined -notmatch "error: closed|device not found|device offline") {
            return (($lines | Select-String "case=$case " | Select-Object -Last 1) -replace "^.*result=", "")
        }
        Start-Sleep -Seconds 2
    }
    return ""
}

function WaitTt([string]$case, [int]$timeoutSec = 15) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $r = GetTt $case
        if ($r) { return $r }
        Start-Sleep -Seconds 2
    }
    return $null
}

function KindCount([string]$recordType, [string]$kind) {
    return CountChecked "SELECT COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND recordType='$recordType' AND kind='$kind';"
}

function TypeCount([string]$recordType) {
    return CountChecked "SELECT COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND recordType='$recordType';"
}

# 计数必须为纯数字行;连接错误/杂文本一律重试,杜绝垃圾值假 PASS
function CountChecked([string]$sql) {
    for ($i = 0; $i -lt 6; $i++) {
        $rows = @(Q $sql)
        if ($rows.Count -ge 1 -and $rows[0] -match "^\d+$") { return [int]$rows[0] }
        Start-Sleep -Seconds 2
    }
    throw "count query non-numeric after retries: $($rows -join '|')"
}

function Assert([string]$name, [bool]$ok, [string]$detail = "") {
    $status = if ($ok) { "PASS" } else { "FAIL" }
    if ($ok) { $script:PASS++ } else { $script:FAIL++ }
    Write-Host "  [$status] $name $(if ($detail) {"— $detail"})" -ForegroundColor $(if ($ok) { "Green" } else { "Red" })
    [void]$script:RESULTS.Add([pscustomobject]@{ Name = $name; Status = $status; Detail = $detail })
}

function Skip([string]$name, [string]$reason) {
    $script:SKIP++
    Write-Host "  [SKIP] $name — $reason" -ForegroundColor Yellow
    [void]$script:RESULTS.Add([pscustomobject]@{ Name = $name; Status = "SKIP"; Detail = $reason })
}

function DumpResults([string]$file) {
    $script:RESULTS | ConvertTo-Json | Set-Content -Path $file -Encoding UTF8
    Write-Host "`n结果: PASS=$($script:PASS) FAIL=$($script:FAIL) SKIP=$($script:SKIP) → $file"
}

function ScreencapNonBlackRatio {
    $ErrorActionPreference = "Continue"
    $png = Join-Path $script:TMP "cap.png"
    for ($i = 0; $i -lt 5; $i++) {
        Reconnect
        & $script:ADB -s $script:DEVICE exec-out screencap -p > $png 2>$null
        if ((Test-Path $png) -and (Get-Item $png).Length -gt 1000) { break }
        Start-Sleep -Seconds 2
    }
    if (-not (Test-Path $png) -or (Get-Item $png).Length -lt 1000) { return -1 }
    Add-Type -AssemblyName System.Drawing
    try {
        $bmp = [System.Drawing.Bitmap]::FromFile($png)
        $nonBlack = 0; $total = 0
        for ($y = 0; $y -lt $bmp.Height; $y += 17) {
            for ($x = 0; $x -lt $bmp.Width; $x += 17) {
                $c = $bmp.GetPixel($x, $y)
                $total++
                if ($c.R -gt 16 -or $c.G -gt 16 -or $c.B -gt 16) { $nonBlack++ }
            }
        }
        $bmp.Dispose()
        if ($total -eq 0) { return -1 }
        return [math]::Round($nonBlack / $total, 4)
    } catch { return -1 }
}

function UiDumpContains([string]$needle) {
    AdbShell "uiautomator dump /sdcard/ui.xml" | Out-Null
    $xml = AdbShell "cat /sdcard/ui.xml"
    return ($xml -match [regex]::Escape($needle))
}

function TargetAlive {
    $p = AdbShell "pidof $script:TARGET_PKG"
    return ($p -match "\d")
}

# 等待注入侧 spool 排空(run-as 零 su 轮询;洪泛后查询前调用,防「未回放」假阴性)
function WaitSpoolDrain([int]$timeoutSec = 90) {
    $sp = "files/modev_spool/$script:TARGET_PKG/spool.dat"
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    $prev = -1; $stable = 0
    while ((Get-Date) -lt $deadline) {
        $wc = AdbShell "run-as $script:TARGET_PKG wc -l $sp"
        $n = 0
        if ($wc -match "^\s*(\d+)") { $n = [int]$Matches[1] }
        if ($n -eq 0) { return $true }
        if ($n -eq $prev) { $stable++ } else { $stable = 0 }
        if ($stable -ge 4) { return $true } # 连续五轮不变 = 排空停滞(断连保留),放行
        $prev = $n
        Start-Sleep -Seconds 4
    }
    return $false
}
