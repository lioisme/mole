# 22-run-linkage.ps1 — 联动矩阵 C(12 项)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tt-lib.ps1"

Write-Host "== 联动矩阵 Round(C) ==" -ForegroundColor Cyan

# C-GATE-01 结果双闸门截断
# V50:配置复用监听矩阵自己的 `db-all` 档(20-run-listen.ps1:8,该域在矩阵里 5/5 稳定),
# 只额外叠 `maxResultRows:10` —— 手工自拼 kinds 子集在磁盘兜底通道上不稳定
# (同配置多次跑出 0/207 两种结果),不该由一条闸门用例去重新发明。
ResetState '{"dbListen":true,"logStack":false,"kinds":{"OPEN":true,"QUERY":true,"DML":true,"EXEC":true,"TRANSACTION":true},"maxResultRows":10}'
RunCase "db" "L-DB-11" 10
WaitSpoolDrain | Out-Null
# V50 量具修复:原断言查 `rowCount <= 10` —— 但 `rowCount` 的语义是**游标总行数**
# (被动 `mCount` 反射,ADR-6/#11:不得为测量而迭代游标),靶用例插 200 行并全量迭代,
# 所以 rowCount 恒为 200,这条断言自 V45 起就是**恒红的错判据**(V49 台账里那一族
# 「长期红、无人修」的实例)。行闸门的真判据是**被采集的行数** = `rowsJson` 里的行对象数。
$rowsJson = @(Q "SELECT rowsJson FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND operation='QUERY_RESULT' ORDER BY id DESC LIMIT 1;")
$total = @(Q "SELECT rowCount FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND operation='QUERY_RESULT' ORDER BY id DESC LIMIT 1;")
$captured = ([regex]::Matches([string]$rowsJson[0], '\{"c":')).Count
Assert "C-GATE-01 row gate truncates captured rows (<=10)" ($captured -ge 1 -and $captured -le 10) "captured=$captured"
# 被动总行数仍须如实上报 200(证明确实是「采集被截」而非「查询只返 10 行」)
Assert "C-GATE-01 passive cursor total still reported" ("$($total[0])" -match "^200$") "rowCount=$($total[0])"

# C-GATE-02 ignoreStackPrefixes denylist
ResetState '{"cryptoListen":true,"kinds":{"MESSAGE_DIGEST":true},"ignoreStackPrefixes":["com.lioisme.modev.testtarget"]}'
RunCase "crypto" "L-CRY-01" 6
WaitSpoolDrain | Out-Null
$n = KindCount "crypto" "MESSAGE_DIGEST"
Assert "C-GATE-02 stack denylist suppresses" ($n -eq 0) "count=$n"

# C-GATE-03 PathGate 用户前缀
ResetState '{"behaviorListen":true,"kinds":{"FILE_WRITE":true},"ignorePathPrefixes":["/data/user/0/com.lioisme.modev.testtarget/files/tt"]}'
RunCase "behavior" "L-BHV-01" 6
WaitSpoolDrain | Out-Null
$n = KindCount "behavior" "FILE_WRITE"
Assert "C-GATE-03 path denylist suppresses" ($n -eq 0) "count=$n"

# C-THR-01 SessionThrottle 去重
ResetState '{"behaviorListen":true,"kinds":{"FILE_WRITE":true}}'
RunCase "behavior" "L-BHV-12" 6
WaitSpoolDrain | Out-Null
$n = @(Q "SELECT COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND kind='FILE_WRITE' AND path LIKE '%throttle%';")
$cnt = if ($n[0] -match "^\d+$") { [int]$n[0] } else { -1 }
Assert "C-THR-01 throttle dedup (2 writes -> <=1 record)" ($cnt -ge 0 -and $cnt -le 1) "count=$cnt"

# C-SPOOL-01 断线补传
ResetState '{"cryptoListen":true,"kinds":{"MESSAGE_DIGEST":true}}'
AdbShell "am force-stop $script:MODEV_PKG" | Out-Null
Start-Sleep -Seconds 2
RunCase "crypto" "L-CRY-01" 6
$spool = SuShell "ls /data/user/0/$script:TARGET_PKG/files/modev_spool/$script:TARGET_PKG/ 2>/dev/null"
Assert "C-SPOOL-01 spool file while host down" ($spool -match "spool") "ls=$spool"
AdbShell "am start -n $script:MODEV_PKG/.ui.MainActivity" | Out-Null
Start-Sleep -Seconds 8
WaitSpoolDrain | Out-Null
$n = KindCount "crypto" "MESSAGE_DIGEST"
Assert "C-SPOOL-01 drained after host restart" ($n -gt 0) "count=$n"
$spool2 = SuShell "ls /data/user/0/$script:TARGET_PKG/files/modev_spool/$script:TARGET_PKG/ 2>/dev/null"
Assert "C-SPOOL-01 spool cleared post-ACK" ($spool2 -notmatch "spool.dat") "ls=$spool2"

# C-SEQ-01 seq 续号(重启靶进程后不回卷)
$before = @(Q "SELECT MAX(seq) FROM crypto_records WHERE packageName='$script:TARGET_PKG';")
RestartTarget
RunCase "crypto" "L-CRY-01" 6
WaitSpoolDrain | Out-Null
$after = @(Q "SELECT MAX(seq) FROM crypto_records WHERE packageName='$script:TARGET_PKG';")
$mono = ($before[0] -match "^\d+$" -and $after[0] -match "^\d+$" -and [int64]$after[0] -gt [int64]$before[0])
Assert "C-SEQ-01 seq monotonic across restart" $mono "before=$($before[0]) after=$($after[0])"

# C-MULTI-01 多进程
ResetState '{"prefsListen":true,"kinds":{"PUT":true}}'
RunCase "linkage" "C-MULTI-01" 8
WaitSpoolDrain | Out-Null
$n = @(Q "SELECT COUNT(*) FROM crypto_records WHERE packageName='$script:TARGET_PKG' AND processName LIKE '%:sub';")
$cnt = if ($n[0] -match "^\d+$") { [int]$n[0] } else { 0 }
Assert "C-MULTI-01 sub-process records archived" ($cnt -gt 0) "count=$cnt"

# C-OVR-01/02 二态偏差即写即生效 + 恢复默认
ResetState '{"cryptoListen":true,"kinds":{"BASE64":true}}'
RunCase "crypto" "L-CRY-13" 6
WaitSpoolDrain | Out-Null
$n = KindCount "crypto" "BASE64"
Assert "C-OVR-01 deviation effective" ($n -gt 0) "count=$n"
ResetState '{}'
RunCase "crypto" "L-CRY-13" 6
WaitSpoolDrain | Out-Null
$n = KindCount "crypto" "BASE64"
Assert "C-OVR-02 default restored (BASE64 off)" ($n -eq 0) "count=$n"

# C-FB-01 V35 磁盘兜底加载日志
ResetState '{"cryptoListen":true,"kinds":{"BASE64":true}}'
$tt = AdbShell "logcat -d -s HookInstaller"
# V50 量具修复:原断言匹配中文子串「磁盘加载」,而 PowerShell 5.1 解码 adb 文本输出时
# **非 ASCII 字符全成 `?`**(实测 logcat 行显示为 `???????????: NetworkProbe`),
# ⇒ 这条断言永远不可能命中,而日志实际在(`V35` 命中为 True)。改用行内的 ASCII 锚点。
Assert "C-FB-01 disk fallback loaded" ($tt -match "V35.*override") ""
RemoveConfig

# C-EXT-01 externalAccess 默认回环绑定
$listen = SuShell "netstat -tln 2>/dev/null | grep 1510"
Assert "C-EXT-01 default loopback bind" ($listen -match "127\.0\.0\.1:1510") "netstat=$listen"

DumpResults (Join-Path $script:TMP "linkage-round.json")
Exit-WithResult
