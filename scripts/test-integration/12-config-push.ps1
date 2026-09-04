# 12-config-push.ps1 — 独立配置推送工具(V35 磁盘兜底通道)
# 用法: .\12-config-push.ps1 -Json '{"kinds":{"BASE64":true}}'   |  .\12-config-push.ps1 -Clear
param([string]$Json = "", [switch]$Clear)
. "$PSScriptRoot\tt-lib.ps1"

if ($Clear) {
    RemoveConfig
    Write-Host "config cleared"
} else {
    if (-not $Json) { throw "-Json required (or -Clear)" }
    PushConfig $Json
    Write-Host "config pushed: $Json"
}
RestartTarget
Write-Host "target restarted (next launch picks up new config)"
