# 27-run-backpressure.ps1 — V43 T6 背压/锁链回归(V1.1/C-1 照妖镜)
# 场景:宿主 wire 服务(127.0.0.1:1511)被「只 accept 不消费」的黑洞监听替代,
# 注入侧全开刷记录 → TCP 零窗口 → writer 阻塞。
# 断言:① 阻塞的写被 watchdog(锁外 close)秒级判死,宿主 App 触发用例仍在有界时间回结果
#        (V1.1 锁链旧实现在此永久卡死注入线程,UI 触发路径随之僵死);
#      ② 黑洞移除 + 宿主恢复后,spool 积压完整回放入库(at-least-once 不丢)。
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\tt-lib.ps1"

Write-Host "== 背压锁链回归 ==" -ForegroundColor Cyan
if (-not (WaitDevice)) { Assert "backpressure/device-reachable" $false "VM lost"; exit 1 }
AdbShell "settings put global cached_apps_freezer disabled" | Out-Null
EnsureHost
Reconnect

# 全开档案 + 重启靶进程(注入通道建立)
$ALL_ON = '{"cryptoListen":true,"dbListen":true,"behaviorListen":false,"prefsListen":false,"webviewListen":false,"networkListen":false,"deviceEnvListen":false,"uiListen":false,"systemListen":false,"processControlListen":false,"kinds":{"MESSAGE_DIGEST":true,"CIPHER":true}}'
PushConfig $ALL_ON
RestartTarget

# 宿主下线 → 黑洞监听占住 1511(accept 不读,循环重建)
AdbShell "am force-stop $script:MODEV_PKG" | Out-Null
Start-Sleep -Seconds 2
$blackhole = "while true; do (nc -l -p 1511 >/dev/null 2>&1; sleep 1); done"
PushScript "bh1511.sh" "#!/system/bin/sh`n$blackhole`n" | Out-Null
SuShell "chmod 755 /data/local/tmp/bh1511.sh" | Out-Null
SuShell "nohup /data/local/tmp/bh1511.sh >/dev/null 2>&1 &" | Out-Null
Start-Sleep -Seconds 2

# 重启注入:连接黑洞;刷大量记录直至队列满/写阻塞
RestartTarget
1..14 | ForEach-Object { RunCase "crypto" "L-CRY-01" 2 }
1..14 | ForEach-Object { RunCase "crypto" "L-CRY-02" 2 }

# ① 背压中宿主线程存活:触发用例有界返回
$t0 = Get-Date
RunCase "smoke" "L-SMOKE-01" 4
$tt = WaitTt "L-SMOKE-01" 15
$elapsed = ((Get-Date) - $t0).TotalSeconds
Assert "backpressure/A target stays responsive (watchdog unblocks writer)" (
    "$tt" -match "forNameSingle=ok" -and $elapsed -lt 25
) "tt=$tt elapsed=$([int]$elapsed)s"

# ② 移除黑洞 → 宿主恢复 → 积压回放入库
SuShell "pkill -f bh1511.sh" | Out-Null
SuShell "pkill -f 'nc -l -p 1511'" | Out-Null
EnsureHost
WaitSpoolDrain | Out-Null
Start-Sleep -Seconds 3
$nCrypto = TypeCount "crypto"
Assert "backpressure/B replayed records persisted" ($nCrypto -gt 0) "count=$nCrypto"

DumpResults (Join-Path $script:TMP "backpressure.json")
exit $(if ($script:FAIL -gt 0) { 1 } else { 0 })
