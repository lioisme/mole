# check-spool.ps1 — 检查 Modev Spool 文件状态
# 验证注入侧写入是否正常

param(
    [string]$Package = "com.ucheuhuo.app.apex"
)

$ADB = "D:\leidian\LDPlayer14\adb.exe"
$DEVICE = "127.0.0.1:5555"

Write-Host "`n=== Modev Spool 状态检查 ===" -ForegroundColor Cyan
Write-Host "  目标包: $Package" -ForegroundColor Gray

# 检查 spool 文件
$spoolDir = "/data/user/0/$Package/files/modev_spool/$Package"
$seqFile = "/data/user/0/$Package/files/modev_seq_$Package"

$spoolExists = & $ADB -s $DEVICE shell "su -c 'test -f $spoolDir/spool.dat && echo yes || echo no'" 2>$null
$seqExists = & $ADB -s $DEVICE shell "su -c 'test -f $seqFile && echo yes || echo no'" 2>$null

if ($spoolExists.Trim() -eq "yes") {
    $spoolSize = & $ADB -s $DEVICE shell "su -c 'stat -c %s $spoolDir/spool.dat'" 2>$null
    Write-Host "  [OK] spool.dat 存在 ($($spoolSize.Trim()) bytes)" -ForegroundColor Green
} else {
    Write-Host "  [WARN] spool.dat 不存在" -ForegroundColor Yellow
}

if ($seqExists.Trim() -eq "yes") {
    $seqContent = & $ADB -s $DEVICE shell "su -c 'cat $seqFile'" 2>$null
    Write-Host "  [OK] seq 文件存在, 内容: $($seqContent.Trim())" -ForegroundColor Green
} else {
    Write-Host "  [WARN] seq 文件不存在" -ForegroundColor Yellow
}

# 检查进程是否存活
$modevProc = & $ADB -s $DEVICE shell "ps -A | grep com.lioisme.modev" 2>$null
$targetProc = & $ADB -s $DEVICE shell "ps -A | grep $Package" 2>$null

if ($modevProc) {
    $pid = ($modevProc -split '\s+')[1]
    Write-Host "  [OK] Modev 进程运行中 (PID: $pid)" -ForegroundColor Green
} else {
    Write-Host "  [ERROR] Modev 进程未运行" -ForegroundColor Red
}

if ($targetProc) {
    $pid = ($targetProc -split '\s+')[1]
    Write-Host "  [OK] 目标应用进程运行中 (PID: $pid)" -ForegroundColor Green
} else {
    Write-Host "  [WARN] 目标应用进程未运行" -ForegroundColor Yellow
}

# 检查 Xposed 作用域
$scopeCheck = & $ADB -s $DEVICE shell "su -c 'cat /data/adb/lspd/scope.list 2>/dev/null || ls /data/adb/modules/*/scope 2>/dev/null'" 2>$null
if ($scopeCheck -match $Package) {
    Write-Host "  [OK] $Package 在 Xposed 作用域内" -ForegroundColor Green
} else {
    Write-Host "  [INFO] 作用域状态需手动确认" -ForegroundColor Yellow
}

Write-Host ""
