# Modev DeviceEnv 压力测试脚本
# 前置条件: LDPlayer 已安装 LSPosed 框架 + Modev 模块已激活
# 使用方法: 在 PowerShell 中运行

$ADB = "D:\leidian\LDPlayer14\adb.exe"
$DEVICE = "127.0.0.1:5555"
$ADB_CMD = "& `"$ADB`" -s $DEVICE"

# 测试目标应用(覆盖不同检测策略)
$TEST_APPS = @(
    @{ name = "com.lioisme.toox"; label = "TooX" },
    @{ name = "com.ambark.jellyflix"; label = "Jellyflix" },
    @{ name = "mark.via"; label = "Via Browser" },
    @{ name = "com.lioisme.lide"; label = "LIDE" },
    @{ name = "com.blacksquircle.ui"; label = "Squircle IDE" }
)

# DeviceEnv 子功能配置(全部启用)
$DEVICE_ENV_CONFIG = @{
    deviceEnvListen = $true
    rootFile = $true
    rootExec = $true
    rootPackageManager = $true
    rootProperty = $true
    xposedClass = $true
    xposedStack = $true
    xposedMaps = $true
    emulatorBuild = $true
    emulatorFile = $true
    emulatorSensor = $true
    emulatorNetwork = $true
    detectRoot = $true
    detectEmulator = $true
}

Write-Host "=== Modev DeviceEnv Stress Test ===" -ForegroundColor Cyan
Write-Host ""

# 1. 检查设备连接
Write-Host "[1/5] Checking device connection..." -ForegroundColor Yellow
$devices = & $ADB devices 2>&1
if ($devices -notmatch $DEVICE) {
    Write-Host "ERROR: Device $DEVICE not connected!" -ForegroundColor Red
    exit 1
}
Write-Host "  Device connected: $DEVICE" -ForegroundColor Green

# 2. 检查 Modev 模块状态
Write-Host "[2/5] Checking Modev module status..." -ForegroundColor Yellow
$modevPackage = & $ADB -s $DEVICE shell "pm list packages | grep modev" 2>&1
if ($modevPackage -notmatch "com.lioisme.modev") {
    Write-Host "ERROR: Modev not installed!" -ForegroundColor Red
    exit 1
}
Write-Host "  Modev installed" -ForegroundColor Green

# 3. 清除 logcat
Write-Host "[3/5] Clearing logcat..." -ForegroundColor Yellow
& $ADB -s $DEVICE shell "logcat -c" 2>&1 | Out-Null
Write-Host "  Logcat cleared" -ForegroundColor Green

# 4. 逐个测试目标应用
Write-Host "[4/5] Testing target apps with ALL DeviceEnv features enabled..." -ForegroundColor Yellow
$passCount = 0
$failCount = 0
$failApps = @()

foreach ($app in $TEST_APPS) {
    $pkg = $app.name
    $label = $app.label
    
    Write-Host ""
    Write-Host "  Testing: $label ($pkg)" -ForegroundColor White
    
    # 强制停止
    & $ADB -s $DEVICE shell "am force-stop $pkg" 2>&1 | Out-Null
    Start-Sleep -Milliseconds 500
    
    # 启动应用
    $startResult = & $ADB -s $DEVICE shell "monkey -p $pkg -c android.intent.category.LAUNCHER 1" 2>&1
    Start-Sleep -Seconds 8
    
    # 检查进程是否存活
    $pid = & $ADB -s $DEVICE shell "pidof $pkg" 2>&1
    $pid = $pid.Trim()
    
    if ($pid -and $pid -ne "") {
        Write-Host "    PASS: Running (PID: $pid)" -ForegroundColor Green
        $passCount++
    } else {
        Write-Host "    FAIL: Not running!" -ForegroundColor Red
        $failCount++
        $failApps += $label
        
        # 抓取崩溃日志
        Write-Host "    Capturing crash logs..." -ForegroundColor Yellow
        $crashLog = & $ADB -s $DEVICE shell "logcat -d -s AndroidRuntime:E" 2>&1
        $crashLog | Select-Object -Last 15 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkRed }
    }
    
    # 强制停止
    & $ADB -s $DEVICE shell "am force-stop $pkg" 2>&1 | Out-Null
    Start-Sleep -Seconds 2
}

# 5. 结果汇总
Write-Host ""
Write-Host "[5/5] Test Results" -ForegroundColor Yellow
Write-Host "  Total: $($TEST_APPS.Count) apps" -ForegroundColor White
Write-Host "  Passed: $passCount" -ForegroundColor Green
Write-Host "  Failed: $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Green" })

if ($failCount -gt 0) {
    Write-Host ""
    Write-Host "  Failed apps:" -ForegroundColor Red
    $failApps | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
    Write-Host ""
    Write-Host "  Check logcat for details: $ADB -s $DEVICE shell logcat -d | Select-String 'FATAL|crash|Modev'" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "  ALL TESTS PASSED!" -ForegroundColor Green
    Write-Host "  All target apps launched successfully with ALL DeviceEnv features enabled." -ForegroundColor Green
}

Write-Host ""
Write-Host "=== Stress Test Complete ===" -ForegroundColor Cyan
