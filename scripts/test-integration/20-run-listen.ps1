# 20-run-listen.ps1 — 监听矩阵 L(81 kind 正向 + 10 域负向)全量执行
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tt-lib.ps1"

# ---------- 配置档案(AppListenOverride JSON) ----------
$P = @{
    "crypto-all" = '{"cryptoListen":true,"logStack":false,"kinds":{"MESSAGE_DIGEST":true,"CIPHER":true,"MAC":true,"SIGNATURE":true,"KEY_PARAM":true,"KEY_GENERATION":true,"KEY_FACTORY":true,"KEY_STORE":true,"KEY_AGREEMENT":true,"RANDOM":true,"BASE64":true,"WEB_CRYPTO":true}}'
    "db-all"     = '{"dbListen":true,"logStack":false,"kinds":{"OPEN":true,"QUERY":true,"DML":true,"EXEC":true,"TRANSACTION":true}}'
    "bhv-all"    = '{"behaviorListen":true,"logStack":false,"kinds":{"FILE_READ":true,"FILE_WRITE":true,"FILE_DELETE":true,"FILE_RENAME":true,"FILE_LIST":true,"ASSET_READ":true,"SHELL_EXEC":true},"fileContentCapture":true}'
    "prf-all"    = '{"prefsListen":true,"logStack":false,"kinds":{"OPEN":true,"GET":true,"PUT":true,"REMOVE":true,"CLEAR":true},"fileContentCapture":true}'
    "wv-all"     = '{"webviewListen":true,"logStack":false,"kinds":{"PAGE_LOAD":true,"EVALUATE_JS":true,"JS_BRIDGE":true,"DEBUG":true,"JS_CRYPTO":true}}'
    "net-obs"    = '{"networkListen":true,"logStack":false}'
    "dev-all"    = '{"deviceEnvListen":true,"logStack":false,"kinds":{"ROOT_FILE":true,"ROOT_EXEC":true,"ROOT_PACKAGE_MANAGER":true,"ROOT_PROPERTY":true,"XPOSED_CLASS":true,"XPOSED_STACK":true,"XPOSED_MAPS":true,"EMULATOR_BUILD":true,"EMULATOR_FILE":true,"EMULATOR_SENSOR":true,"EMULATOR_NETWORK":true,"EMULATOR_TELEPHONY":true,"EMULATOR_CPU":true,"EMULATOR_BATTERY":true,"SYSTEM_PROPERTIES":true,"PROC_STATUS":true,"PROC_MOUNT_INFO":true,"PROC_CGROUP":true,"PROC_NET":true,"DETECT_ROOT":true,"DETECT_EMULATOR":true}}'
    "ui-all"     = '{"uiListen":true,"logStack":false,"kinds":{"DIALOG_SHOW":true,"DIALOG_KEYWORD":true,"WIDGET_TEXT":true,"ACTIVITY_START":true,"ACTIVITY_RESUME":true},"dialogKeywords":["ModevKeywordHit"]}'
    "sys-all"    = '{"systemListen":true,"logStack":false,"kinds":{"LOGCAT":true,"APP_INFO":true,"SIGNATURE":true}}'
    "proc-on"    = '{"processControlListen":true,"logStack":false,"kinds":{"SCREEN_RECORD":true}}'
    "crypto-off" = '{"cryptoListen":false}'
    "db-off"     = '{"dbListen":false}'
    "bhv-off"    = '{"behaviorListen":false}'
    "prf-off"    = '{"prefsListen":false}'
    "wv-off"     = '{"webviewListen":false}'
    "net-off"    = '{"networkListen":false}'
    "dev-off"    = '{"deviceEnvListen":false}'
    "ui-off"     = '{"uiListen":false}'
    "sys-off"    = '{"systemListen":false}'
    "proc-off"   = '{"processControlListen":false}'
}

# ---------- 域定义:profile / 记录类型 / 用例序列 / 预期 kind 集合 ----------
$DOMAINS = [ordered]@{
    "crypto"    = @{ profile="crypto-all"; neg="crypto-off";    rt="crypto";    cases=@(
        @("L-CRY-01","crypto",5), @("L-CRY-02","crypto",5), @("L-CRY-03","crypto",5), @("L-CRY-04","crypto",5),
        @("L-CRY-05","crypto",5), @("L-CRY-06","crypto",5), @("L-CRY-07","crypto",5), @("L-CRY-08","crypto",5),
        @("L-CRY-09","crypto",5), @("L-CRY-10","crypto",5), @("L-CRY-11","crypto",6), @("L-CRY-12","crypto",5),
        @("L-CRY-13","crypto",5), @("L-CRY-14","crypto",5))
        kinds=@("MESSAGE_DIGEST","CIPHER","MAC","SIGNATURE","KEY_PARAM","KEY_GENERATION","KEY_FACTORY","KEY_STORE","KEY_AGREEMENT","RANDOM","BASE64") }
    "db"        = @{ profile="db-all";     neg="db-off";        rt="db";        cases=@(
        @("L-DB-01","db",5), @("L-DB-02","db",5), @("L-DB-03","db",5), @("L-DB-04","db",5), @("L-DB-05","db",5),
        @("L-DB-06","db",5), @("L-DB-07","db",5), @("L-DB-08","db",5), @("L-DB-09","db",5), @("L-DB-11","db",8),
        @("L-DB-12","db",5))
        kinds=@("OPEN","QUERY","DML","EXEC","TRANSACTION") }
    "behavior"  = @{ profile="bhv-all";    neg="bhv-off";       rt="behavior";  cases=@(
        @("L-BHV-01","behavior",5), @("L-BHV-02","behavior",5), @("L-BHV-03","behavior",5), @("L-BHV-04","behavior",5),
        @("L-BHV-05","behavior",5), @("L-BHV-06","behavior",5), @("L-BHV-07","behavior",5), @("L-BHV-08","behavior",5),
        @("L-BHV-09","behavior",5), @("L-BHV-10","behavior",6), @("L-BHV-11","behavior",6), @("L-BHV-12","behavior",5),
        @("L-BHV-13","behavior",5))
        kinds=@("FILE_READ","FILE_WRITE","FILE_DELETE","FILE_RENAME","FILE_LIST","ASSET_READ","SHELL_EXEC") }
    "prefs"     = @{ profile="prf-all";    neg="prf-off";       rt="prefs";     cases=@(
        @("L-PRF-01","prefs",5), @("L-PRF-02","prefs",5), @("L-PRF-03","prefs",5), @("L-PRF-04","prefs",5),
        @("L-PRF-05","prefs",5), @("L-PRF-06","prefs",5))
        kinds=@("OPEN","GET","PUT","REMOVE","CLEAR") }
    "webview"   = @{ profile="wv-all";     neg="wv-off";        rt="webview";   cases=@(
        @("L-WV-01","webview",6), @("L-WV-02","webview",6), @("L-WV-03","webview",7), @("L-WV-04","webview",7),
        @("L-WV-05","webview",6), @("L-WV-06","webview",6), @("L-WV-07","webview",10))
        kinds=@("PAGE_LOAD","EVALUATE_JS","JS_BRIDGE","DEBUG","JS_CRYPTO") }
    "network"   = @{ profile="net-obs";    neg="net-off";       rt="network";   cases=@(
        @("L-NET-01","network",6), @("L-NET-02","network",6))
        kinds=@("REQUEST") }
    "deviceenv" = @{ profile="dev-all";    neg="dev-off";       rt="device_env"; cases=@(
        @("L-DEV-01","deviceenv",5), @("L-DEV-02","deviceenv",6), @("L-DEV-03","deviceenv",6), @("L-DEV-04","deviceenv",5),
        @("L-DEV-05","deviceenv",5), @("L-DEV-06","deviceenv",5), @("L-DEV-07","deviceenv",6), @("L-DEV-08","deviceenv",5),
        @("L-DEV-09","deviceenv",5), @("L-DEV-10","deviceenv",5), @("L-DEV-11","deviceenv",5), @("L-DEV-12","deviceenv",5),
        @("L-DEV-13","deviceenv",5), @("L-DEV-14","deviceenv",5), @("L-DEV-15","deviceenv",5), @("L-DEV-16","deviceenv",5),
        @("L-DEV-17","deviceenv",5), @("L-DEV-18","deviceenv",5), @("L-DEV-19","deviceenv",5), @("L-DEV-20","deviceenv",6),
        @("L-DEV-21","deviceenv",6))
        kinds=@("ROOT_FILE","ROOT_EXEC","XPOSED_CLASS","XPOSED_STACK","XPOSED_MAPS",
                "EMULATOR_FILE","EMULATOR_NETWORK","EMULATOR_TELEPHONY",
                "SYSTEM_PROPERTIES","PROC_MOUNT_INFO","PROC_NET",
                "DETECT_ROOT","DETECT_EMULATOR")
        # 掩蔽型 kind:LDPlayer/Magisk 环境预干净(无 su 包可见/test-keys/qemu 传感器名/
        # magisk cgroup 行/TracerPid≠0),「无脏可掩 → 无记录」即正确行为;干预有效性由 E-* 二态验证
        conditional=@("ROOT_PACKAGE_MANAGER","ROOT_PROPERTY","EMULATOR_BUILD","EMULATOR_SENSOR",
                "EMULATOR_CPU","EMULATOR_BATTERY","PROC_STATUS","PROC_CGROUP") }
    "ui"        = @{ profile="ui-all";     neg="ui-off";        rt="ui";        cases=@(
        @("L-UI-01","ui",6), @("L-UI-02","ui",6), @("L-UI-03","ui",5), @("L-UI-04","ui",6), @("L-UI-05","ui",5))
        kinds=@("DIALOG_SHOW","DIALOG_KEYWORD","WIDGET_TEXT","ACTIVITY_START","ACTIVITY_RESUME") }
    "system"    = @{ profile="sys-all";    neg="sys-off";       rt="system";    cases=@(
        @("L-SYS-01","system",5), @("L-SYS-02","system",5), @("L-SYS-03","system",5))
        kinds=@("LOGCAT","APP_INFO","SIGNATURE") }
    "process"   = @{ profile="proc-on";    neg="proc-off";      rt="process";   cases=@(
        @("L-PRC-01","enhance-secure",6))
        kinds=@("SCREEN_RECORD") }
}

Write-Host "== 监听矩阵 Round(L) ==" -ForegroundColor Cyan
EnsureHost

foreach ($dom in $DOMAINS.Keys) {
  try {
    $spec = $DOMAINS[$dom]
    Write-Host "`n--- domain: $dom ---" -ForegroundColor Magenta
    if (-not (WaitDevice)) { Assert "L/$dom device-reachable" $false "VM lost"; continue }
    ResetState $P[$spec.profile]
    foreach ($c in $spec.cases) {
        RunCase $c[1] $c[0] $c[2]
    }
    WaitSpoolDrain | Out-Null
    $counts = KindCountsAll
    foreach ($k in $spec.kinds) {
        $n = if ($counts.ContainsKey("$($spec.rt)|$k")) { $counts["$($spec.rt)|$k"] } else { 0 }
        Assert "L/$dom kind=$k" ($n -gt 0) "count=$n"
    }
    $cond = $spec.conditional
    if ($null -eq $cond) { $cond = @() }
    foreach ($k in $cond) {
        $n = if ($counts.ContainsKey("$($spec.rt)|$k")) { $counts["$($spec.rt)|$k"] } else { 0 }
        Skip "L/$dom kind=$k (masking-type)" "环境预干净无脏可掩;count=$n;干预有效性见 E-* 二态"
    }
    # 负向:master 关 → 该类型零产出
    ResetState $P[$spec.neg]
    foreach ($c in $spec.cases) {
        RunCase $c[1] $c[0] ([math]::Min($c[2], 4))
    }
    Start-Sleep -Seconds 4
    WaitSpoolDrain | Out-Null
    $counts2 = KindCountsAll
    $n = if ($counts2.ContainsKey("$($spec.rt)|")) { $counts2["$($spec.rt)|"] } else { 0 }
    # 类型级零断言:该 rt 下任何 kind 都不应出现
    $anyInRt = 0
    foreach ($key in $counts2.Keys) { if ($key -like "$($spec.rt)|*") { $anyInRt += $counts2[$key] } }
    Assert "L/$dom NEG master-off zero" ($anyInRt -eq 0) "total=$anyInRt"
  } catch {
    Assert "L/$dom domain-execution" $false "exception: $($_.Exception.Message)"
  }
}

# 字段质量抽查
Write-Host "`n--- 字段质量抽查 ---" -ForegroundColor Magenta
ResetState $P["crypto-all"]
RunCase "crypto" "L-CRY-01" 6
RunCase "crypto" "L-CRY-02" 6
WaitSpoolDrain | Out-Null
$ok = @(Q "SELECT COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND kind='CIPHER' AND keyHex IS NOT NULL AND keyHex != '' AND ivHex IS NOT NULL AND ivHex != '';")
Assert "Q/CIPHER key+iv captured" ($ok[0] -match "^[1-9]") "rows=$($ok[0])"
$ok2 = @(Q "SELECT COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND kind='MESSAGE_DIGEST' AND algorithm LIKE '%SHA%';")
Assert "Q/DIGEST algorithm name" ($ok2[0] -match "^[1-9]") "rows=$($ok2[0])"

ResetState $P["db-all"]
RunCase "db" "L-DB-04" 6
WaitSpoolDrain | Out-Null
$ok3 = @(Q "SELECT COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND kind='QUERY' AND operation='QUERY_RESULT';")
Assert "Q/QUERY_RESULT passive capture" ($ok3[0] -match "^[1-9]") "rows=$($ok3[0])"

RemoveConfig
DumpResults (Join-Path $script:TMP "listen-round.json")
