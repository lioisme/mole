# run-acceptance.ps1 — V45 一次性全量验收编排(TASK-V45-ACCEPTANCE.md §8.1 / BL-4 根因治理)
#
# 用法:
#   powershell -File scripts/test-integration/run-acceptance.ps1                 # 全量单轮
#   powershell -File scripts/test-integration/run-acceptance.ps1 -Stage preflight # 单阶段(诊断用)
#   powershell -File ... -Reboot                                                  # 安装后整机重启重载 lspd
#
# 纪律(V45 Z1..Z8 落地):
# - 阶段门禁步(preflight/build/install/scope)失败 = 整轮 BLOCKED 立即中止(后续全是空转);
# - 矩阵步退出码由 FAIL 数决定(tt-lib Exit-WithResult);Gradle 步以 BUILD SUCCESSFUL 行判定,
#   绝不信管道退出码(Z2);
# - 插桩轮前默认 server 必须只有 1 条序列(Z4);脚本轮用独立 5038 server + TCP 序列;
# - **阶段序 = preflight → build/unit/lint/assemble → install(+可选 -Reboot 重载 lspd)
#   → scope → inject → ui → mcp**:注入矩阵必须在插桩轮之前(AGENTS:androidTest 结束会
#   卸载宿主 APK 并复位 Vector 模块表);MCP 排最后且 -SkipBuild(其 install -r 同样失效 lspd);
# - 产物:docs/test-reports/v45-<utc>.json(逐断言台账)+ 同名 .md(人读)。

param(
    [ValidateSet("all", "preflight", "build", "install", "scope", "ui", "inject", "mcp", "report")]
    [string]$Stage = "all",
    [switch]$SkipUi,
    [switch]$SkipInject,
    [switch]$SkipMcp,
    [switch]$Reboot,
    [switch]$NoReset
)

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\tt-env.ps1"

# §4.3「SKIP 预登记豁免表」的机械执行面(T4.4):表外 SKIP 一律按 FAIL 计入。
# 只数 SKIP 不点名 = 给 assumeTrue 留假绿通道(一整批断言可以被静默吞掉仍报 PASS)。
$script:SKIP_ALLOW = @()
$allowPath = Join-Path $PSScriptRoot "skip-allowlist.txt"
if (Test-Path $allowPath) {
    $script:SKIP_ALLOW = @(
        Get-Content $allowPath -Encoding UTF8 | ForEach-Object { ($_ -replace "#.*$", "").Trim() } |
            Where-Object { $_ }
    )
}

$repo = $script:REPO_ROOT
$utc = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
$logDir = Join-Path $script:REPORT_DIR "v45-$utc"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$script:STEPS = New-Object System.Collections.ArrayList
$script:BLOCKED = $false
$script:FINGERPRINT = $null

function Step-Header([string]$id) {
    Write-Host "`n=== [$id] ===" -ForegroundColor Cyan
}

function Add-Step {
    param([string]$Id, [string]$Verdict, [int]$ExitCode, [string]$Log, [string]$Note = "")
    [void]$script:STEPS.Add([pscustomobject]@{
            Id = $Id; Verdict = $Verdict; ExitCode = $ExitCode; Log = $Log; Note = $Note
            Pass = 0; Fail = 0; Skip = 0; SkipIds = @()
        })
}

function Last-Step { $script:STEPS[$script:STEPS.Count - 1] }

# 读矩阵脚本 DumpResults 落盘的逐断言 JSON(权威台账,不靠控制台正则)
function Import-Round {
    param([string]$Path, [datetime]$Since = [datetime]::MinValue)
    if (-not (Test-Path $Path)) { return $false }
    # 台账必须是**本轮该步写出的**:早于本步启动时间的文件 = 上一轮/另一步的残留,
    # 直接不认(否则「跑失败的步」会被旧台账判绿)。
    if ((Get-Item $Path).LastWriteTime -lt $Since) { return $false }
    # PS 5.1 坑:`Get-Content -Raw | ConvertFrom-Json` 把整个数组当**一个**管道对象
    # 输出,`@(...)` 于是得到「1 个元素 = 数组本身」,`.Status` 变成
    # "PASS PASS PASS…" 全不匹配 ⇒ 所有矩阵步计数恒 0(V45 首轮实锤)。
    # 必须先解除嵌套(ForEach-Object 枚举)再计数。
    $parsed = Get-Content $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    $rows = @($parsed | ForEach-Object { $_ })
    if ($rows.Count -eq 0) { return $false }
    $st = Last-Step
    foreach ($r in $rows) {
        switch ("$($r.Status)") {
            "PASS" { $st.Pass++ }
            "FAIL" { $st.Fail++ }
            # SKIP 必须留名:表外 SKIP = FAIL(§4.3),只计数字等于给「assumeTrue 静默吞掉
            # 一整批断言」留了假绿通道(T4.4)。
            "SKIP" {
                $st.Skip++
                # 矩阵台账的断言键名是 Name(Assert/SkipResult 落盘字段),Id 只存在于
                # 少数脚本形态 —— 两者都认,取不到名字就把整行对象写进台账(不静默丢)。
                $sid = "$($r.Name)"
                if (-not $sid) { $sid = "$($r.Id)" }
                if (-not $sid) { $sid = "(无名 SKIP:$($r | ConvertTo-Json -Compress))" }
                $st.SkipIds += $sid
            }
            default { $st.Note = "台账含未知 Status(计数将不覆盖它):$($r.Id)" }
        }
    }
    return $true
}

function Run-Child {
    # 参数名禁撞 PS 自动变量:本参数曾名 $Args(= 自动 $args,大小写不敏感),
    # 传 -Args @(...) 时不绑定 ⇒ 子脚本按默认档运行(V45 实锤:preflight 误跑全档
    # 导致整轮 BLOCKED、MCP 步的 -SkipBuild 同样被吞)。AGENTS「自动变量」条目再实证。
    param([string]$Id, [string]$File, [string]$RoundJson, [string[]]$ChildArgs = @(), [bool]$Gate = $false, [int]$TimeoutMin = 60)
    if ($script:BLOCKED) { Add-Step -Id $Id -Verdict "BLOCKED" -ExitCode -1 -Log ""; return }
    Step-Header $Id
    $log = Join-Path $logDir "$Id.log"
    $errLog = Join-Path $logDir "$Id.err.log"
    $path = Join-Path $PSScriptRoot $File
    Add-Step -Id $Id -Verdict "RUN" -ExitCode -1 -Log $log
    $st = Last-Step
    $started = Get-Date
    # 子进程必须走**文件重定向**,不能用 `& cmd | Tee`:子脚本会拉起 gradle/adb 守护进程,
    # 它们继承 stdout 句柄 ⇒ 管道永不 EOF ⇒ 编排器在子进程退出后仍挂死(V45 首轮实锤)。
    $psArgs = (@("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$path`"") + $ChildArgs) -join " "
    $proc = Start-Process -FilePath "powershell.exe" -ArgumentList $psArgs `
        -RedirectStandardOutput $log -RedirectStandardError $errLog -NoNewWindow -PassThru
    # PS 5.1 实锤:不加 EnableRaisingEvents 时,`WaitForExit(ms)` 轮询后 `.ExitCode`
    # 恒为空 ⇒ 门禁步被误判为非 0 退出(整轮假红)。必须先开事件再等。
    $proc.EnableRaisingEvents = $true
    $deadline = $started.AddMinutes($TimeoutMin)
    $timedOut = $false
    while (-not $proc.WaitForExit(5000)) {
        if ((Get-Date) -gt $deadline) {
            $timedOut = $true
            try { $proc.Kill($true) } catch { try { $proc.Kill() } catch { } }
            break
        }
    }
    try { $proc.WaitForExit() } catch { }
    # 读不到退出码 = 不可判定,按硬失败处理(绝不当 0 通过)
    $code = $proc.ExitCode
    if ($null -eq $code) { $code = if ($timedOut) { -2 } else { -3 } }
    $st.ExitCode = $code
    if ($timedOut) { $st.Note = "超时 ${TimeoutMin}min,已强杀子进程" }
    $imported = $false
    if ($RoundJson) { $imported = Import-Round -Path (Join-Path $script:TMP $RoundJson) -Since $started }
    if ($st.Fail -gt 0) { $st.Verdict = "FAIL" }
    elseif ($timedOut) { $st.Verdict = "FAIL" }
    elseif ($st.ExitCode -ne 0) { $st.Verdict = "FAIL" }
    elseif ($st.Pass -eq 0 -and -not $imported) { $st.Verdict = "FAIL"; $st.Note = "无逐断言台账且退出码 0:不可判定 = 按 FAIL 处理(防假绿)" }
    else { $st.Verdict = "PASS" }
    if ($st.Verdict -eq "FAIL" -and $Gate) {
        $script:BLOCKED = $true
        Write-Host "  [BLOCKED] 门禁步 $Id 失败,后续阶段全部记 BLOCKED" -ForegroundColor Red
    }
    Write-Host ("  {0} exit={1} PASS={2} FAIL={3} SKIP={4} ({5:N0}s) log={6}" -f `
            $st.Verdict, $st.ExitCode, $st.Pass, $st.Fail, $st.Skip, ((Get-Date) - $started).TotalSeconds, (Split-Path $log -Leaf)) -ForegroundColor $(if ($st.Verdict -eq "PASS") { "Green" } else { "Red" })
}

# Gradle 步:以 BUILD SUCCESSFUL 行判定(Z2,禁信管道退出码)
function Run-Gradle {
    param([string]$Id, [string[]]$Tasks, [bool]$Gate = $false, [int]$TimeoutMin = 45)
    if ($script:BLOCKED) { Add-Step -Id $Id -Verdict "BLOCKED" -ExitCode -1 -Log ""; return }
    Step-Header $Id
    $log = Join-Path $logDir "$Id.log"
    $errLog = Join-Path $logDir "$Id.err.log"
    Add-Step -Id $Id -Verdict "RUN" -ExitCode -1 -Log $log
    $st = Last-Step
    $started = Get-Date
    # 参数名禁撞 PS 自动变量 $args(AGENTS 条目;此处仅需一次改名)
    # 验收轮必须「从源码可复现」:本地构建缓存曾把旧的 androidTest dex 当 up-to-date 交回
    # (同一轮宿主 APK 是新产物),插桩运行期才炸 NoSuchMethodError —— 前端-3/4 两轮
    # DataSettingsScreenTest 即此因,`--rerun-tasks --no-build-cache` 重编后消失(DEF-V45-012)。
    $gradleArgs = (@($Tasks) + @("--console=plain", "--no-daemon", "--max-workers=1", "--no-build-cache", "-Dorg.gradle.jvmargs=-Xmx2048m")) -join " "
    # 与 Run-Child 同构走**文件重定向**:`& gradlew | Tee` 下 gradle 拉起的构建/Kotlin
    # 守护进程继承 stdout 句柄 ⇒ 管道无 EOF ⇒ 编排器在构建已结束后永久挂死
    # (AGENTS「验收编排器三坑」①,本轮 -Stage ui 实测复现)。
    $bat = Join-Path $repo "gradlew.bat"
    $proc = Start-Process -FilePath "$env:ComSpec" -ArgumentList "/c `"$bat`" $gradleArgs" `
        -WorkingDirectory $repo -RedirectStandardOutput $log -RedirectStandardError $errLog -NoNewWindow -PassThru
    $proc.EnableRaisingEvents = $true
    $deadline = $started.AddMinutes($TimeoutMin)
    $timedOut = $false
    while (-not $proc.WaitForExit(5000)) {
        if ((Get-Date) -gt $deadline) {
            $timedOut = $true
            try { $proc.Kill($true) } catch { try { $proc.Kill() } catch { } }
            break
        }
    }
    try { $proc.WaitForExit() } catch { }
    $code = $proc.ExitCode
    if ($null -eq $code) { $code = if ($timedOut) { -2 } else { -3 } }
    $st.ExitCode = $code
    if ($timedOut) { $st.Note = "超时 ${TimeoutMin}min,已强杀构建进程" }
    $txt = if (Test-Path $log) { Get-Content $log -Raw } else { "" }
    $ok = ("$txt" -match "BUILD SUCCESSFUL")
    if ("$txt" -match "BUILD FAILED") { $ok = $false }
    if ($ok -and $st.ExitCode -ne 0) { $ok = $false; $st.Note = "BUILD SUCCESSFUL 但退出码非 0" }
    $st.Verdict = $(if ($ok) { "PASS" } else { "FAIL" })
    # 插桩步额外从 XML 结果统计逐用例数(权威)
    if ($Id -eq "ui") {
        $xmls = @(Get-ChildItem (Join-Path $repo "app\build\outputs\androidTest-results") -Recurse -Filter "TEST-*.xml" -ErrorAction SilentlyContinue)
        foreach ($x in $xmls) {
            [xml]$doc = Get-Content $x.FullName -Raw
            $su = $doc.testsuite
            if ($su) {
                $st.Pass += [int]$su.tests; $st.Fail += [int]$su.failures + [int]$su.errors; $st.Skip += [int]$su.skipped
                foreach ($tc in $su.SelectNodes("testcase")) {
                    if ($tc.SelectSingleNode("skipped")) { $st.SkipIds += "$($tc.classname)#$($tc.name)" }
                }
            }
        }
        $st.Note = ("androidTest XML={0}" -f $xmls.Count)
    }
    if ($st.Verdict -eq "FAIL" -and $Gate) {
        $script:BLOCKED = $true
        Write-Host "  [BLOCKED] 门禁步 $Id 失败,后续阶段全部记 BLOCKED" -ForegroundColor Red
    }
    Write-Host ("  {0} exit={1}" -f $st.Verdict, $st.ExitCode) -ForegroundColor $(if ($st.Verdict -eq "PASS") { "Green" } else { "Red" })
}

function Set-SingleSequence {
    param([bool]$ForInstrument)
    $pt = Join-Path $script:PLATFORM_TOOLS "adb.exe"
    if (-not (Test-Path $pt)) { $pt = "adb" }
    if ($ForInstrument) {
        # tt-env.ps1 在 dot-source 时就把 ANDROID_ADB_SERVER_PORT 设成了独立 server
        # (5038),而 **gradle / AGP 继承同一环境变量** ⇒ connectedAndroidTest 会对
        # 「127.0.0.1:5555 + emulator-5554」两条序列各跑一遍同一台虚拟机(互相抢前台、
        # 焦点、触摸注入与 prefs)= 大面积假失败(V45 前端轮实锤根因之一)。
        # 插桩档必须回到默认 server,且只看 emulator-5554 一条序列。
        Remove-Item Env:ANDROID_ADB_SERVER_PORT -ErrorAction SilentlyContinue
        & $pt disconnect $script:DEVICE 2>&1 | Out-Null
        $devs = @((& $pt devices | Select-String -Pattern "device$" | ForEach-Object { ($_ -split "\s+")[0] }))
        Write-Host "  [Z4] instrument 序列面:$($devs -join ',')(应恰 1 条)" -ForegroundColor Gray
        $script:Z4_VIOLATION = ($devs.Count -ne 1)
    } else {
        $env:ANDROID_ADB_SERVER_PORT = "$($script:ADB_PORT)"
        & $script:ADB connect $script:DEVICE 2>&1 | Out-Null
        Write-Host "  [Z4] 脚本序列面:$script:DEVICE @ server $($script:ADB_PORT)" -ForegroundColor Gray
        $script:Z4_VIOLATION = $false
    }
}

function Reset-TmpState {
    if ($NoReset) { Write-Host "  [reset] -NoReset 跳过(下轮可能受残留影响)" -ForegroundColor Yellow; return }
    Step-Header "reset"
    & $script:ADB -s $script:DEVICE shell "rm -f /data/local/tmp/modev_config_*.json /data/local/tmp/bh1511.sh" 2>&1 | Out-Null
    & $script:ADB -s $script:DEVICE shell "settings delete global http_proxy" 2>&1 | Out-Null
    & $script:ADB -s $script:DEVICE shell "settings delete global global_http_proxy_host" 2>&1 | Out-Null
    & $script:ADB -s $script:DEVICE shell "settings delete global global_http_proxy_port" 2>&1 | Out-Null
    & $script:ADB -s $script:DEVICE shell "settings put global cached_apps_freezer enabled" 2>&1 | Out-Null
    Write-Host "  [reset] 磁盘兜底配置/黑洞脚本/全局代理/冻结器已复位" -ForegroundColor Gray
}

Set-SingleSequence -ForInstrument $false
$script:FINGERPRINT = Get-ModevFingerprint
Write-Host ("[env] fingerprint: " + ($script:FINGERPRINT | ConvertTo-Json -Compress)) -ForegroundColor Gray

# 门禁档 = 静态环境(装包/根/框架/伴生 App);运行态断言(进程/spool/已入库)
# 在 install+scope 接线之前必然为假,故归 inject 阶段入口(V45 T0.4/BL-4)。
if ($Stage -in @("all", "preflight")) {
    Run-Child -Id "preflight" -File "00-env-verify.ps1" -ChildArgs @("-Mode", "env") -RoundJson "env-round.json" -Gate $true
}
if ($Stage -in @("all", "build")) {
    Run-Gradle -Id "build" -Tasks @(":app:assembleModernDebug", ":testtarget:assembleDebug") -Gate $true
    Run-Gradle -Id "unit" -Tasks @(":app:testModernDebugUnitTest") -Gate $true
    Run-Gradle -Id "lint" -Tasks @(":app:lintModernRelease")
    Run-Gradle -Id "assembleRelease" -Tasks @(":app:assembleModernRelease")
}
if ($Stage -in @("all", "install")) {
    Run-Child -Id "install" -File "10-build-install.ps1" -RoundJson "install-round.json" -TimeoutMin 30 -Gate $true
    if ($Reboot) {
        Step-Header "reboot"
        # 覆盖安装后 lspd 不再向新进程注入(AGENTS 实锤),唯一恢复 = 整机冷重启;
        # 重启后必须等 boot_completed,否则后续 scope/inject 对着开机中的设备全红。
        . "$PSScriptRoot\tt-lib.ps1"
        if (Test-Path $script:DNCONSOLE) {
            & $script:DNCONSOLE quit --index 0 | Out-Null
            Start-Sleep -Seconds 8
            Start-Process -FilePath $script:DNPLAYER -ArgumentList "-index", "0" -WorkingDirectory $script:LDPLAYER_DIR | Out-Null
            Write-Host "  [reboot] dnplayer 已冷启动,等待 boot_completed…" -ForegroundColor Gray
            if (WaitDevice -timeoutSec 480) {
                Write-Host "  [reboot] 设备已就绪" -ForegroundColor Green
            } else {
                $script:BLOCKED = $true
                Write-Host "  [reboot] 等待超时 = 整轮 BLOCKED" -ForegroundColor Red
            }
        } else {
            $script:BLOCKED = $true
            Write-Host "  [reboot] dnconsole 缺失:$($script:DNCONSOLE) = 整轮 BLOCKED" -ForegroundColor Red
        }
    }
}
# 注入矩阵在本轮被跳过时,作用域步也只验「作用域行」(注入生效验证需整机重启,
# 不该把纯前端轮锁死;见 11-vector-scope.ps1 的 -VerifyInjection)。
$scopeArgs = if ($SkipInject) { @("-SkipInjectionVerify") } else { @() }
if ($Stage -in @("all", "scope")) {
    Run-Child -Id "scope" -File "11-vector-scope.ps1" -ChildArgs $scopeArgs -RoundJson "scope-round.json" -TimeoutMin 20 -Gate $true
}
if ($Stage -in @("all", "inject")) {
    if ($SkipInject) { Add-Step -Id "inject" -Verdict "SKIPPED" -ExitCode 0 -Log "" }
    else {
        # 运行态前置(进程存活 / spool / 已有记录):装包与作用域接线之后才有意义
        Run-Child -Id "env-runtime" -File "00-env-verify.ps1" -ChildArgs @("-Mode", "runtime") -RoundJson "env-round.json"
        # 顺序纪律:冒烟 → 监听 → 增强 → 联动 → 负向 → 背压(MCP 必须最后,见头注)
        Run-Child -Id "smoke" -File "23-run-smoke.ps1" -RoundJson "smoke-round.json" -TimeoutMin 40
        Run-Child -Id "listen" -File "20-run-listen.ps1" -RoundJson "listen-round.json" -TimeoutMin 60
        Run-Child -Id "enhance" -File "21-run-enhance.ps1" -RoundJson "enhance-round.json" -TimeoutMin 60
        Run-Child -Id "linkage" -File "22-run-linkage.ps1" -RoundJson "linkage-round.json" -TimeoutMin 40
        Run-Child -Id "module-off" -File "26-run-module-off-negative.ps1" -RoundJson "module-off-negative.json" -TimeoutMin 20
        Run-Child -Id "backpressure" -File "27-run-backpressure.ps1" -RoundJson "backpressure.json" -TimeoutMin 25
    }
}
# 插桩轮必须排在注入矩阵之后(AGENTS 实锤坑:connectedAndroidTest 结束后 AGP 卸载
# 被测 APK → Vector 模块行被删 + enabled 复位,后续注入矩阵全 FAIL 且无崩溃日志)。
if ($Stage -in @("all", "ui")) {
    if ($SkipUi) { Add-Step -Id "ui" -Verdict "SKIPPED" -ExitCode 0 -Log "" }
    else {
        Set-SingleSequence -ForInstrument $true
        if ($script:Z4_VIOLATION) {
            # Z4 不成立时跑出来的整轮数据全是踩踏假失败,跑它只会污染台账(V45 前端-2 的 23 条
            # 失败里有 7 条 "Failed to inject touch input" 即此因),故直接阻塞并给出修复指令。
            Add-Step -Id "ui" -Verdict "FAIL" -ExitCode -1 -Log "" -Note "Z4 不成立:instrument 序列面非恰 1 条,整轮会互相踩踏"
            $script:BLOCKED = $true
            Write-Host "  [BLOCKED] Z4:先断开多余 ADB 序列再跑插桩轮" -ForegroundColor Red
        } else {
            # 插桩轮起点必须确定:上一轮/注入侧残留会让「按种子数断言读回」与
            # 「按资源文案定位节点」两类用例集体错位(V45 首轮实锤 6→4006)。
            Run-Child -Id "reset-before-ui" -File "13-reset-device-state.ps1" -RoundJson "reset-round.json" -TimeoutMin 10
            Run-Gradle -Id "ui" -Tasks @(":app:connectedModernDebugAndroidTest") -TimeoutMin 60
        }
        Set-SingleSequence -ForInstrument $false
    }
}
if ($Stage -in @("all", "mcp")) {
    if ($SkipMcp) { Add-Step -Id "mcp" -Verdict "SKIPPED" -ExitCode 0 -Log "" }
    else { Run-Child -Id "mcp" -File "24-run-mcp.ps1" -RoundJson "mcp-round.json" -ChildArgs @("-SkipBuild") -TimeoutMin 45 }
}
Reset-TmpState

$totP = 0; $totF = 0; $totS = 0
foreach ($s in $script:STEPS) { $totP += $s.Pass; $totF += $s.Fail; $totS += $s.Skip }
# 表外 SKIP 点名计红(T4.4):豁免表 = skip-allowlist.txt(与 ACCEPTANCE §4.3 同源)
$allSkips = @($script:STEPS | ForEach-Object { $_.SkipIds } | Where-Object { "$_" })
$script:SKIP_OFFENDERS = @($allSkips | Where-Object { $script:SKIP_ALLOW -notcontains $_ })
if ($script:SKIP_OFFENDERS.Count -gt 0) {
    $totF += $script:SKIP_OFFENDERS.Count
    Write-Host ("  [SKIP 表外] {0} 条未登记豁免,按 FAIL 计入:{1}" -f `
            $script:SKIP_OFFENDERS.Count, ($script:SKIP_OFFENDERS -join ", ")) -ForegroundColor Red
}
$verdict = if ($script:BLOCKED -or $totF -gt 0) { "FAIL" } elseif ($totP -eq 0) { "NO-DATA" } else { "PASS" }
$summary = [pscustomobject]@{
    run        = "v45-$utc"
    verdict    = $verdict
    blocked    = $script:BLOCKED
    stage      = $Stage
    fingerprint = $script:FINGERPRINT
    totals     = [pscustomobject]@{ pass = $totP; fail = $totF; skip = $totS; steps = $script:STEPS.Count }
    skipIds    = @($allSkips)
    skipOffenders = @($script:SKIP_OFFENDERS)
    steps      = @($script:STEPS | ForEach-Object { $_ | Select-Object Id, Verdict, ExitCode, Pass, Fail, Skip, Note })
}
$jsonPath = Join-Path $script:REPORT_DIR "v45-$utc.json"
$mdPath = Join-Path $script:REPORT_DIR "v45-$utc.md"
$summary | ConvertTo-Json -Depth 6 | Set-Content -Path $jsonPath -Encoding UTF8
$md = @(
    "# V45 全量验收轮 v45-$utc",
    "",
    "判定:**$verdict**(PASS=$totP / FAIL=$totF / SKIP=$totS,blocked=$($script:BLOCKED),stage=$Stage)",
    "",
    "| 阶段 | 判定 | 退出码 | PASS | FAIL | SKIP | 备注 |",
    "|---|---|---|---|---|---|---|"
)
foreach ($s in $script:STEPS) { $md += "| $($s.Id) | $($s.Verdict) | $($s.ExitCode) | $($s.Pass) | $($s.Fail) | $($s.Skip) | $($s.Note) |" }
# SKIP 台账(T4.4):每条 SKIP 逐名可复核,表外者点名计红 —— 「SKIP=1」这种匿名数字不再够用。
$md += @("", "## SKIP 台账(表外 SKIP = FAIL,豁免源 = skip-allowlist.txt)", "")
if (@($allSkips).Count -eq 0) {
    $md += @("本轮零 SKIP。")
} else {
    foreach ($k in $allSkips) {
        $tag = if ($script:SKIP_ALLOW -contains $k) { "豁免表内" } else { "**表外 → 已计 FAIL**" }
        $md += "- $k → $tag"
    }
}
$md += @("", "## 环境指纹", "", '```json', ($script:FINGERPRINT | ConvertTo-Json -Compress), '```', "", "逐断言日志目录:``$logDir``")
$md | Set-Content -Path $mdPath -Encoding UTF8

Write-Host "`n=== V45 acceptance round: $verdict (PASS=$totP FAIL=$totF SKIP=$totS) ===" -ForegroundColor $(if ($verdict -eq "PASS") { "Green" } else { "Red" })
Write-Host "  台账: $jsonPath"
Write-Host "  报告: $mdPath"
if ($verdict -ne "PASS") { exit 1 } else { exit 0 }
