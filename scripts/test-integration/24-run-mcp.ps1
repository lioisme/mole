# 24-run-mcp.ps1 — V38 MCP 服务集成测试矩阵(任务书 §9.2)
# 前置:雷电模拟器在线;本脚本自建 APK 构建 + 安装 + 作用域 + 端口转发。
# 用法: powershell -File scripts/test-integration/24-run-mcp.ps1
# 断言 22 项:协议 6 + 记录 7 + 配置 2 + 资产 2 + 服务 2 + 应用/作用域/危险 3。

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\tt-lib.ps1"
. "$PSScriptRoot\tt-mcp-lib.ps1"

Write-Host "=== V38 MCP integration matrix ===" -ForegroundColor Cyan

# ---- 环境准备 ----
if (-not (WaitDevice)) { Write-Host "FATAL: device lost"; exit 1 }
Write-Host "[env] installing APKs..."
cmd /c "gradlew.bat :app:assembleModernDebug :testtarget:assembleDebug --console=plain -q" | Out-Null
& $script:ADB -s $script:DEVICE install -r "app\build\outputs\apk\modern\debug\app-modern-x86_64-debug.apk" 2>&1 | Out-Null
& $script:ADB -s $script:DEVICE install -r "testtarget\build\outputs\apk\debug\testtarget-debug.apk" 2>&1 | Out-Null
& $script:ADB -s $script:DEVICE shell "settings put global cached_apps_freezer disabled" 2>$null | Out-Null
EnsureHost
McpForward
Start-Sleep -Seconds 2

# ---- 协议组 ----
$ver = McpInitialize
Assert "M-PROTO-01" ($null -ne $ver -and $ver -eq "2025-06-18") "initialize handshake, protocolVersion=$ver"

$r = McpRpc "tools/list" @{}
$toolNames = @($r.obj.result.tools | ForEach-Object { $_.name })
Assert "M-PROTO-02" ($r.status -eq 200 -and $toolNames.Count -eq 43) "tools/list count=$($toolNames.Count) expect=43"

# 错误语义:未知 method → -32601
$bad = McpRpc "resources/list" @{}
Assert "M-PROTO-03a" ($bad.status -eq 200 -and $bad.obj.error.code -eq -32601) "unknown method -32601"
# 缺参(未知工具) → -32602
$bad2 = McpRpc "tools/call" @{ name = "nope"; arguments = @{} }
Assert "M-PROTO-03b" ($bad2.status -eq 200 -and $bad2.obj.error.code -eq -32602) "unknown tool -32602"
# 坏 JSON → -32700(服务端回 HTTP 200 + JSON-RPC error;PS 5.1 无 -SkipHttpErrorCheck,try/catch 兜底)
try {
    $raw = Invoke-WebRequest -Uri $script:MCP_URL -Method Post -Body '{"jsonrpc":' -Headers @{ "Content-Type" = "application/json"; "Mcp-Session-Id" = $script:MCP_SESSION } -UseBasicParsing -TimeoutSec 15
    $badJson = $raw.Content | ConvertFrom-Json
    $badStatus = [int]$raw.StatusCode
} catch {
    $badJson = $null; $badStatus = -1
    $r = $_.Exception.Response
    if ($null -ne $r) {
        $badStatus = [int]$r.StatusCode
        try { $badJson = ((New-Object IO.StreamReader($r.GetResponseStream())).ReadToEnd() | ConvertFrom-Json) } catch { $badJson = $null }
    }
}
Assert "M-PROTO-03c" ($badStatus -eq 200 -and $badJson.error.code -eq -32700) "malformed json -32700"
# batch → -32600
try {
    $raw2 = Invoke-WebRequest -Uri $script:MCP_URL -Method Post -Body '[{"jsonrpc":"2.0","id":1,"method":"ping"}]' -Headers @{ "Content-Type" = "application/json"; "Mcp-Session-Id" = $script:MCP_SESSION } -UseBasicParsing -TimeoutSec 15
    $batchJson = $raw2.Content | ConvertFrom-Json
    $batchStatus = [int]$raw2.StatusCode
} catch {
    $batchJson = $null; $batchStatus = -1
    $r = $_.Exception.Response
    if ($null -ne $r) {
        $batchStatus = [int]$r.StatusCode
        try { $batchJson = ((New-Object IO.StreamReader($r.GetResponseStream())).ReadToEnd() | ConvertFrom-Json) } catch { $batchJson = $null }
    }
}
Assert "M-PROTO-03d" ($batchStatus -eq 200 -and $batchJson.error.code -eq -32600) "batch -32600"

$ping = McpRpc "ping" @{}
Assert "M-PROTO-04" ($ping.status -eq 200 -and $null -ne $ping.obj.result) "ping"

# 会话失效:无 session 头 → 404(显式 -NoRecover:此组正是断言 404 语义)
$script:MCP_SESSION = $null
$r404 = McpRpc "ping" @{} -sessionId $null -NoRecover
Assert "M-PROTO-05a" ($r404.status -eq 404) "missing session 404"
$null = McpInitialize
$r404b = McpRpc "ping" @{ } -sessionId "dead-session-id" -NoRecover
Assert "M-PROTO-05b" ($r404b.status -eq 404) "stale session 404"
$null = McpInitialize
$okPing = McpRpc "ping" @{}
Assert "M-PROTO-05c" ($okPing.status -eq 200) "re-init recover"

# 回环免 Token(V39 语义:令牌认证默认停用 → 回环无 Token 直通;启用后全接口强制由
# McpServerLifecycleTest 令牌矩阵覆盖,设备侧不驱动 UI 开关)
Assert "M-PROTO-06" ($okPing.status -eq 200) "loopback no-token accepted (token auth off by default)"

# ---- 记录组(种子:RunCase 真实触发) ----
# 环境防线(V38):宿主进程无前台服务,LDPlayer 后台可回收;ResetState 统一
# 推配置→重启靶→起宿主→REGISTRY 暖机(回放旧 spool)→排空→清库,杜绝跨轮脏行;
# WaitChannel 以 server_status.wire.connectedPackages 证注入侧通道建立,
# 杜绝「未连接即断言」的假阴性(记录滞留 spool)。
function WaitChannel([int]$timeoutSec = 60) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $ss = McpTool "server_status" @{}
        if ($null -ne $ss -and (@($ss.wire.connectedPackages) -contains $script:TARGET_PKG)) { return $true }
        EnsureHost
        Start-Sleep -Seconds 2
    }
    return $false
}
$seed = '{"cryptoListen":true,"kinds":{"CIPHER":true,"MESSAGE_DIGEST":true,"MAC":true},"dbListen":true,"kindsQuery":true,"behaviorListen":true,"prefsListen":true,"webviewListen":true,"networkListen":true,"deviceEnvListen":true,"uiListen":true,"systemListen":true,"processControlListen":true,"maxResultRows":200,"maxResultChars":100000}'
ResetState $seed
RunCase "crypto" "L-CRY-01" 5
$chanOk = WaitChannel
Write-Host "  [channel] target connected: $chanOk" -ForegroundColor DarkCyan
RunCase "crypto" "L-CRY-02" 5
RunCase "db" "L-DB-01" 5
RunCase "behavior" "L-BHV-01" 5
RunCase "prefs" "L-PRF-01" 5
EnsureHost
WaitSpoolDrain 90 | Out-Null

$qq = McpTool "records_query" @{ recordType = "crypto"; packageName = $script:TARGET_PKG; pageSize = 100 }
$sqlCount = McpSqlCount "recordType='crypto' AND packageName='$script:TARGET_PKG'"
Assert "M-REC-01" ($null -ne $qq -and $qq.total -eq $sqlCount -and $qq.total -gt 0) "crypto total=$($qq.total) sql=$sqlCount"

$qq2 = McpTool "records_query" @{ recordType = "crypto"; packageName = $script:TARGET_PKG; kind = "CIPHER"; pageSize = 100 }
$sqlCipher = McpSqlCount "recordType='crypto' AND packageName='$script:TARGET_PKG' AND kind='CIPHER'"
Assert "M-REC-02" ($null -ne $qq2 -and $qq2.total -eq $sqlCipher -and $qq2.total -gt 0) "kind=CIPHER total=$($qq2.total) sql=$sqlCipher"

$qq3 = McpTool "records_query" @{ recordType = "behavior"; packageName = $script:TARGET_PKG; pageSize = 200 }
$sqlBeh = McpSqlCount "recordType='behavior' AND packageName='$script:TARGET_PKG'"
Assert "M-REC-03" ($null -ne $qq3 -and $qq3.total -eq $sqlBeh -and $qq3.total -gt 0) "behavior total=$($qq3.total) sql=$sqlBeh"

$qq4 = McpTool "records_query" @{ recordType = "prefs"; packageName = $script:TARGET_PKG; pageSize = 200 }
$sqlPrefs = McpSqlCount "recordType='prefs' AND packageName='$script:TARGET_PKG'"
Assert "M-REC-04" ($null -ne $qq4 -and $qq4.total -eq $sqlPrefs) "prefs total=$($qq4.total) sql=$sqlPrefs"

# 分页:page=0/pageSize=2 vs page=1
$p0 = McpTool "records_query" @{ recordType = "crypto"; packageName = $script:TARGET_PKG; page = 0; pageSize = 2 }
$p1 = McpTool "records_query" @{ recordType = "crypto"; packageName = $script:TARGET_PKG; page = 1; pageSize = 2 }
Assert "M-REC-05" ($p0.items.Count -eq 2 -and $p0.page -eq 0 -and $p0.total -gt 2 -and $p1.items.Count -ge 1 -and $p1.page -eq 1) "pagination p0=$($p0.items.Count) p1=$($p1.items.Count) total=$($p0.total)"

$firstId = $p0.items[0].id
$det = McpTool "record_get" @{ id = $firstId }
Assert "M-REC-06" ($null -ne $det -and $det.id -eq $firstId -and $det.recordType -eq "crypto" -and $null -ne $det.record.algorithm) "record_get id=$firstId"

$nb = McpTool "record_neighbor" @{ id = $firstId; newer = $false }
Assert "M-REC-07a" ($null -ne $nb -and ($null -ne $nb.id)) "record_neighbor older"
$null = McpTool "records_delete_by_filter" @{ packageName = "com.mcp.import.test" }
$mk = McpTool "record_create" @{ packageName = "com.mcp.import.test"; kind = "CIPHER"; operation = "ENCRYPT"; algorithm = "AES/MCP-IMPORT"; threadName = "mcp-it"; recordType = "crypto" }
$cntAfter = McpTool "records_count" @{ recordType = "crypto"; packageName = "com.mcp.import.test" }
Assert "M-REC-07b" ($null -ne $mk.id -and $cntAfter.count -eq 1) "record_create id=$($mk.id)"
$up = McpTool "record_update" @{ id = $mk.id; draft = @{ packageName = "com.mcp.import.test"; kind = "CIPHER"; operation = "DECRYPT"; algorithm = "AES/MCP-EDIT"; threadName = "mcp-it"; recordType = "crypto" } }
$det2 = McpTool "record_get" @{ id = $mk.id }
Assert "M-REC-07c" ($null -ne $up -and $det2.record.algorithm -eq "AES/MCP-EDIT") "record_update"
$del = McpTool "record_delete" @{ id = $mk.id }
$cntDel = McpTool "records_count" @{ recordType = "crypto"; packageName = "com.mcp.import.test" }
Assert "M-REC-07d" ($del.ok -eq $true -and $cntDel.count -eq 0) "record_delete"

# ---- 配置组 ----
# M-CFG-01 闭环:FILE_READ 内置默认 OFF。开(宿主偏差→TCP 推送)→ 记录出现;
# 关(偏差 false)→ 无新增;reset → 偏差清零。
# 注:磁盘兜底偏差优先级高于推送(ConfigChannel global ⊕ deviation),
# 故磁盘文件恒不含 FILE_READ,仅由 MCP 宿主偏差驱动。
$son = McpTool "listen_settings_set" @{ packageName = $script:TARGET_PKG; kindEntries = @{ FILE_READ = $true } }
Assert "M-CFG-01a" ($null -ne $son -and $son.deviationCount -ge 1) "listen set FILE_READ=true devCount=$($son.deviationCount)"
RestartTarget
$null = WaitChannel
$readBase = CountChecked "SELECT COUNT(*) FROM crypto_records WHERE recordType='behavior' AND kind='FILE_READ' AND packageName='$script:TARGET_PKG';"
RunCase "behavior" "L-BHV-07" 5
EnsureHost
WaitSpoolDrain 90 | Out-Null
$readOn = CountChecked "SELECT COUNT(*) FROM crypto_records WHERE recordType='behavior' AND kind='FILE_READ' AND packageName='$script:TARGET_PKG';"
# V38 自愈重试:宿主被回收时记录滞留 spool,重放可能晚于断言窗口——重触发一轮
if ($readOn -le $readBase) {
    Write-Host "  [mcp-retry] FILE_READ count=$readOn base=$readBase; re-running L-BHV-07" -ForegroundColor DarkCyan
    EnsureHost
    RunCase "behavior" "L-BHV-07" 5
    WaitSpoolDrain 90 | Out-Null
    $readOn = CountChecked "SELECT COUNT(*) FROM crypto_records WHERE recordType='behavior' AND kind='FILE_READ' AND packageName='$script:TARGET_PKG';"
}
Assert "M-CFG-01b" ($readOn -gt $readBase) "push path enables FILE_READ count=$readOn (base=$readBase)"
$null = McpTool "listen_settings_set" @{ packageName = $script:TARGET_PKG; kindEntries = @{ FILE_READ = $false } }
RestartTarget
$null = WaitChannel
RunCase "behavior" "L-BHV-07" 5
EnsureHost
WaitSpoolDrain 90 | Out-Null
$readOff = CountChecked "SELECT COUNT(*) FROM crypto_records WHERE recordType='behavior' AND kind='FILE_READ' AND packageName='$script:TARGET_PKG';"
Assert "M-CFG-01c" ($readOff -eq $readOn) "deviation suppresses FILE_READ count=$readOff (was $readOn)"
$sreset = McpTool "listen_settings_reset" @{ packageName = $script:TARGET_PKG }
Assert "M-CFG-01d" ($null -ne $sreset -and $sreset.deviationCount -eq 0) "listen reset"

# M-CFG-02 闭环:EXIT_INTERCEPT 默认 OFF;磁盘基线已含 processControlListen=true。
$fset = McpTool "feature_settings_set" @{ packageName = $script:TARGET_PKG; kindEntries = @{ EXIT_INTERCEPT = $true } }
$fchk = McpTool "feature_settings_get" @{ packageName = $script:TARGET_PKG }
Assert "M-CFG-02a" ($null -ne $fset -and $fset.deviationCount -ge 1 -and $null -ne $fchk.effective) "feature set EXIT_INTERCEPT devCount=$($fset.deviationCount)"
AdbShell "logcat -c" | Out-Null
RunCase "enhance-proc" "E-EXIT-01" 6
$alive = TargetAlive
$tt = GetTt "E-EXIT-01"
Assert "M-CFG-02b" ($alive -and "$tt" -match "survived=true") "E-EXIT-01 alive=$alive tt=$tt"
WaitSpoolDrain 60 | Out-Null
$exitN = KindCount "process" "EXIT_INTERCEPT"
Assert "M-CFG-02c" ($exitN -gt 0) "EXIT_INTERCEPT BLOCKED record count=$exitN"
$null = McpTool "feature_settings_reset" @{ packageName = $script:TARGET_PKG }

# ---- 资产组 ----
$null = McpTool "keywords_add" @{ text = "ModevMcpKeyword01"; matchMode = "CONTAINS" }
$kw = McpTool "keywords_list" @{}
$hit = @($kw.entries | Where-Object { $_.text -eq "ModevMcpKeyword01" })
Assert "M-ASSET-01a" ($hit.Count -eq 1) "keywords_add+list"
$fg = McpTool "feature_settings_get" @{ packageName = $script:TARGET_PKG }
$rules = @($fg.effective.feature.dialogKeywordRules | Where-Object { $_.text -eq "ModevMcpKeyword01" })
Assert "M-ASSET-01b" ($rules.Count -eq 1) "keyword flows to effective rules"
$null = McpTool "keywords_remove" @{ id = $hit[0].id }
$kw2 = McpTool "keywords_list" @{}
Assert "M-ASSET-01c" (@($kw2.entries | Where-Object { $_.text -eq "ModevMcpKeyword01" }).Count -eq 0) "keywords_remove"

$sp = McpTool "spoof_global_set" @{ profileId = "pixel7pro" }
$spl = McpTool "spoof_profiles_list" @{}
Assert "M-ASSET-02a" ($sp.profileId -eq "pixel7pro" -and $spl.currentProfileId -eq "pixel7pro") "spoof set pixel7pro"
$bl = McpTool "blacklist_edit" @{ kind = "rootPackages"; action = "add"; values = @("com.mcp.root.test") }
$bl2 = McpTool "blacklist_list" @{}
Assert "M-ASSET-02b" (@($bl2.extraRootPackages | Where-Object { $_ -eq "com.mcp.root.test" }).Count -eq 1) "blacklist add"
$null = McpTool "blacklist_edit" @{ kind = "rootPackages"; action = "remove"; values = @("com.mcp.root.test") }
$null = McpTool "spoof_global_set" @{ profileId = "auto" }

# ---- 服务组 ----
$ss = McpTool "server_status" @{}
Assert "M-SRV-01a" ($ss.wire.port -ge 1 -and $ss.mcp.port -eq 1510 -and $ss.mcp.enabled -eq $true -and $ss.mcp.tokenAuth -eq $false) "server_status wire+mcp (V39: mcp 1510, token auth off)"
$netstat = SuShell "netstat -tln | grep 1510"
Assert "M-SRV-01b" ("$netstat" -match "127\.0\.0\.1:1510") "netstat mcp 1510 bound"

$diag = McpTool "diagnostics" @{}
Assert "M-SRV-02" ($null -ne $diag.stats -and $diag.report.Count -ge 10) "diagnostics stats+report"

# ---- 应用组 ----
$apps = McpTool "apps_list" @{ query = "testtarget" }
$ttRow = @($apps.apps | Where-Object { $_.packageName -eq $script:TARGET_PKG })
Assert "M-APP-01a" ($ttRow.Count -eq 1 -and $ttRow[0].scoped -eq $true) "apps_list query scoped=$($ttRow[0].scoped)"
$mon = McpTool "app_monitor" @{ packageName = $script:TARGET_PKG }
Assert "M-APP-01b" ($null -ne $mon.counts -and $mon.counts.crypto -ge 0) "app_monitor counts"
$prefs = McpTool "applist_prefs_set" @{ showXposedModules = $true }
$prefs2 = McpTool "applist_prefs_get" @{}
Assert "M-APP-01c" ($prefs2.showXposedModules -eq $true) "applist_prefs roundtrip"
$null = McpTool "applist_prefs_set" @{ showXposedModules = $false }

# ---- 作用域组 ----
# Vector 2.2 不投递 XposedService → 宿主侧 ScopeState 可能为 unavailable(合法形态);
# ready 时必须含 testtarget(11-vector-scope 已配置)。scope_add 依赖系统确认弹窗 → 不驱动。
$sc = McpTool "scope_state" @{}
$scOk = $null -ne $sc -and @("ready", "unavailable", "connecting") -contains $sc.state
if ($sc.state -eq "ready") { $scOk = $scOk -and (@($sc.scopedPackages) -contains $script:TARGET_PKG) }
Assert "M-SCOPE-01" $scOk "scope_state state=$($sc.state)"

# ---- 危险操作 ----
$danger = McpRpc "tools/call" @{ name = "records_clear_all"; arguments = @{} }
Assert "M-DANGER-01a" ($danger.obj.error.code -eq -32602) "clear_all without confirm -32602"
$null = McpToolConfirm "records_clear_all" @{}
$after = McpTool "records_count" @{}
Assert "M-DANGER-01b" ($null -ne $after -and $after.count -eq 0) "clear_all confirm=true wiped all count=$($after.count)"

# ---- 汇总 ----
DumpResults (Join-Path $script:TMP "mcp-round.json")
if ($script:FAIL -gt 0) {
    Write-Host "=== MCP matrix: $($script:PASS) PASS / $($script:FAIL) FAIL / $($script:SKIP) SKIP ===" -ForegroundColor Red
    exit 1
} else {
    Write-Host "=== MCP matrix: $($script:PASS) PASS / 0 FAIL ===" -ForegroundColor Green
    exit 0
}
