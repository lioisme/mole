# 28-wp0-bringup.ps1 — WP-0 设备复位:复用仓库自带 WaitDevice(含 VM 冷自愈),不另起恢复逻辑
# 退出码:0 = 设备可达且启动完成;1 = 超时未恢复
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\tt-lib.ps1"

Write-Host ("ADB={0}" -f $script:ADB)
Write-Host ("ADB_PORT={0} DEVICE={1}" -f $script:ADB_PORT, $script:DEVICE)

$ok = WaitDevice 420
Write-Host ("WaitDevice -> {0}" -f $ok)
if (-not $ok) {
    Write-Host "DEVICE-BRINGUP: FAILED"
    exit 1
}
try {
    $fp = Get-ModevFingerprint
    ($fp | ConvertTo-Json -Depth 4) -split "`n" | ForEach-Object { Write-Host "  $_" }
} catch {
    Write-Host "fingerprint probe unavailable: $_"
}
Write-Host "DEVICE-BRINGUP: OK"
exit 0
