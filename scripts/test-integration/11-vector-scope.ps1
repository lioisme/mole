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
    # 归因先于结论(V48 实锤:把「框架整场缺席」报成「大概要重启模拟器」会让人白重开 VM)。
    # LSPosed 与 Vector 都是 Zygisk 模块 —— Magisk 没跑则两者一起不加载,与本项目代码无关;
    # 而雷电自带 su,所以「能 root」不等于「框架在跑」。
    PushScript "fwprobe.sh" @"
#!/system/bin/sh
if [ -d /dev/.magisk ] || [ -d /sbin/.magisk ]; then echo magisk=mounted; else echo magisk=absent; fi
Z=`$(pidof zygote64)
echo zygisk_in_zygote=`$(grep -c zygisk /proc/`$Z/maps 2>/dev/null)
echo module_mapped=`$(grep -c $($script:MODEV_PKG)- /proc/`$(pidof $($script:TARGET_PKG))/maps 2>/dev/null)
echo framework_log_newest=`$(ls -t /data/adb/lspd/log 2>/dev/null | head -1)
"@
    $fw = SuShell "sh /data/local/tmp/fwprobe.sh"
    $absent = "$fw" -match "magisk=absent|zygisk_in_zygote=0"
    if ($absent) {
        Write-Host "框架守护进程未运行(Zygisk 缺席)⇒ 注入类断言本轮必红,属环境档" -ForegroundColor Yellow
    } else {
        Write-Host "框架在跑但未注入,可能需要整机重启使 lspd 重载模块表" -ForegroundColor Yellow
    }
    Assert "Vector injection into testtarget" $false $(if ($absent) { "environment: magisk/zygisk not running" } else { "reboot required?" })
    Write-Host "注入诊断:`n$fw" -ForegroundColor Gray
}
DumpResults (Join-Path $script:TMP "scope-round.json")
Exit-WithResult
