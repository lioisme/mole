# 00-env-verify.ps1 - Environment baseline check
# V45 T0.4/BL-4:两类断言分档 —— 编排的 preflight 门禁只能查「静态环境」
# (设备/安装包/版本/root/框架/伴生 App),「运行态」(进程存活/spool/已入库记录)
# 在装包与作用域接线之前必然为假,混在一档会让整轮验收在门口就 BLOCKED。
#   env     = 仅静态环境
#   runtime = 仅运行态(作用域接线后、注入矩阵前)
#   all     = 两档全查(默认,兼容人工单跑)
param([ValidateSet("env", "runtime", "all")][string]$Mode = "all")

. "$PSScriptRoot\tt-env.ps1"
$ADB = $script:ADB
$DEVICE = $script:DEVICE
$TARGET_PKG = Get-ModevEnv "MODEV_TWILIGHT_PKG" "modev.twilightPkg" "com.ucheuhuo.app.apex"
$MODEV_PKG = $script:MODEV_PKG

Write-Host ""
Write-Host "========================================"
Write-Host "  Modev x Twilight Toolbox - Env Check"
Write-Host "========================================"
Write-Host ""

$pass = 0; $fail = 0
# 逐断言台账(V45 QA-1:编排器只认 JSON 台账,「退出码 0 且无台账」按不可判定 = FAIL)
$script:ROWS = New-Object System.Collections.ArrayList

function Check($name, $ok) {
    if ($ok) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  [FAIL] $name" -ForegroundColor Red; $script:fail++ }
    [void]$script:ROWS.Add([ordered]@{
        Id = "ENV-{0:D2}" -f ($script:ROWS.Count + 1)
        Name = $name
        Status = $(if ($ok) { "PASS" } else { "FAIL" })
        Mode = $Mode
    })
}

$d = & $ADB devices 2>&1
Check "LDPlayer connected" ($d -match "127.0.0.1:5555")

$t = & $ADB -s $DEVICE shell "pm list packages $TARGET_PKG" 2>$null
Check "Twilight installed" ($t -match $TARGET_PKG)

$m = & $ADB -s $DEVICE shell "pm list packages $MODEV_PKG" 2>$null
Check "Modev installed" ($m -match $MODEV_PKG)

$tv = & $ADB -s $DEVICE shell "dumpsys package $TARGET_PKG" 2>$null | Select-String "versionName"
Check "Twilight version found" ($tv -ne $null)

if ($Mode -ne "env") {
    # 库文件 = 「应用已运行过」的产物(首启才建),不属静态环境档
    $db = & $ADB -s $DEVICE shell "su -c 'ls /data/user/0/$MODEV_PKG/databases/modev.db'" 2>$null
    Check "Modev DB exists" ($db -match "modev.db")

    $mp = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $MODEV_PKG
    Check "Modev process alive" ($mp -ne $null)

    $tp = & $ADB -s $DEVICE shell "ps -A" 2>$null | Select-String $TARGET_PKG
    Check "Twilight process alive" ($tp -ne $null)

    # V50 实锤:「spool.dat 存在」在这一档**恒不可能为真** —— spool 是「宿主不可达时的
    # 磁盘兜底」(V36 D-1),而本步的 EnsureHost 已经保证宿主在线,记录走 TCP 直落库,
    # 兜底文件自然不存在。断在线态的存在性 = 断言方向写反。
    # 该能力的正档在 `26-run-module-off-negative`(模块关闭/宿主离线时才验 spool 留存),
    # 这里删除而不是「放宽成目录存在」——放宽只会造出第二条永不失败的断言。
}

$root = & $ADB -s $DEVICE shell "su -c id" 2>$null
Check "Root available" ($root -match "uid=0")

$mag = & $ADB -s $DEVICE shell "pm list packages" 2>$null | Select-String "magisk"
Check "Magisk installed" ($mag -ne $null)

$pp = & $ADB -s $DEVICE shell "pm list packages" 2>$null | Select-String "com.network.proxy"
Check "PinProxy installed" ($pp -ne $null)

if ($Mode -ne "env") {
    # Record count —— V50 实锤:旧实装 `cp modev.db` 到 /sdcard 再拉回本地查,而 **Room 是
    # 显式 WAL 的**(AGENTS #18),未 checkpoint 的行全在 `modev.db-wal` 里 ⇒ 单拷主库文件
    # 必然少读甚至读到 0 行,这一条恒红;同一时刻矩阵用 tt-lib 的 `Q`
    # (`run-as … sqlite3 databases/modev.db`)查**活库**却看得见同样这些记录。
    # 修法 = 与 `Q` 同路(设备侧开活库,WAL 自解析)。SQL 里带包名单引号,
    # 照 `Q` 的做法落成 .sql 文件再 `.read`,避开 adb shell 引号地狱。
    $sqlLocal = Join-Path $env:TEMP "modev_env_count.sql"
    [IO.File]::WriteAllText(
        $sqlLocal,
        "SELECT COUNT(*) FROM crypto_records WHERE packageName='$TARGET_PKG';",
        (New-Object System.Text.UTF8Encoding($false)))
    & $ADB -s $DEVICE push $sqlLocal /data/local/tmp/modev_env_count.sql 2>$null | Out-Null
    $cnt = ((& $ADB -s $DEVICE shell "run-as $MODEV_PKG sqlite3 databases/modev.db '.read /data/local/tmp/modev_env_count.sql'" 2>$null |
            ForEach-Object { "$_" }) -join "").Trim()
    if ($cnt -match "^\d+$") {
        Check "Modev has records ($cnt)" ([int]$cnt -gt 0)
    } else {
        Check "Modev has records (unreadable: $cnt)" $false
    }
}

Write-Host ""
Write-Host "  Result: $pass passed, $fail failed" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Yellow" })
Write-Host ""
# 台账目录 = tt-env 的 $script:TMP(%TEMP%\modev-tt),与其余矩阵脚本同一约定
$script:ROWS | ConvertTo-Json | Set-Content -Path (Join-Path $script:TMP "env-round.json") -Encoding UTF8
exit $(if ($fail -gt 0) { 1 } else { 0 })
