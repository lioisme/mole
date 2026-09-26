# tt-lib.ps1 — Modev × ModevTestTarget 集成测试共享库
# 用法: . "$PSScriptRoot\tt-lib.ps1"

# 环境参数化唯一入口(V45 T0.5):adb 路径 / 设备序列 / 独立 server 端口 / 包名 /
# 导出目录一律来自环境变量或 local.properties,缺省回落既有默认值。
. "$PSScriptRoot\tt-env.ps1"

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
    $lastErr = ""
    while ((Get-Date) -lt $deadline) {
        Reconnect
        $st = (& $script:ADB -s $script:DEVICE shell "getprop sys.boot_completed" 2>&1 | ForEach-Object { "$_" }) -join ""
        if ("$st".Trim() -eq "1") { return $true }
        if ("$st" -match "not found|offline|closed|refused") { $miss++; $lastErr = "$st" } else { $miss = 0 }
        # V50 实锤:阈值 8(≈40s)太低 —— 宿主满载时 `adb shell` 单次就能卡过这个窗口,
        # 而本分支会 **Stop-Process -Force 掉 dnplayer 再冷启动**,等于量具自己把一台
        # 活着的 VM 打死,再把后半程所有零产出错记成产品缺陷(R8 一轮 8 次即此)。
        # 现在:窗口拉到 24 次(≈2min 持续 adb 级报错,而非单次慢),并把真实报错
        # 打进日志,掉线才有可归因的证据。
        if ($miss -ge 24) {
            # V50 实锤:报错形态是 `adb.exe: device '...' not found`,而这**同样**是
            # 雷电 TCP 桥掉了而 Android 照常活着的特征 —— 旧实装不分辨,直接杀 dnplayer,
            # 于是「自愈」本身毁掉了靶进程/spool/lspd 现场,后半程必然全零。
            # 一级恢复只动 adb 桥(不碰 VM,现场无损);只有桥救不回来才认定 VM 真死。
            Write-Host "  [VM] $miss 次连续 adb 报错(末次: $($lastErr.Trim()))⇒ 一级恢复:重启 adb 桥(不动 VM)" -ForegroundColor Yellow
            & $script:ADB kill-server 2>&1 | Out-Null
            $bridgeOk = $false
            # V50 铁证(`LDPlayer14/crash_copyx86/leidian0_vbox.log`):
            #   vbox::VBoxClientImpl::_onCallPropNotify(1908): app crash
            #   vbox::VBoxClientImpl::autoAdbReconnect(1691): adb auto reconnect
            #   remove natnetwork port forward tcp_5555_0 / add natnetwork port forward tcp_5555_0
            # ⇒ Guest 内**某个 App 崩溃**(压力矩阵里天天发生)就会触发 LDPlayer 自己
            #   重挂 5555 端口转发,期间 adb 侧序列凭空消失 = `device '...' not found`,
            #   **而 VM 活得好好的**。旧自愈把这当成 VM 死亡并 Stop-Process,等于把
            #   R3/R4/R5 三轮「掉线」的根因从环境故障升级成量具误杀健康 VM。
            # 一级恢复窗口因此给到 300s(要覆盖 guest 框架重启 + 端口重挂 + boot_completed 回 1)。
            $bDeadline = (Get-Date).AddSeconds(300)
            while ((Get-Date) -lt $bDeadline) {
                Reconnect
                $b = (& $script:ADB -s $script:DEVICE shell "getprop sys.boot_completed" 2>&1 | ForEach-Object { "$_" }) -join ""
                if ("$b".Trim() -eq "1") { $bridgeOk = $true; break }
                Start-Sleep -Seconds 4
            }
            if ($bridgeOk) {
                Write-Host "  [VM] adb 桥恢复,VM 未受扰 ⇒ 现场保留,继续本步" -ForegroundColor Green
                $miss = 0
                $deadline = (Get-Date).AddSeconds($timeoutSec)
                continue
            }
            Write-Host "  [VM] 桥重启无效 ⇒ 判定 VM 真死,二级恢复:冷启动 dnplayer(本步现场已毁)" -ForegroundColor Red
            # V50 实锤:掉线自愈过去**只打一行黄字**,编排器毫不知情,于是继续用被重启
            # 过的设备跑完剩余断言 —— 靶进程已没了、注入未必恢复(lspd 缓存模块表),
            # 后半程计数恒 0,一轮里凭空造出 21 条「功能失败」(真因 = 1 次掉线)。
            # 现在把这件事写成标记文件交给编排器裁决:本步作废 + 整轮 BLOCKED,
            # 而不是把环境故障登记成产品缺陷。
            if ($env:TT_VM_LOST_MARK) {
                try {
                    [IO.File]::AppendAllText(
                        $env:TT_VM_LOST_MARK,
                        ("{0}`t{1}`n" -f (Get-Date).ToString("o"), $MyInvocation.ScriptName),
                        (New-Object System.Text.UTF8Encoding($false)))
                } catch { }
            }
            Get-Process dnplayer,Ld9BoxHeadless,VBoxNetDHCP -ErrorAction SilentlyContinue | Stop-Process -Force
            Start-Sleep -Seconds 6
            Start-Process -FilePath $script:DNPLAYER -ArgumentList "-index","0" -WorkingDirectory $script:LDPLAYER_DIR
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
    # V50 实锤(R6 普查抓到):设备掉线瞬间 `Q` 把 **adb 报错文本当数据行**返回
    # (`adb.exe: device '...' not found`),旧循环因 `$p[2]` 不是数字而**静默跳过** ⇒
    # 整表读成「零记录」⇒ db 五类恒 `count=0` 被记成产品缺陷,真因却是设备不可达。
    # 这正是 R3 红 / R10 绿 / R6 又红的全部成因 —— 与产品代码无关的 flaky 假故障。
    # 现在:报错行不再被吞,记账成显式状态供断言侧点名。
    $script:LAST_QUERY_ERROR = ""
    foreach ($r in $rows) {
        if ($r -match "device .*not found|device offline|error:|closed|refused|cannot connect") {
            $script:LAST_QUERY_ERROR = "$r"
            continue
        }
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
    # spool 排空 ≠ 入库完成:TCP 在途记录经宿主异步批写,清库前留落库窗口,
    # 否则上一轮在途行落在 ClearDb 之后,污染下一轮 NEG 零断言(V38 实锤)。
    Start-Sleep -Seconds 3
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
        Reconnect   # 同上:掉线时不重新 connect,轮询永远拿不到 TT_RESULT(空值 ⇒ 下游判成"未生效")
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
        # TCP 序列掉线时,单纯重跑 adb 命令永远拿回设备("device '127.0.0.1:5555' not found"
        # 会耗尽全部重试后抛异常,把整阶段打成零断言 —— V49 验收轮 enhance 步两轮实测形态)。
        # 重试前必须重新 connect;WaitDevice 的自愈只在建轮时跑一次,救不了中途掉线。
        Reconnect
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

# V45 T0.4:矩阵脚本必须以 FAIL 数决定退出码(旧 20/21/22/23 恒退出 0 = 编排无法短路根因)
function Exit-WithResult {
    if ($script:FAIL -gt 0) {
        Write-Host "=== matrix result: $($script:PASS) PASS / $($script:FAIL) FAIL / $($script:SKIP) SKIP ===" -ForegroundColor Red
        exit 1
    }
    Write-Host "=== matrix result: $($script:PASS) PASS / 0 FAIL / $($script:SKIP) SKIP ===" -ForegroundColor Green
    exit 0
}

function ScreencapNonBlackRatio {
    $ErrorActionPreference = "Continue"
    $png = Join-Path $script:TMP "cap.png"
    Remove-Item $png -Force -ErrorAction SilentlyContinue
    # V50 量具修复:原实装 `adb exec-out screencap -p > cap.png` 在 PowerShell 5.1 下
    # **按文本重编码原生 stdout**(换行/字符集转换),PNG 被毁 ⇒ FromFile 抛 ⇒ 恒返回 -1,
    # 于是 E-SEC-01/02 三条「FLAG_SECURE 解除」断言在每一轮都是假红(V49 台账里那 14 条
    # 长期无人修复的红项正是这一族)。改为「设备侧落图 + `adb pull`」:pull 由 adb
    # 自己写字节文件,二进制安全。
    for ($i = 0; $i -lt 5; $i++) {
        Reconnect
        & $script:ADB -s $script:DEVICE shell "screencap -p /sdcard/modev_cap.png" 2>$null | Out-Null
        & $script:ADB -s $script:DEVICE pull /sdcard/modev_cap.png $png 2>$null | Out-Null
        if ((Test-Path $png) -and (Get-Item $png).Length -gt 1000) { break }
        Start-Sleep -Seconds 2
    }
    if (-not (Test-Path $png) -or (Get-Item $png).Length -lt 1000) {
        Write-Host "    [cap] 取图失败(文件缺失或 <1000B)" -ForegroundColor DarkGray
        return -1
    }
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
    } catch {
        # 失败原因必须可见 —— 静默 -1 让「量具坏了」和「功能坏了」长得一模一样
        Write-Host "    [cap] 解码失败: $($_.Exception.Message)" -ForegroundColor DarkGray
        return -1
    }
}

# UI 树里找子串。**必须走设备侧 grep**:PowerShell 5.1 解码 adb 文本输出时非 ASCII 全成 `?`
# (V50 实锤:`-match "录屏"` 恒假 ⇒ E-MP-01 的「OFF 出弹窗」永远红,而「ON 弹窗消失」
# 因为检测不到而**真空假绿** —— 一个假红配一个假绿,比两个假红更坏)。
# 把 needle 以 UTF-8 文件推到设备上 `grep -f`,回传的是命中行数(纯 ASCII),编码不再经过 PS。
function UiDumpContains([string]$needle) {
    $ErrorActionPreference = "Continue"
    AdbShell "uiautomator dump /sdcard/ui.xml" | Out-Null
    $local = Join-Path $script:TMP "needle.txt"
    [IO.File]::WriteAllText($local, $needle, (New-Object System.Text.UTF8Encoding($false)))
    Reconnect
    & $script:ADB -s $script:DEVICE push $local /sdcard/modev_needle.txt 2>&1 | Out-Null
    $c = AdbShell "grep -c -f /sdcard/modev_needle.txt /sdcard/ui.xml"
    if ("$c" -match "^\s*(\d+)") { return ([int]$Matches[1]) -gt 0 }
    return $false
}

function TargetAlive {
    $p = AdbShell "pidof $script:TARGET_PKG"
    return ($p -match "\d")
}

# 等待注入侧 spool 排空(run-as 零 su 轮询;洪泛后查询前调用,防「未回放」假阴性)
# V38:宿主进程无前台服务可被后台回收 → 排空前置 EnsureHost(回收后靶端 ≤30s 重连回放)
function WaitSpoolDrain([int]$timeoutSec = 90) {
    EnsureHost
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
