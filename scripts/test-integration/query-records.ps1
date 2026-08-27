# query-records.ps1 — Modev 数据库通用查询工具
# 用法:
#   .\query-records.ps1                          # 统计所有记录类型
#   .\query-records.ps1 -RecordType crypto       # 查询算法记录
#   .\query-records.ps1 -RecordType db -Package com.ucheuhuo.app.apex
#   .\query-records.ps1 -RecordType behavior -Last 10
#   .\query-records.ps1 -RecordType device_env -Kind ROOT_FILE

param(
    [string]$RecordType = "",
    [string]$Package = "com.ucheuhuo.app.apex",
    [string]$Kind = "",
    [string]$Operation = "",
    [int]$Last = 0,
    [switch]$CountOnly,
    [switch]$ExportCsv
)

$ADB = "D:\leidian\LDPlayer14\adb.exe"
$SQLITE = "D:\Android\platform-tools\sqlite3.exe"
$DEVICE_DB = "/data/user/0/com.lioisme.modev/databases/modev.db"
$LOCAL_DB = "$env:TEMP\modev_query.db"

# --- 拉取最新数据库 ---
& $ADB -s 127.0.0.1:5555 shell "su -c 'cp $DEVICE_DB /sdcard/modev_query.db'" 2>$null
& $ADB -s 127.0.0.1:5555 pull /sdcard/modev_query.db $LOCAL_DB 2>$null | Out-Null

if (-not (Test-Path $LOCAL_DB)) {
    Write-Host "[ERROR] 无法拉取 Modev 数据库" -ForegroundColor Red
    exit 1
}

function Invoke-Sql($sql) {
    & $SQLITE $LOCAL_DB $sql 2>$null
}

# --- 构建查询 ---
if ($CountOnly -or (-not $RecordType -and -not $Kind -and -not $Operation -and $Last -eq 0)) {
    # 统计模式
    Write-Host "`n=== Modev 记录统计 (Package: $Package) ===" -ForegroundColor Cyan
    $stats = Invoke-Sql "SELECT recordType, kind, COUNT(*) as cnt FROM crypto_records WHERE packageName='$Package' GROUP BY recordType, kind ORDER BY recordType, cnt DESC;"
    if ($stats) {
        $currentType = ""
        foreach ($line in $stats) {
            $parts = $line -split '\|'
            if ($parts.Count -ge 3) {
                $type = $parts[0]; $kind = $parts[1]; $cnt = $parts[2]
                if ($type -ne $currentType) {
                    if ($currentType -ne "") { Write-Host "" }
                    Write-Host "  [$type]" -ForegroundColor Yellow
                    $currentType = $type
                }
                Write-Host "    ${kind}: $cnt" -ForegroundColor Gray
            }
        }
    }
    $total = Invoke-Sql "SELECT COUNT(*) FROM crypto_records WHERE packageName='$Package';"
    Write-Host "`n  总计: $total 条" -ForegroundColor Green
    exit 0
}

# --- 详细查询 ---
$where = @("packageName='$Package'")
if ($RecordType) { $where += "recordType='$RecordType'" }
if ($Kind) { $where += "kind='$Kind'" }
if ($Operation) { $where += "operation='$Operation'" }
$whereClause = $where -join " AND "

$orderClause = "ORDER BY timestamp DESC"
$limitClause = if ($Last -gt 0) { "LIMIT $Last" } else { "LIMIT 50" }

$sql = "SELECT id, recordType, kind, operation, algorithm, timestamp FROM crypto_records WHERE $whereClause $orderClause $limitClause;"

Write-Host "`n=== Modev 记录查询 ===" -ForegroundColor Cyan
Write-Host "  条件: $whereClause" -ForegroundColor Gray

$results = Invoke-Sql $sql
if ($results) {
    Write-Host ""
    Write-Host ("  {0,-8} {1,-12} {2,-20} {3,-25} {4,-15} {5}" -f "ID", "Type", "Kind", "Operation", "Algorithm", "Timestamp") -ForegroundColor White
    Write-Host ("  {0,-8} {1,-12} {2,-20} {3,-25} {4,-15} {5}" -f "---", "----", "----", "---------", "---------", "---------") -ForegroundColor DarkGray
    foreach ($line in $results) {
        $parts = $line -split '\|'
        if ($parts.Count -ge 6) {
            $ts = [DateTimeOffset]::FromUnixTimeMilliseconds([long]$parts[5]).ToLocalTime().ToString("HH:mm:ss.fff")
            Write-Host ("  {0,-8} {1,-12} {2,-20} {3,-25} {4,-15} {5}" -f $parts[0], $parts[1], $parts[2], $parts[3], $parts[4], $ts) -ForegroundColor Gray
        }
    }
    $count = ($results | Measure-Object).Count
    Write-Host "`n  显示 $count 条记录" -ForegroundColor Green
} else {
    Write-Host "  无匹配记录" -ForegroundColor Yellow
}

# --- CSV 导出 ---
if ($ExportCsv -and $results) {
    $csvPath = "D:\Temp\opencode\modev_records_$RecordType.csv"
    $header = "id,recordType,kind,operation,algorithm,timestamp"
    $csvLines = @($header)
    foreach ($line in $results) {
        $csvLines += $line
    }
    $csvLines | Out-File -FilePath $csvPath -Encoding UTF8
    Write-Host "`n  已导出: $csvPath" -ForegroundColor Green
}

# --- 清理 ---
Remove-Item $LOCAL_DB -Force -ErrorAction SilentlyContinue
