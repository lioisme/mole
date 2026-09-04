# run-all.ps1 - Full regression test for Modev x Twilight Toolbox
# Usage: powershell -ExecutionPolicy Bypass -File run-all.ps1

$ADB = "D:\leidian\LDPlayer14\adb.exe"
$SQLITE = "D:\Android\platform-tools\sqlite3.exe"
$DEVICE = "127.0.0.1:5555"
$TARGET_PKG = "com.ucheuhuo.app.apex"
$MODEV_PKG = "com.lioisme.modev"
$PASS = 0; $FAIL = 0; $WARN = 0

Write-Host ""
Write-Host "================================================================"
Write-Host "  Modev Full Integration Regression Test"
Write-Host "  Target: Twilight Toolbox ($TARGET_PKG)"
Write-Host "  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "================================================================"
Write-Host ""

function Assert($name, $ok, $detail = "") {
    if ($ok) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:PASS++ }
    else { Write-Host "  [FAIL] $name $detail" -ForegroundColor Red; $script:FAIL++ }
}

function Warn($name, $detail = "") {
    Write-Host "  [WARN] $name $detail" -ForegroundColor Yellow; $script:WARN++
}

# --- Helper: Pull DB ---
function Pull-ModevDB($outPath) {
    & $ADB -s $DEVICE shell "su -c 'cp /data/user/0/$MODEV_PKG/databases/modev.db /sdcard/modev_test.db'" 2>$null
    & $ADB -s $DEVICE pull /sdcard/modev_test.db $outPath 2>$null | Out-Null
    return (Test-Path $outPath)
}

function QueryDB($dbPath, $sql) {
    & $SQLITE $dbPath $sql 2>$null
}

# ============================================
# Section 1: Environment Checks
# ============================================
Write-Host "--- Section 1: Environment ---" -ForegroundColor Cyan

$d = & $ADB devices 2>&1
Assert "LDPlayer connected" ($d -match "127.0.0.1:5555")

$t = & $ADB -s $DEVICE shell "pm list packages $TARGET_PKG" 2>$null
Assert "Twilight installed" ($t -match $TARGET_PKG)

$m = & $ADB -s $DEVICE shell "pm list packages $MODEV_PKG" 2>$null
Assert "Modev installed" ($m -match $MODEV_PKG)

$mp = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $MODEV_PKG
Assert "Modev process alive" ($mp -ne $null)

$sp = & $ADB -s $DEVICE shell "su -c 'ls /data/user/0/$TARGET_PKG/files/modev_spool/$TARGET_PKG/spool.dat'" 2>$null
Assert "Spool file exists" ($sp -match "spool.dat")

$root = & $ADB -s $DEVICE shell "su -c id" 2>$null
Assert "Root available" ($root -match "uid=0")

# ============================================
# Section 2: Pull & Query Database
# ============================================
Write-Host "`n--- Section 2: Database Records ---" -ForegroundColor Cyan

$dbPath = "$env:TEMP\modev_regression.db"
if (Pull-ModevDB $dbPath) {
    Assert "DB pull successful" $true
    
    # Total records for target
    $total = QueryDB $dbPath "SELECT COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG';"
    $totalVal = if ($total) { $total.Trim() } else { "0" }
    Assert "Target has records ($totalVal)" ([int]$totalVal -gt 0)
    
    # Device env records (the fixed probe)
    $devEnv = QueryDB $dbPath "SELECT COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG' AND recordType='device_env';"
    $devEnvVal = if ($devEnv) { $devEnv.Trim() } else { "0" }
    Assert "DeviceEnv records exist ($devEnvVal)" ([int]$devEnvVal -gt 0)
    
    # Device env kinds
    $devKinds = QueryDB $dbPath "SELECT kind, COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG' AND recordType='device_env' GROUP BY kind;"
    if ($devKinds) {
        foreach ($line in $devKinds) {
            $parts = $line -split '\|'
            if ($parts.Count -ge 2) {
                Assert "DeviceEnv kind: $($parts[0]) ($($parts[1]) records)" ([int]$parts[1] -gt 0)
            }
        }
    }
    
    # Network records
    $net = QueryDB $dbPath "SELECT COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG' AND recordType='network';"
    $netVal = if ($net) { $net.Trim() } else { "0" }
    if ([int]$netVal -gt 0) {
        Assert "Network records exist ($netVal)" $true
        $netKinds = QueryDB $dbPath "SELECT kind, operation, COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG' AND recordType='network' GROUP BY kind, operation;"
        if ($netKinds) {
            foreach ($line in $netKinds) {
                $parts = $line -split '\|'
                if ($parts.Count -ge 3) {
                    Assert "Network: $($parts[0])/$($parts[1]) ($($parts[2]) records)" ([int]$parts[2] -gt 0)
                }
            }
        }
    } else {
        Warn "No network records (app may not make network calls)"
    }
    
    # Cross-app probe verification (mark.via has all probe types)
    $via_total = QueryDB $dbPath "SELECT COUNT(*) FROM crypto_records WHERE packageName='mark.via';"
    $viaVal = if ($via_total) { $via_total.Trim() } else { "0" }
    Assert "mark.via has records for cross-probe validation ($viaVal)" ([int]$viaVal -gt 0)
    
    $probeTypes = @("crypto", "db", "behavior", "prefs", "webview")
    foreach ($pt in $probeTypes) {
        $cnt = QueryDB $dbPath "SELECT COUNT(*) FROM crypto_records WHERE packageName='mark.via' AND recordType='$pt';"
        $cntVal = if ($cnt) { $cnt.Trim() } else { "0" }
        Assert "Probe $pt has records ($cntVal)" ([int]$cntVal -gt 0)
    }
    
    # Record quality checks
    Write-Host "`n--- Section 3: Record Quality ---" -ForegroundColor Cyan
    
    # Check no null required fields
    $nullKinds = QueryDB $dbPath "SELECT COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG' AND (kind IS NULL OR kind='');"
    $nullKindsVal = if ($nullKinds) { $nullKinds.Trim() } else { "0" }
    Assert "No null kind fields" ([int]$nullKindsVal -eq 0)
    
    $nullOps = QueryDB $dbPath "SELECT COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG' AND (operation IS NULL OR operation='');"
    $nullOpsVal = if ($nullOps) { $nullOps.Trim() } else { "0" }
    Assert "No null operation fields" ([int]$nullOpsVal -eq 0)
    
    $nullTs = QueryDB $dbPath "SELECT COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG' AND timestamp=0;"
    $nullTsVal = if ($nullTs) { $nullTs.Trim() } else { "0" }
    Assert "No zero timestamps" ([int]$nullTsVal -eq 0)
    
    Remove-Item $dbPath -Force -ErrorAction SilentlyContinue
} else {
    Assert "DB pull successful" $false "Cannot pull database"
}

# ============================================
# Section 4: Process Stability
# ============================================
Write-Host "`n--- Section 4: Stability ---" -ForegroundColor Cyan

# Check Modev not crashed
$mpAfter = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $MODEV_PKG
Assert "Modev still alive after checks" ($mpAfter -ne $null)

# Check target not crashed
$tpAfter = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $TARGET_PKG
Assert "Target still alive after checks" ($tpAfter -ne $null)

# Check no recent crashes
$crashes = & $ADB -s $DEVICE shell "logcat -d -t 60" 2>$null | Select-String "FATAL|AndroidRuntime" | Select-String "$TARGET_PKG|$MODEV_PKG"
Assert "No recent crashes" ($crashes -eq $null)

# ============================================
# Summary
# ============================================
Write-Host ""
Write-Host "================================================================"
Write-Host "  Results: $PASS passed, $FAIL failed, $WARN warnings"
if ($FAIL -eq 0) {
    Write-Host "  Status: ALL TESTS PASSED" -ForegroundColor Green
} else {
    Write-Host "  Status: $FAIL TESTS FAILED" -ForegroundColor Red
}
Write-Host "================================================================"
Write-Host ""
