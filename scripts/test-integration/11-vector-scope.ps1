# 11-vector-scope.ps1 — 确保 testtarget 在 Vector 作用域并验证注入生效
# -SkipInjectionVerify 用于「只要作用域、不跑注入矩阵」的轮次(如纯前端插桩轮):
# 注入日志断言在覆盖安装后需整机重启才可能成立,不该把不需要它的轮次锁死。
# (开关式而非布尔值:-File 传参一律按字符串绑定,`$false` 字面量会被当成非空串判真。)
param([switch]$SkipInjectionVerify)
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
# V45 T0.7/QA-1:门禁步同样必须落逐断言台账(编排器「无台账 = 不可判定 = FAIL」),
# 故 fail 走 Assert 记录而非 throw(throw 会让台账消失,只剩不可解释的红灯)。
Assert "testtarget in Vector scope" ($rows -match [regex]::Escape($script:TARGET_PKG)) "scope rows: $rows"
if ($script:FAIL -gt 0) {
    DumpResults (Join-Path $script:TMP "scope-round.json")
    Exit-WithResult
}
Write-Host "scope rows:`n$rows"

Write-Host "== verify injection (HookInstaller log after trigger) ==" -ForegroundColor Cyan
if ($SkipInjectionVerify) {
    Write-Host "  [SKIP] 注入生效验证(本轮跳过注入矩阵)" -ForegroundColor Yellow
    DumpResults (Join-Path $script:TMP "scope-round.json")
    Exit-WithResult
}
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
DumpResults (Join-Path $script:TMP "scope-round.json")
Exit-WithResult
