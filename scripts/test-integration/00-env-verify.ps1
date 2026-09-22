# 00-env-verify.ps1 - Environment baseline check
# V45 T0.4/BL-4:两类断言分档 —— 编排的 preflight 门禁只能查「静态环境」
# (设备/安装包/版本/root/框架/伴生 App),「运行态」(进程存活/spool/已入库记录)
# 在装包与作用域接线之前必然为假,混在一档会让整轮验收在门口就 BLOCKED。
#   env     = 仅静态环境
#   runtime = 仅运行态(作用域接线后、注入矩阵前)
#   all     = 两档全查(默认,兼容人工单跑)
param([ValidateSet("env", "runtime", "all")][string]$Mode = "all")

. "$PSScriptRoot\tt-env.ps1"
$ADB = $script:ADB
$DEVICE = $script:DEVICE
$TARGET_PKG = Get-ModevEnv "MODEV_TWILIGHT_PKG" "modev.twilightPkg" "com.ucheuhuo.app.apex"
$MODEV_PKG = $script:MODEV_PKG

Write-Host ""
Write-Host "========================================"
Write-Host "  Modev x Twilight Toolbox - Env Check"
Write-Host "========================================"
Write-Host ""

$pass = 0; $fail = 0
# 逐断言台账(V45 QA-1:编排器只认 JSON 台账,「退出码 0 且无台账」按不可判定 = FAIL)
$script:ROWS = New-Object System.Collections.ArrayList

function Check($name, $ok) {
    if ($ok) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  [FAIL] $name" -ForegroundColor Red; $script:fail++ }
    [void]$script:ROWS.Add([ordered]@{
        Id = "ENV-{0:D2}" -f ($script:ROWS.Count + 1)
        Name = $name
        Status = $(if ($ok) { "PASS" } else { "FAIL" })
        Mode = $Mode
    })
}

$d = & $ADB devices 2>&1
Check "LDPlayer connected" ($d -match "127.0.0.1:5555")

$t = & $ADB -s $DEVICE shell "pm list packages $TARGET_PKG" 2>$null
Check "Twilight installed" ($t -match $TARGET_PKG)

$m = & $ADB -s $DEVICE shell "pm list packages $MODEV_PKG" 2>$null
Check "Modev installed" ($m -match $MODEV_PKG)

$tv = & $ADB -s $DEVICE shell "dumpsys package $TARGET_PKG" 2>$null | Select-String "versionName"
Check "Twilight version found" ($tv -ne $null)

if ($Mode -ne "env") {
    # 库文件 = 「应用已运行过」的产物(首启才建),不属静态环境档
    $db = & $ADB -s $DEVICE shell "su -c 'ls /data/user/0/$MODEV_PKG/databases/modev.db'" 2>$null
    Check "Modev DB exists" ($db -match "modev.db")

    $mp = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $MODEV_PKG
    Check "Modev process alive" ($mp -ne $null)

    $tp = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $TARGET_PKG
    Check "Twilight process alive" ($tp -ne $null)

    $sp = & $ADB -s $DEVICE shell "su -c 'ls /data/user/0/$TARGET_PKG/files/modev_spool/$TARGET_PKG/spool.dat'" 2>$null
    Check "Spool file exists" ($sp -match "spool.dat")
}

$root = & $ADB -s $DEVICE shell "su -c id" 2>$null
Check "Root available" ($root -match "uid=0")

$mag = & $ADB -s $DEVICE shell "pm list packages" 2>$null | Select-String "magisk"
Check "Magisk installed" ($mag -ne $null)

$pp = & $ADB -s $DEVICE shell "pm list packages" 2>$null | Select-String "com.network.proxy"
Check "PinProxy installed" ($pp -ne $null)

if ($Mode -ne "env") {
    # Record count
    $SQLITE = $script:SQLITE
    $tmpDb = "$env:TEMP\modev_env_check.db"
    & $ADB -s $DEVICE shell "su -c 'cp /data/user/0/$MODEV_PKG/databases/modev.db /sdcard/modev_env_check.db'" 2>$null | Out-Null
    & $ADB -s $DEVICE pull /sdcard/modev_env_check.db $tmpDb 2>$null | Out-Null
    if (Test-Path $tmpDb) {
        $cnt = & $SQLITE $tmpDb "SELECT COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG';" 2>$null
        $cntVal = $cnt.Trim()
        Check "Modev has records ($cntVal)" ([int]$cntVal -gt 0)
        Remove-Item $tmpDb -Force -ErrorAction SilentlyContinue
    } else {
        Check "DB readable" $false
    }
}

Write-Host ""
Write-Host "  Result: $pass passed, $fail failed" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Yellow" })
Write-Host ""
# 台账目录 = tt-env 的 $script:TMP(%TEMP%\modev-tt),与其余矩阵脚本同一约定
$script:ROWS | ConvertTo-Json | Set-Content -Path (Join-Path $script:TMP "env-round.json") -Encoding UTF8
exit $(if ($fail -gt 0) { 1 } else { 0 })
