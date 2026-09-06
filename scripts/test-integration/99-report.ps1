# 99-report.ps1 — 汇总各轮结果 → docs/test-reports/round-N.md
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tt-lib.ps1"

$files = [ordered]@{
    "监听矩阵 L"   = Join-Path $script:TMP "listen-round.json"
    "增强矩阵 E"   = Join-Path $script:TMP "enhance-round.json"
    "联动矩阵 C"   = Join-Path $script:TMP "linkage-round.json"
    "冷启动冒烟 S" = Join-Path $script:TMP "smoke-round.json"
    "MCP 矩阵 M"   = Join-Path $script:TMP "mcp-round.json"
}
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reports = Resolve-Path "$PSScriptRoot\..\..\docs\test-reports" -ErrorAction SilentlyContinue
if (-not $reports) { New-Item -ItemType Directory -Force -Path "docs\test-reports" | Out-Null; $reports = "docs\test-reports" }
$out = Join-Path $PWD "docs\test-reports\round-$stamp.md"

$lines = @("# Modev 全功能验收报告 — $stamp", "")
$totP = 0; $totF = 0; $totS = 0
foreach ($k in $files.Keys) {
    $f = $files[$k]
    $lines += "## $k"
    if (-not (Test-Path $f)) { $lines += "_未执行_`n"; continue }
    $items = Get-Content $f -Raw | ConvertFrom-Json
    $p = ($items | Where-Object Status -eq "PASS").Count
    $fl = ($items | Where-Object Status -eq "FAIL").Count
    $sk = ($items | Where-Object Status -eq "SKIP").Count
    $totP += $p; $totF += $fl; $totS += $sk
    $lines += "PASS=$p FAIL=$fl SKIP=$sk`n"
    $lines += "| 状态 | 用例 | 详情 |"; $lines += "|---|---|---|"
    foreach ($i in $items) {
        $icon = switch ($i.Status) { "PASS" { "✅" } "FAIL" { "❌" } default { "⏭️" } }
        $lines += "| $icon $($i.Status) | $($i.Name) | $($i.Detail) |"
    }
    $lines += ""
}
$denom = $totP + $totF
$rate = if ($denom -gt 0) { [math]::Round(100.0 * $totP / $denom, 1) } else { 0 }
$lines += "## 总计", "PASS=$totP FAIL=$totF SKIP=$totS 符合率=$rate%(不含 SKIP)", ""
Set-Content -Path $out -Value ($lines -join "`n") -Encoding UTF8
Write-Host "报告 → $out (符合率 $rate%)"
