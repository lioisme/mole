# tt-mcp-lib.ps1 — V38 MCP 集成测试客户端库(在 tt-lib.ps1 之后加载)
# 依赖:tt-lib.ps1 已提供 $script:ADB/$script:DEVICE/WaitDevice/EnsureHost/Assert 等。
# 端口:15110(MCP 默认;adb forward 到宿主回环)。

$script:MCP_PORT = 15110
$script:MCP_URL = "http://127.0.0.1:$($script:MCP_PORT)/mcp"
$script:MCP_SESSION = $null
$script:MCP_ID = 0

# 建立端口转发(幂等;先删除旧的避免端口占用累积)
function McpForward {
    & $script:ADB -s $script:DEVICE forward --remove "tcp:$($script:MCP_PORT)" 2>$null | Out-Null
    & $script:ADB -s $script:DEVICE forward "tcp:$($script:MCP_PORT)" "tcp:$($script:MCP_PORT)" 2>$null | Out-Null
}

# 发送 JSON-RPC(返回 @{status=HTTP状态码; obj=解析后的响应(可能 $null)})
# 自愈(V38):宿主进程无前台服务,后台可被系统回收(LDPlayer 实测)——
# 连接拒绝(-1)或会话失效(404)时拉起宿主 + 重握手 + 重试一次。
function McpRpc([string]$method, [object]$params, [string]$sessionId = $script:MCP_SESSION, [switch]$Notify, [switch]$NoRecover) {
    $script:MCP_ID++
    $body = @{ jsonrpc = "2.0"; method = $method }
    if (-not $Notify) { $body.id = $script:MCP_ID }
    if ($null -ne $params) { $body.params = $params }
    $json = $body | ConvertTo-Json -Depth 12 -Compress
    $r = McpSend $json $sessionId
    if (-not $NoRecover -and ($r.status -eq -1 -or $r.status -eq 404) -and $method -ne "initialize") {
        Write-Host "    [mcp-recover] host/session lost (status=$($r.status)); relaunching + re-initializing" -ForegroundColor DarkCyan
        EnsureHost
        McpForward
        Start-Sleep -Seconds 2
        $null = McpInitialize
        $r = McpSend $json $script:MCP_SESSION
    }
    return $r
}

function McpSend([string]$json, [string]$sessionId) {
    $headers = @{ "Content-Type" = "application/json" }
    if ($sessionId) { $headers["Mcp-Session-Id"] = $sessionId }
    try {
        $resp = Invoke-WebRequest -Uri $script:MCP_URL -Method Post -Body $json -Headers $headers -UseBasicParsing -TimeoutSec 45
        $status = [int]$resp.StatusCode
        $text = $resp.Content
        $sid = $resp.Headers["Mcp-Session-Id"]
        if ($sid) { $script:MCP_SESSION = @($sid)[0] }
    } catch {
        $r = $_.Exception.Response
        if ($null -ne $r) {
            $status = [int]$r.StatusCode
            try { $text = (New-Object IO.StreamReader($r.GetResponseStream())).ReadToEnd() } catch { $text = "" }
            if ($_.Exception.Response.Headers["Mcp-Session-Id"]) { $script:MCP_SESSION = @($_.Exception.Response.Headers["Mcp-Session-Id"])[0] }
        } else {
            return @{ status = -1; obj = $null; text = "$($_.Exception.Message)" }
        }
    }
    $obj = $null
    if ($text) { try { $obj = $text | ConvertFrom-Json } catch { $obj = $null } }
    return @{ status = $status; obj = $obj; text = $text }
}

# initialize + notifications/initialized 握手;返回协议版本
function McpInitialize {
    $r = McpRpc "initialize" @{ protocolVersion = "2025-06-18"; clientInfo = @{ name = "modev-it"; version = "1.0" } } -sessionId $null
    if ($r.status -ne 200) { return $null }
    $null = McpRpc "notifications/initialized" $null -Notify
    return $r.obj.result.protocolVersion
}

# tools/call 捷径(返回 structuredContent PSObject)
# 参数名禁用 $args:与 PowerShell 自动变量冲突(实参不会绑定到该名字,
# 序列化后 arguments 恒为空 → 服务端收到零参数默认执行,V38 集成实锤)。
function McpTool([string]$name, [object]$toolArgs) {
    $r = McpRpc "tools/call" @{ name = $name; arguments = $toolArgs }
    if ($r.status -ne 200 -or $null -eq $r.obj.result) { return $null }
    if ($r.obj.result.isError) {
        Write-Host "    [tool-error] ${name}: $($r.obj.result.content[0].text)" -ForegroundColor DarkYellow
        return $null
    }
    return $r.obj.result.structuredContent
}

# 危险工具确认宏
function McpToolConfirm([string]$name, [object]$toolArgs) {
    $a = @{}
    if ($null -ne $toolArgs) { foreach ($k in $toolArgs.Keys) { $a[$k] = $toolArgs[$k] } }
    $a["confirm"] = $true
    return McpTool $name $a
}

# SQL 计数对账助手:委托 tt-lib 的 CountChecked(纯数字校验 + 重试,杜绝垃圾值假 PASS)
function McpSqlCount([string]$where) {
    return CountChecked "SELECT COUNT(*) FROM crypto_records WHERE $where;"
}
