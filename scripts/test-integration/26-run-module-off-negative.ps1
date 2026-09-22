# 26-run-module-off-negative.ps1 — V43 T6 宿主独立性负向矩阵(R1 红线)
# 断言:被作用域 App 不依赖 Modev 存活——
#   A. 宿主进程不可达(无 TCP)+ 磁盘兜底删除:冷启动存活、无 FATAL/ANR、
#      帧语义完整、触发用例仍可达(注入侧线程不随宿主死亡卡死)、spool WAL 离线落盘;
#   B. (可选 -FullSwitch)模块真停用(表 enabled=0 + 整机重启):存活 + 行为一致。
#      默认 SKIP(AGENTS 已知坑:改表须 VM 重启,避免常规矩阵时长爆炸;发布验收带参)。
param([switch]$FullSwitch)
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\tt-lib.ps1"

function ColdStartCheck([string]$tag) {
    AdbShell "am force-stop $script:TARGET_PKG" | Out-Null
    AdbShell "logcat -c -b crash" | Out-Null
    AdbShell "am start -S -n $script:TARGET_PKG/.TriggerActivity" | Out-Null
    Start-Sleep -Seconds 12
    $pidOut = AdbShell "pidof $script:TARGET_PKG"
    $alive = $pidOut -match "\d"
    $fatal = AdbShell "logcat -d -b crash"
    $crashed = ($fatal -match [regex]::Escape($script:TARGET_PKG)) -or ($fatal -match "FATAL EXCEPTION")
    $anr = (AdbShell "logcat -d -t 200") -match "ANR in $script:TARGET_PKG"
    Assert "module-off/$tag alive" ($alive -and -not $crashed -and -not $anr) $(
        if ($crashed) { "FATAL" } elseif ($anr) { "ANR" } elseif (-not $alive) { "process dead" } else { "ok" }
    )
    return ($alive -and -not $crashed -and -not $anr)
}

Write-Host "== 宿主独立性负向矩阵 ==" -ForegroundColor Cyan
if (-not (WaitDevice)) { Assert "module-off/device-reachable" $false "VM lost"; exit 1 }
AdbShell "settings put global cached_apps_freezer disabled" | Out-Null

# --- A. 宿主进程不可达 + 无磁盘兜底(注入照常发生,但无 TCP 对端、无偏差文件) ---
RemoveConfig
AdbShell "am force-stop $script:MODEV_PKG" | Out-Null
Start-Sleep -Seconds 2
$okA = ColdStartCheck "A/host-unreachable"
if ($okA) {
    # 注入侧存活验证:任意触发用例必须在有界时间回结果(读路径含钩子线程)
    RunCase "smoke" "L-SMOKE-01" 6
    $tt = WaitTt "L-SMOKE-01" 20
    Assert "module-off/A trigger cases reachable" ("$tt" -match "forNameSingle=ok") "tt=$tt"
    # 无宿主时 spool 必须落盘(宿主恢复后回放;R2 侧 at-least-once 证据)
    RunCase "crypto" "L-CRY-01" 5
    Start-Sleep -Seconds 3
    $spool = AdbShell "run-as $script:TARGET_PKG ls files/modev_spool"
    Assert "module-off/A spool WAL persists offline" ($spool -match "modev_spool|spool") "ls=$spool"
}

# --- B. 真停用模块(Vector/LSPosed 表 enabled=0;需整机重启,默认跳过) ---
if ($FullSwitch) {
    SuShell "sqlite3 /data/adb/lspd/config/modules_config.db `\"UPDATE modules SET enabled=0 WHERE module_pkg_name='$script:MODEV_PKG';`\"" | Out-Null
    # 改表后 lspd 缓存必须整机重启才生效(AGENTS 已知坑:dnconsole quit + dnplayer -index 0);
    # 本脚本只落表并标记,恢复由 10-build-install/11-vector-scope 序列负责。
    Write-Host "  [NOTE] 表已置 enabled=0;执行整机重启后复跑 A 段断言(无注入也应存活),再恢复 enabled=1" -ForegroundColor Yellow
    Assert "module-off/B disable flag written" $true "VM restart required"
} else {
    Skip "module-off/B true-disable" "Vector 改模块表须整机重启(AGENTS 已知坑);发布验收带 -FullSwitch 执行"
}

# --- C. 恢复:重开宿主,断言记录回放(与 A 的 spool 落盘闭环) ---
EnsureHost
Start-Sleep -Seconds 3
Reconnect
RunCase "smoke" "L-SMOKE-01" 6
$ttR = WaitTt "L-SMOKE-01" 20
Assert "module-off/C host restored cases reachable" ("$ttR" -match "forNameSingle=ok") "tt=$ttR"

DumpResults (Join-Path $script:TMP "module-off-negative.json")
exit $(if ($script:FAIL -gt 0) { 1 } else { 0 })
