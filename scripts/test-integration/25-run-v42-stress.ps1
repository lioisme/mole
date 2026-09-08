# 25-run-v42-stress.ps1 — V42 全量压力测试战役编排器
# 单轮次顺序执行 G0–G8,全部 PASS = 战役通过;任一 FAIL 退出码 1(失败循环由维护者驱动修复后整轮重跑)。
# 沿用 tt-lib 惯例:ANDROID_ADB_SERVER_PORT=5038、单序列、[PASS]/[FAIL] 行 + JSON 落盘。
# 用法: powershell -File scripts\test-integration\25-run-v42-stress.ps1 [-SkipG7]
param(
    [switch]$SkipG7,   # 跳过 G7 注入矩阵( listening/enhance/linkage 重负载组)
    [switch]$SkipG6    # 跳过 G6 MCP 矩阵
)
$ErrorActionPreference = "Continue"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $here "tt-lib.ps1")

$script:PASS = 0; $script:FAIL = 0; $script:SKIP = 0
$script:RESULTS = New-Object System.Collections.Generic.List[object]
$repoRoot = Split-Path -Parent (Split-Path -Parent $here)
$gradle = Join-Path $repoRoot "gradlew.bat"
$roundId = Get-Date -Format "yyyyMMdd-HHmmss"

function Gate([string]$name, [bool]$ok, [string]$detail = "") {
    $status = if ($ok) { "PASS" } else { "FAIL" }
    if ($ok) { $script:PASS++ } else { $script:FAIL++ }
    Write-Host "[$status] GATE $name $(if ($detail) {"— $detail"})" -ForegroundColor $(if ($ok) { "Green" } else { "Red" })
    [void]$script:RESULTS.Add([pscustomobject]@{ Gate = $name; Status = $status; Detail = $detail })
}

function RunGradle([string]$tasks, [string[]]$filter = @()) {
    $out = & $gradle $tasks.Split(" ") 2>&1 | Out-String
    $ok = ($LASTEXITCODE -eq 0)
    return [pscustomobject]@{ Ok = $ok; Output = $out }
}

function SumUnitTestXml {
    $dir = Join-Path $repoRoot "app\build\test-results\testModernDebugUnitTest"
    if (-not (Test-Path $dir)) { return -1 }
    $total = 0; $bad = 0
    Get-ChildItem $dir -Filter "TEST-*.xml" | ForEach-Object {
        $m = Select-String -Path $_.FullName -Pattern 'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"' |
            Select-Object -First 1
        if ($m) {
            $f = [int]$m.Matches[0].Groups[3].Value
            $e = [int]$m.Matches[0].Groups[4].Value
            $total += [int]$m.Matches[0].Groups[1].Value
            $bad += $f + $e
        }
    }
    return @{ Total = $total; Bad = $bad }
}

# ================= G0 环境门 =================
Write-Host "`n=== G0 环境门 ==="
WaitDevice | Out-Null
Reconnect | Out-Null
$devOut = AdbShell "getprop ro.build.version.sdk"
Gate "G0-1 设备在线" (-not [string]::IsNullOrWhiteSpace("$devOut")) "seq=$env:DEVICE"
Gate "G0-2 设备可用性" ($LASTEXITCODE -eq 0) "sdk=$("$devOut".Trim())"
AdbShell "settings put global cached_apps_freezer disabled" | Out-Null
Gate "G0-3 冻结器禁用" ($LASTEXITCODE -eq 0)

# ================= G1 构建门 =================
Write-Host "`n=== G1 构建门 ==="
$r1 = RunGradle ":app:assembleModernDebug :app:assembleModernDebugAndroidTest :testtarget:assembleDebug"
Gate "G1 构建(主源+androidTest+靶程序)" $r1.Ok
if (-not $r1.Ok) { $r1.Output | Select-Object -Last 30 | Write-Host }

# ================= G2 安装与注入恢复门 =================
Write-Host "`n=== G2 安装与注入恢复门 ==="
$hostApk = Join-Path $repoRoot "app\build\outputs\apk\modern\debug\app-modern-x86_64-debug.apk"
$testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\modern\debug\app-modern-debug-androidTest.apk"
$ttApk   = Join-Path $repoRoot "testtarget\build\outputs\apk\debug\testtarget-debug.apk"
$okInstall = $true
foreach ($apk in @($hostApk, $testApk, $ttApk)) {
    if (-not (Test-Path $apk)) { Gate "G2-1 安装 $($apk)" $false "APK 缺失"; $okInstall = $false; continue }
    AdbShell "install -r `"$apk`"" | Out-Null
    if ($LASTEXITCODE -ne 0) { Gate "G2-1 安装 $(Split-Path -Leaf $apk)" $false; $okInstall = $false }
}
if ($okInstall) { Gate "G2-1 三包 install -r" $true }

# 整机重启(install -r 后 lspd 模块表失效,唯一恢复法;AGENTS 已知坑)
Write-Host "  整机重启以重载 lspd 模块表(约 60-120s)…"
$dnconsole = "D:\leidian\LDPlayer14\dnconsole.exe"
$dnplayer = "D:\leidian\LDPlayer14\dnplayer.exe"
if (Test-Path $dnconsole) {
    & $dnconsole quit --index 0 | Out-Null
    Start-Sleep -Seconds 8
    Start-Process -FilePath $dnplayer -ArgumentList "-index 0" -WindowStyle Hidden | Out-Null
    WaitDevice 420 | Out-Null
    Reconnect | Out-Null
    $up = AdbShell uptime
    Gate "G2-2 整机重启完成" ($LASTEXITCODE -eq 0) "uptime=$("$up".Trim())"
} else {
    Gate "G2-2 整机重启" $false "dnconsole 不存在,请手动重启 VM 后重跑"
}

# Vector 作用域恢复(11 号脚本:modules enabled + scope 行 + 注入冒烟)
Write-Host "  执行 11-vector-scope.ps1…"
$vecOut = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here "11-vector-scope.ps1") 2>&1 | Out-String
$vecOk = ($LASTEXITCODE -eq 0)
Gate "G2-3 Vector 作用域恢复+注入冒烟" $vecOk ($(if ($vecOk) { "HookInstaller 探针命中" } else { ($vecOut -split "`n" | Select-Object -Last 5) -join " | " }))

# ================= G3 宿主单测门 =================
Write-Host "`n=== G3 宿主单测门 ==="
$r3 = RunGradle ":app:testModernDebugUnitTest"
$s = SumUnitTestXml
if ($s -is [hashtable]) { Gate "G3 全量单测($($s.Total) 用例)" ($r3.Ok -and $s.Bad -eq 0) "failures+errors=$($s.Bad)" }
else { Gate "G3 全量单测" $r3.Ok "XML 汇总不可用,以 gradle 退出码为准" }

# ================= G4 冷启动冒烟门 =================
Write-Host "`n=== G4 冷启动冒烟门 ==="
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here "23-run-smoke.ps1") 2>&1 | Tee-Object -Variable smokeOut | Out-Null
$smokePass = ($smokeOut -match "\[FAIL\]").Count
Gate "G4 冷启动冒烟(全功能 ON ×5 App + L-SMOKE-01)" ($smokePass -eq 0) "FAIL 行数=$smokePass"

# ================= G5 设备插桩门 =================
Write-Host "`n=== G5 设备插桩门(单次连续执行)==="
AdbShell "am force-stop $script:MODEV_PKG" | Out-Null
Start-Sleep -Seconds 2
# am instrument -w 全量 20+ 分钟:直接调 adb 后台执行 + 输出文件轮询
# (AdbShell 的 5 次重试封装会阻塞收不到流,不经它)。
$env:ANDROID_ADB_SERVER_PORT = "5038"
$instLog = Join-Path $env:TEMP "modev-tt\v42-instrument-$roundId.log"
$adbExe = "D:\leidian\LDPlayer14\adb.exe"
$proc = Start-Process -FilePath $adbExe -ArgumentList "-s $script:DEVICE", "shell", "am instrument -w com.lioisme.modev.test/androidx.test.runner.AndroidJUnitRunner" -NoNewWindow -PassThru -RedirectStandardOutput $instLog
$deadline5 = (Get-Date).AddMinutes(45)
$done = $false
while ((Get-Date) -lt $deadline5 -and -not $proc.HasExited) {
    Start-Sleep -Seconds 15
    $tail = Get-Content $instLog -Tail 3 -ErrorAction SilentlyContinue | Out-String
    if ($tail -match "OK \(\d+ tests?\)|FAILURES!? \d+") { $done = $true; break }
}
try { if (-not $proc.HasExited) { $proc.Kill() } } catch {}
$instText = Get-Content $instLog -ErrorAction SilentlyContinue | Out-String
$mFail = [regex]::Match($instText, "FAILURES!? (\d+)")
$mOk = [regex]::Match($instText, "OK \((\d+) tests?\)")
if ($mOk.Success) { Gate "G5 全量插桩($($mOk.Groups[1].Value) 用例)" $true }
elseif ($mFail.Success) {
    Gate "G5 全量插桩" $false "FAILURES=$($mFail.Groups[1].Value)"
    $instText -split "`n" | Where-Object { $_ -match "^\s+\)|Error:" } | Select-Object -First 20 | Write-Host
} else {
    Gate "G5 全量插桩" $false "输出未含 OK/FAILURES 摘要(instrument 异常):$(($instText -split "`n" | Select-Object -Last 6) -join ' | ')"
}

# ================= G6 MCP 矩阵门 =================
# 顺序纪律:24-run-mcp 内部 install -r 宿主 APK → lspd 模块表失效 → 若在其后
# 跑注入矩阵必零捕获假失败。故注入矩阵(G7)先于 MCP 矩阵(G6)执行,androidTest
# (手动 am instrument,不卸载 APK、不依赖注入)与注入无耦合,位置不变。
if (-not $SkipG7) {
    Write-Host "`n=== G7 注入矩阵门(先于 MCP:24 号内部 install -r 会失效 lspd 缓存)==="
    foreach ($script in @("20-run-listen.ps1", "21-run-enhance.ps1", "22-run-linkage.ps1")) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here $script) 2>&1 | Tee-Object -Variable mOut | Out-Null
        $mFailCount = ($mOut -match "\[FAIL\]").Count
        Gate "G7 $script" ($mFailCount -eq 0) "FAIL 行数=$mFailCount"
    }
} else { Gate "G7 注入矩阵" $true "SKIP(-SkipG7)" }

if (-not $SkipG6) {
    Write-Host "`n=== G6 MCP 矩阵门 ==="
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here "24-run-mcp.ps1") 2>&1 | Tee-Object -Variable mcpOut | Out-Null
    $mcpFail = ($mcpOut -match "\[FAIL\]").Count
    Gate "G6 MCP 矩阵(22 断言)" ($mcpFail -eq 0) "FAIL 行数=$mcpFail"
} else { Gate "G6 MCP 矩阵" $true "SKIP(-SkipG6)" }

# ================= G8 汇总裁决门 =================
Write-Host "`n=== G8 汇总裁决 ==="
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here "99-report.ps1") 2>&1 | Out-Null
$failedGates = @($script:RESULTS | Where-Object { $_.Status -eq "FAIL" })
Gate "G8 单轮次全门裁决" ($failedGates.Count -eq 0) $(if ($failedGates.Count) { ($failedGates | ForEach-Object { $_.Gate }) -join ", " } else { "G0–G8 全 PASS" })

# JSON 落盘 + 摘要
$jsonPath = Join-Path $env:TEMP "modev-tt\v42-stress-$roundId.json"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $jsonPath) | Out-Null
$script:RESULTS | ConvertTo-Json | Set-Content -Path $jsonPath -Encoding UTF8
Write-Host "`n=== V42 压测战役(Round $roundId): PASS=$($script:PASS) FAIL=$($script:FAIL) — $jsonPath ==="
if ($script:FAIL -gt 0) { Write-Host "进入失败循环:按 TEST-V42-STRESS.md §3 协议审计修复后整轮重跑" -ForegroundColor Yellow; exit 1 }
Write-Host "一次性测试结果全量符合预期 ✓" -ForegroundColor Green
exit 0
