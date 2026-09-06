# 21-run-enhance.ps1 — 增强矩阵 E(ON/OFF 二态行为断言)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tt-lib.ps1"

$E = @{
    "default"     = '{}'
    "proc-on"     = '{"processControlListen":true,"kinds":{"SCREEN_RECORD":true,"EXIT_INTERCEPT":true,"CRASH_PREVENT":true}}'
    "exit-on"     = '{"processControlListen":true,"kinds":{"EXIT_INTERCEPT":true}}'
    "crash-on"    = '{"processControlListen":true,"kinds":{"CRASH_PREVENT":true}}'
    "dlg-on"      = '{"uiListen":true,"kinds":{"DIALOG_KEYWORD":true},"dialogKeywords":["ModevKeywordHit"]}'
    "dlg-pass"    = '{"uiListen":true,"kinds":{"DIALOG_KEYWORD":true,"DIALOG_SHOW":true}}'
    "ssl1"        = '{"networkListen":true,"cryptoListen":false,"kinds":{"SSL_CONTEXT":true}}'
    "ssl2"        = '{"networkListen":true,"cryptoListen":false,"kinds":{"SSL_TRUST_MANAGER":true}}'
    # V38:okhttp/networkconfig 校验链位于 trust 放行之后,单开 pin/verifier 档时
    # trust 链先拒(V31 设计缺陷被 V36 起断言绑定失效掩盖),组合开档使命中可达。
    "ssl3"        = '{"networkListen":true,"cryptoListen":false,"kinds":{"SSL_TRUST_MANAGER":true,"SSL_OKHTTP_PINNER":true}}'
    "ssl4"        = '{"networkListen":true,"cryptoListen":false,"kinds":{"SSL_TRUST_MANAGER":true,"SSL_OKHTTP_VERIFIER":true}}'
    "ssl5"        = '{"networkListen":true,"cryptoListen":false,"kinds":{"SSL_TRUST_MANAGER":true,"SSL_WEBVIEW":true}}'
    "ssl6"        = '{"networkListen":true,"cryptoListen":false,"kinds":{"SSL_TRUST_MANAGER":true,"SSL_NETWORK_CONFIG":true}}'
    "proxy-on"    = '{"networkListen":true,"kinds":{"PROXY_SYSTEM_PROP":true,"PROXY_CONNECTIVITY":true,"PROXY_SETTINGS":true,"PROXY_OKHTTP":true}}'
    "vpn-on"      = '{"networkListen":true,"kinds":{"VPN_CAPABILITIES":true,"VPN_INTERFACE":true,"VPN_NETWORK_INFO":true,"VPN_LINK_PROPERTIES":true}}'
    "dns-on"      = '{"networkListen":true,"kinds":{"PROXY_CONNECTIVITY":true}}'
    "root-on"     = '{"deviceEnvListen":true,"kinds":{"ROOT_FILE":true,"ROOT_EXEC":true,"ROOT_PACKAGE_MANAGER":true,"ROOT_PROPERTY":true}}'
    "xposed-on"   = '{"deviceEnvListen":true,"kinds":{"XPOSED_CLASS":true,"XPOSED_STACK":true,"XPOSED_MAPS":true}}'
    "emulator-on" = '{"deviceEnvListen":true,"kinds":{"EMULATOR_BUILD":true,"EMULATOR_FILE":true,"EMULATOR_SENSOR":true,"EMULATOR_NETWORK":true,"EMULATOR_TELEPHONY":true,"EMULATOR_CPU":true,"EMULATOR_BATTERY":true}}'
    "procfile-on" = '{"deviceEnvListen":true,"kinds":{"SYSTEM_PROPERTIES":true,"PROC_STATUS":true,"PROC_MOUNT_INFO":true,"PROC_CGROUP":true,"PROC_NET":true}}'
}

function ApplyProfile([string]$name) {
    ResetState $E[$name]
}

Write-Host "== 增强矩阵 Round(E) ==" -ForegroundColor Cyan

# ---------- E-SEC:录屏解限(用户指定样板) ----------
Write-Host "`n--- E-SEC 录屏解限 ---" -ForegroundColor Magenta
ApplyProfile "default"
AdbShell "am start -S -n $script:TARGET_PKG/.TriggerActivity --es domain enhance-secure --es case E-SEC-01" | Out-Null
Start-Sleep -Seconds 4
$ratioOff = ScreencapNonBlackRatio
Assert "E-SEC-01 OFF screencap black" ($ratioOff -ge 0 -and $ratioOff -lt 0.02) "ratio=$ratioOff"
ApplyProfile "proc-on"
AdbShell "am start -S -n $script:TARGET_PKG/.TriggerActivity --es domain enhance-secure --es case E-SEC-01" | Out-Null
Start-Sleep -Seconds 4
$ratioOn = ScreencapNonBlackRatio
Assert "E-SEC-01 ON screencap visible" ($ratioOn -gt 0.05) "ratio=$ratioOn"
WaitSpoolDrain | Out-Null
$n = KindCount "process" "SCREEN_RECORD"
Assert "E-SEC-01 ON BLOCKED record" ($n -gt 0) "count=$n"
AdbShell "am start -S -n $script:TARGET_PKG/.TriggerActivity --es domain enhance-secure --es case E-SEC-02" | Out-Null
Start-Sleep -Seconds 4
$ratio2 = ScreencapNonBlackRatio
Assert "E-SEC-02 ON addFlags screencap visible" ($ratio2 -gt 0.05) "ratio=$ratio2"
AdbShell "am start -S -n $script:TARGET_PKG/.TriggerActivity --es domain enhance-secure --es case E-SEC-03" | Out-Null
Start-Sleep -Seconds 4
WaitSpoolDrain | Out-Null
$sup2 = @(Q "SELECT COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND recordType='process' AND kind='SCREEN_RECORD';")
Assert "E-SEC-03 ON clearFlags path recorded" ($sup2[0] -match "^[1-9]") "count=$($sup2[0])"
AdbShell "am force-stop $script:TARGET_PKG" | Out-Null

# ---------- E-EXIT / E-CRASH ----------
Write-Host "`n--- E-EXIT / E-CRASH ---" -ForegroundColor Magenta
ApplyProfile "default"
AdbShell "logcat -c" | Out-Null
RunCase "enhance-proc" "E-EXIT-01" 5
$alive = TargetAlive
Assert "E-EXIT-01 OFF process dies" (-not $alive)
ApplyProfile "exit-on"
RunCase "enhance-proc" "E-EXIT-01" 5
$alive = TargetAlive
$tt = GetTt "E-EXIT-01"
Assert "E-EXIT-01 ON process survives" ($alive -and ($tt -match "survived=true")) "tt=$tt"
WaitSpoolDrain | Out-Null
$n = KindCount "process" "EXIT_INTERCEPT"
Assert "E-EXIT-01 ON BLOCKED record" ($n -gt 0) "count=$n"
RunCase "enhance-proc" "E-EXIT-02" 5
$tt2 = GetTt "E-EXIT-02"
Assert "E-EXIT-02 ON survives" ($tt2 -match "survived=true") "tt=$tt2"
ApplyProfile "default"
AdbShell "logcat -c" | Out-Null
RunCase "enhance-proc" "E-CRASH-01" 6
$fatal = AdbShell "logcat -d | grep -c 'FATAL EXCEPTION'"
$fatalN = if ($fatal -match "(\d+)") { [int]$Matches[1] } else { -1 }
Assert "E-CRASH-01 OFF fatal crash" ($fatalN -gt 0) "fatal=$fatalN"
ApplyProfile "crash-on"
AdbShell "logcat -c" | Out-Null
RunCase "enhance-proc" "E-CRASH-01" 6
$tt3 = GetTt "E-CRASH-01"
$fatal2 = AdbShell "logcat -d | grep -c 'FATAL EXCEPTION'"
$fatal2N = if ($fatal2 -match "(\d+)") { [int]$Matches[1] } else { -1 }
Assert "E-CRASH-01 ON survives no-fatal" (($tt3 -match "survived=true") -and ($fatal2N -eq 0)) "tt=$tt3 fatal=$fatal2N"
WaitSpoolDrain | Out-Null
$n = KindCount "process" "CRASH_PREVENT"
Assert "E-CRASH-01 ON record" ($n -gt 0) "count=$n"

# ---------- E-DLG ----------
Write-Host "`n--- E-DLG 弹窗拦截 ---" -ForegroundColor Magenta
ApplyProfile "dlg-pass"
RunCase "enhance-dialog" "E-DLG-01" 6
$tt = GetTt "E-DLG-01"
Assert "E-DLG-01 OFF(no keywords) dialog shown" ($tt -match "shown=true") "tt=$tt"
ApplyProfile "dlg-on"
RunCase "enhance-dialog" "E-DLG-01" 6
$tt = GetTt "E-DLG-01"
Assert "E-DLG-01 ON dialog blocked" ($tt -match "shown=false") "tt=$tt"
WaitSpoolDrain | Out-Null
$n = KindCount "ui" "DIALOG_KEYWORD"
Assert "E-DLG-01 ON INTERCEPTED record" ($n -gt 0) "count=$n"
RunCase "enhance-dialog" "E-DLG-02" 6
$tt = GetTt "E-DLG-02"
Assert "E-DLG-02 ON non-keyword passes" ($tt -match "shown=true") "tt=$tt"

# ---------- E-SSL(逐开关档案) ----------
Write-Host "`n--- E-SSL 六路径 ---" -ForegroundColor Magenta
$SSL = @(
    @("E-SSL-01","ssl1","SSL_CONTEXT"), @("E-SSL-02","ssl2","SSL_TRUST_MANAGER"),
    @("E-SSL-03","ssl3","SSL_OKHTTP_PINNER"), @("E-SSL-04","ssl4","SSL_OKHTTP_VERIFIER"),
    @("E-SSL-06","ssl6","SSL_NETWORK_CONFIG")
)
foreach ($s in $SSL) {
    ApplyProfile "default"
    RunCase "enhance-ssl" $s[0] 8
    $tt = [string](GetTt $s[0])
    Assert "$($s[0]) OFF handshake fails" ("$tt" -match "false|fail") "tt=$tt"
    ApplyProfile $s[1]
    ClearDb
    RunCase "enhance-ssl" $s[0] 8
    $tt = [string](GetTt $s[0])
    Assert "$($s[0]) ON handshake ok" ("$tt" -match "true|200") "tt=$tt"
    WaitSpoolDrain | Out-Null
    $n = KindCount "network" $s[2]
    Assert "$($s[0]) ON record kind=$($s[2])" ($n -gt 0) "count=$n"
}
# E-SSL-05 WebView SSL 错误
ApplyProfile "default"
AdbShell "am start -S -n $script:TARGET_PKG/.WvActivity --es case E-SSL-05" | Out-Null
Start-Sleep -Seconds 8
$tt = GetTt "E-SSL-05"
Assert "E-SSL-05 OFF webview ssl error page" ($tt -notmatch "loaded=true") "tt=$tt"
ApplyProfile "ssl5"
ClearDb
AdbShell "am start -S -n $script:TARGET_PKG/.WvActivity --es case E-SSL-05" | Out-Null
Start-Sleep -Seconds 8
$tt = GetTt "E-SSL-05"
Assert "E-SSL-05 ON webview proceeds" ($tt -match "loaded=true") "tt=$tt"
WaitSpoolDrain | Out-Null
$n = KindCount "network" "SSL_WEBVIEW"
Assert "E-SSL-05 ON record" ($n -gt 0) "count=$n"

# ---------- E-PRX(需全局代理) ----------
Write-Host "`n--- E-PRX 代理隐藏 ---" -ForegroundColor Magenta
AdbShell "settings put global http_proxy 127.0.0.1:8888" | Out-Null
ApplyProfile "default"
RunCase "enhance-proxy" "E-PRX-01" 5
$tt = GetTt "E-PRX-01"
Assert "E-PRX-01 OFF proxy visible" ($tt -match "proxyHost=127") "tt=$tt"
ApplyProfile "proxy-on"
RunCase "enhance-proxy" "E-PRX-01" 5
$tt = GetTt "E-PRX-01"
Assert "E-PRX-01 ON proxy hidden" ($tt -match "proxyHost=$") "tt=$tt"
foreach ($c in @("E-PRX-02","E-PRX-03","E-PRX-04")) {
    WaitSpoolDrain | Out-Null
    $n = KindCount "network" ($c -replace "E-PRX-","PROXY_")
    Write-Host "  [INFO] $c record probe kind=$($c -replace 'E-PRX-','PROXY_') count=$n"
}
RunCase "enhance-proxy" "E-PRX-02" 5
$tt = GetTt "E-PRX-02"
Assert "E-PRX-02 ON defaultProxy null" ($tt -match "defaultProxy=null") "tt=$tt"
RunCase "enhance-proxy" "E-PRX-03" 5
$tt = GetTt "E-PRX-03"
Assert "E-PRX-03 ON global proxy null" ($tt -match "globalHttpProxy=null") "tt=$tt"
RunCase "enhance-proxy" "E-PRX-04" 5
$tt = GetTt "E-PRX-04"
Assert "E-PRX-04 ON selector DIRECT" ($tt -match "selector=DIRECT") "tt=$tt"
AdbShell "settings put global http_proxy :0" | Out-Null

# ---------- E-VPN / E-DNS(无 VPN 链路 → 记录面断言) ----------
Write-Host "`n--- E-VPN / E-DNS ---" -ForegroundColor Magenta
ApplyProfile "vpn-on"
ClearDb
foreach ($c in @("E-VPN-01","E-VPN-02","E-VPN-03","E-VPN-04")) { RunCase "enhance-vpn" $c 5 }
$tt = GetTt "E-VPN-01"
Skip "E-VPN ON/OFF delta" "环境无 VPN 链路,二态差异物理不可达;记录面见下"
WaitSpoolDrain | Out-Null
$n = TypeCount "network"
Assert "E-VPN hooks fire (records)" ($n -gt 0) "count=$n"

# ---------- E-ROOT / E-XPP / E-EMU / E-PRCFILE ----------
Write-Host "`n--- E-ROOT / E-XPP ---" -ForegroundColor Magenta
ApplyProfile "default"
foreach ($c in @("E-ROOT-01","E-ROOT-02","E-ROOT-03","E-ROOT-04","E-XPP-01","E-XPP-02","E-XPP-03")) { RunCase "enhance-root" $c 5 }
$ttMaps = GetTt "E-XPP-03"
Skip "E-ROOT delta" "Magisk 默认 denylist 已对 App 隐藏 su,OFF 基线不可区分;记录面断言见 L-DEV"
ApplyProfile "xposed-on"
ClearDb
foreach ($c in @("E-XPP-01","E-XPP-02","E-XPP-03")) { RunCase "enhance-xposed" $c 5 }
$tt = GetTt "E-XPP-03"
Assert "E-XPP-03 ON maps clean" ($tt -match "dirtyLines=0") "tt=$tt"
WaitSpoolDrain | Out-Null
$n = KindCount "device_env" "XPOSED_MAPS"
Assert "E-XPP-03 ON record" ($n -gt 0) "count=$n"

Write-Host "`n--- E-EMU ---" -ForegroundColor Magenta
ApplyProfile "default"
RunCase "enhance-emulator" "E-EMU-01" 5
$ttOff = GetTt "E-EMU-01"
ApplyProfile "emulator-on"
RunCase "enhance-emulator" "E-EMU-01" 5
$ttOn = GetTt "E-EMU-01"
Skip "E-EMU-01 ON Build spoofed" "LDPlayer 已预伪装 Xiaomi 真机值,无模拟器标记可改;钩子逻辑由 EmulatorBuildHookerTest 单测覆盖"
Skip "E-EMU-01 OFF/ON delta" "环境预伪装,OFF/ON 同值属预期"
WaitSpoolDrain | Out-Null
$n = KindCount "device_env" "EMULATOR_BUILD"
Assert "E-EMU-01 ON record" ($n -gt 0) "count=$n"
foreach ($c in @("E-EMU-02","E-EMU-03","E-EMU-04","E-EMU-05","E-EMU-06","E-EMU-07")) { RunCase "enhance-emulator" $c 5 }

Write-Host "`n--- E-PRCFILE ---" -ForegroundColor Magenta
ApplyProfile "procfile-on"
ClearDb
foreach ($c in @("E-PRC-01","E-PRC-02","E-PRC-03","E-PRC-04","E-PRC-05")) { RunCase "enhance-procfile" $c 5 }
foreach ($k in @("PROC_MOUNT_INFO","PROC_NET","SYSTEM_PROPERTIES")) {
    WaitSpoolDrain | Out-Null
    $n = KindCount "device_env" $k
    Assert "E-PRCFILE record kind=$k" ($n -gt 0) "count=$n"
}
foreach ($k in @("PROC_STATUS","PROC_CGROUP")) {
    Skip "E-PRCFILE record kind=$k" "环境无 magisk cgroup 行/TracerPid=0,无脏可掩即正确行为"
}

# ---------- E-MP ----------
Write-Host "`n--- E-MP MediaProjection ---" -ForegroundColor Magenta
ApplyProfile "default"
AdbShell "am start -S -n $script:TARGET_PKG/.TriggerActivity --es domain enhance-proc --es case E-MP-01" | Out-Null
Start-Sleep -Seconds 4
$dlg = (UiDumpContains "录屏") -or (UiDumpContains "录制") -or (UiDumpContains "Screen")
if (-not $dlg) { $dlg = UiDumpContains "screen" }
Assert "E-MP-01 OFF consent dialog appears" $dlg
ApplyProfile "proc-on"
AdbShell "am start -S -n $script:TARGET_PKG/.TriggerActivity --es domain enhance-proc --es case E-MP-01" | Out-Null
Start-Sleep -Seconds 4
$dlg2 = (UiDumpContains "录屏") -or (UiDumpContains "录制") -or (UiDumpContains "Screen")
if (-not $dlg2) { $dlg2 = UiDumpContains "screen" }
Assert "E-MP-01 ON dialog suppressed" (-not $dlg2)
WaitSpoolDrain | Out-Null
$n = KindCount "process" "SCREEN_RECORD"
Assert "E-MP-01 ON record" ($n -gt 0) "count=$n"
AdbShell "am force-stop $script:TARGET_PKG" | Out-Null

RemoveConfig
DumpResults (Join-Path $script:TMP "enhance-round.json")
