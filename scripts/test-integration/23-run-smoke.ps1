# 23-run-smoke.ps1 — 被作用域 App 冷启动冒烟门禁(D-17 防回归)
# 全功能/全子功能 ON 档案下,逐个冷启动作用域 App,断言存活 + 无 FATAL + 无 ANR。
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\tt-lib.ps1"

# 全开档案:10 master + 全部 81 kind + 微调项(含全部干预型)
$ALL_ON = '{"cryptoListen":true,"dbListen":true,"behaviorListen":true,"prefsListen":true,"webviewListen":true,"networkListen":true,"deviceEnvListen":true,"uiListen":true,"systemListen":true,"processControlListen":true,"logStack":false,"fileContentCapture":true,"dialogKeywords":["ModevKeywordHit"],"kinds":{"MESSAGE_DIGEST":true,"CIPHER":true,"MAC":true,"SIGNATURE":true,"KEY_PARAM":true,"KEY_GENERATION":true,"KEY_FACTORY":true,"KEY_STORE":true,"KEY_AGREEMENT":true,"RANDOM":true,"BASE64":true,"WEB_CRYPTO":true,"OPEN":true,"QUERY":true,"DML":true,"EXEC":true,"TRANSACTION":true,"FILE_READ":true,"FILE_WRITE":true,"FILE_DELETE":true,"FILE_RENAME":true,"FILE_LIST":true,"ASSET_READ":true,"SHELL_EXEC":true,"GET":true,"PUT":true,"REMOVE":true,"CLEAR":true,"PAGE_LOAD":true,"EVALUATE_JS":true,"JS_BRIDGE":true,"DEBUG":true,"JS_CRYPTO":true,"SSL_CONTEXT":true,"SSL_TRUST_MANAGER":true,"SSL_OKHTTP_PINNER":true,"SSL_OKHTTP_VERIFIER":true,"SSL_WEBVIEW":true,"SSL_NETWORK_CONFIG":true,"PROXY_SYSTEM_PROP":true,"PROXY_CONNECTIVITY":true,"PROXY_SETTINGS":true,"PROXY_OKHTTP":true,"VPN_CAPABILITIES":true,"VPN_INTERFACE":true,"VPN_NETWORK_INFO":true,"VPN_LINK_PROPERTIES":true,"REQUEST":true,"ROOT_FILE":true,"ROOT_EXEC":true,"ROOT_PACKAGE_MANAGER":true,"ROOT_PROPERTY":true,"XPOSED_CLASS":true,"XPOSED_STACK":true,"XPOSED_MAPS":true,"EMULATOR_BUILD":true,"EMULATOR_FILE":true,"EMULATOR_SENSOR":true,"EMULATOR_NETWORK":true,"EMULATOR_TELEPHONY":true,"EMULATOR_CPU":true,"EMULATOR_BATTERY":true,"SYSTEM_PROPERTIES":true,"PROC_STATUS":true,"PROC_MOUNT_INFO":true,"PROC_CGROUP":true,"PROC_NET":true,"DETECT_ROOT":true,"DETECT_EMULATOR":true,"DIALOG_SHOW":true,"DIALOG_KEYWORD":true,"WIDGET_TEXT":true,"ACTIVITY_START":true,"ACTIVITY_RESUME":true,"LOGCAT":true,"APP_INFO":true,"SIGNATURE":true,"EXIT_INTERCEPT":true,"CRASH_PREVENT":true,"SCREEN_RECORD":true}}'

# 作用域 App → 启动组件(经 resolve-activity 动态解析,失败回退已知值)
$APPS = @(
    "com.lioisme.modev.testtarget",
    "com.ucheuhuo.app.apex",
    "io.github.vvb2060.mahoshojo",
    "icu.nullptr.nativetest",
    "com.demo.test"
)

function Resolve-Launch([string]$pkg) {
    $r = AdbShell "cmd package resolve-activity --brief $pkg"
    $line = ($r -split "`n" | Where-Object { $_ -match "$pkg/" } | Select-Object -Last 1)
    if ($line) { return $line.Trim() }
    return $null
}

function ColdStart([string]$pkg, [int]$settleSec = 14) {
    $comp = Resolve-Launch $pkg
    if (-not $comp) { Skip "smoke/$pkg" "无可启动 Activity(非启动器应用)"; return }
    AdbShell "am force-stop $pkg" | Out-Null
    AdbShell "logcat -c -b crash" | Out-Null
    AdbShell "am start -S -n $comp" | Out-Null
    Start-Sleep -Seconds $settleSec
    $pid = AdbShell "pidof $pkg"
    $alive = $pid -match "\d"
    $fatal = AdbShell "logcat -d -b crash"
    $crashed = $fatal -match [regex]::Escape($pkg) -or $fatal -match "FATAL EXCEPTION"
    $anr = (AdbShell "logcat -d -t 200") -match "ANR in $pkg"
    $ok = $alive -and (-not $crashed) -and (-not $anr)
    $detail = if ($crashed) { "FATAL in crash buffer" } elseif ($anr) { "ANR" } elseif (-not $alive) { "process dead" } else { "pid=$($pid.Trim())" }
    Assert "smoke/$pkg all-features-ON cold start" $ok $detail
}

Write-Host "== 冷启动冒烟:全功能 ON ==" -ForegroundColor Cyan
if (-not (WaitDevice)) { Assert "smoke/device-reachable" $false "VM lost"; exit 1 }
AdbShell "settings put global cached_apps_freezer disabled" | Out-Null
EnsureHost

foreach ($pkg in $APPS) {
    # 磁盘兜底全开档案(V35 通道,注入侧重启即生效)
    $local = Join-Path $script:TMP "cfg_all.json"
    [IO.File]::WriteAllText($local, $ALL_ON, (New-Object System.Text.UTF8Encoding($false)))
    Reconnect
    & $script:ADB -s $script:DEVICE push $local "/data/local/tmp/modev_config_$pkg.json" 2>&1 | Out-Null
    SuShell "chmod 666 /data/local/tmp/modev_config_$pkg.json" | Out-Null
    ColdStart $pkg
}

# testtarget 专属:L-SMOKE-01 帧语义断言
RunCase "smoke" "L-SMOKE-01" 6
$tt = GetTt "L-SMOKE-01"
Assert "smoke/L-SMOKE-01 forName caller-loader intact" ($tt -match "forNameSingle=ok.*forNameExplicit=ok.*instantiate=ok") "tt=$tt"

# 清理他 App 全开档案(保留 testtarget 供后续矩阵)
foreach ($pkg in ($APPS | Where-Object { $_ -ne $script:TARGET_PKG })) {
    SuShell "rm -f /data/local/tmp/modev_config_$pkg.json" | Out-Null
}

DumpResults (Join-Path $script:TMP "smoke-round.json")
