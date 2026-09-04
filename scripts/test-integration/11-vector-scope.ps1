# 11-vector-scope.ps1 — 确保 testtarget 在 Vector 作用域并验证注入生效
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tt-lib.ps1"

Write-Host "== ensure scope row ==" -ForegroundColor Cyan
PushScript "scope.sh" @"
#!/system/bin/sh
DB=/data/adb/lspd/config/modules_config.db
sqlite3 `$DB "INSERT OR IGNORE INTO scope(mid,app_pkg_name,user_id) SELECT mid,'$script:TARGET_PKG',0 FROM modules WHERE module_pkg_name='$script:MODEV_PKG';"
sqlite3 `$DB "SELECT app_pkg_name FROM scope WHERE mid=(SELECT mid FROM modules WHERE module_pkg_name='$script:MODEV_PKG');"
"@
$rows = SuShell "sh /data/local/tmp/scope.sh"
if ($rows -notmatch [regex]::Escape($script:TARGET_PKG)) { throw "scope insert failed: $rows" }
Write-Host "scope rows:`n$rows"

Write-Host "== verify injection (HookInstaller log after trigger) ==" -ForegroundColor Cyan
RestartTarget
EnsureHost
AdbShell "logcat -c" | Out-Null
RunCase "crypto" "L-CRY-01" 6
$log = AdbShell "logcat -d -s HookInstaller"
if ($log -match "探针已安装|Probe") {
    Assert "Vector injection into testtarget" $true
} else {
    Write-Host "注入日志缺失,可能需要重启模拟器使 Vector 重载作用域" -ForegroundColor Yellow
    Assert "Vector injection into testtarget" $false "reboot required?"
}
