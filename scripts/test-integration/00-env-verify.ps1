# 00-env-verify.ps1 - Environment baseline check
param()

$ADB = "D:\leidian\LDPlayer14\adb.exe"
$DEVICE = "127.0.0.1:5555"
$TARGET_PKG = "com.ucheuhuo.app.apex"
$MODEV_PKG = "com.lioisme.modev"

Write-Host ""
Write-Host "========================================"
Write-Host "  Modev x Twilight Toolbox - Env Check"
Write-Host "========================================"
Write-Host ""

$pass = 0; $fail = 0

function Check($name, $ok) {
    if ($ok) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  [FAIL] $name" -ForegroundColor Red; $script:fail++ }
}

$d = & $ADB devices 2>&1
Check "LDPlayer connected" ($d -match "127.0.0.1:5555")

$t = & $ADB -s $DEVICE shell "pm list packages $TARGET_PKG" 2>$null
Check "Twilight installed" ($t -match $TARGET_PKG)

$m = & $ADB -s $DEVICE shell "pm list packages $MODEV_PKG" 2>$null
Check "Modev installed" ($m -match $MODEV_PKG)

$tv = & $ADB -s $DEVICE shell "dumpsys package $TARGET_PKG" 2>$null | Select-String "versionName"
Check "Twilight version found" ($tv -ne $null)

$mp = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $MODEV_PKG
Check "Modev process alive" ($mp -ne $null)

$tp = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $TARGET_PKG
Check "Twilight process alive" ($tp -ne $null)

$sp = & $ADB -s $DEVICE shell "su -c 'ls /data/user/0/$TARGET_PKG/files/modev_spool/$TARGET_PKG/spool.dat'" 2>$null
Check "Spool file exists" ($sp -match "spool.dat")

$db = & $ADB -s $DEVICE shell "su -c 'ls /data/user/0/$MODEV_PKG/databases/modev.db'" 2>$null
Check "Modev DB exists" ($db -match "modev.db")

$root = & $ADB -s $DEVICE shell "su -c id" 2>$null
Check "Root available" ($root -match "uid=0")

$mag = & $ADB -s $DEVICE shell "pm list packages" 2>$null | Select-String "magisk"
Check "Magisk installed" ($mag -ne $null)

$pp = & $ADB -s $DEVICE shell "pm list packages" 2>$null | Select-String "com.network.proxy"
Check "PinProxy installed" ($pp -ne $null)

# Record count
$SQLITE = "D:\Android\platform-tools\sqlite3.exe"
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

Write-Host ""
Write-Host "  Result: $pass passed, $fail failed" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Yellow" })
Write-Host ""
