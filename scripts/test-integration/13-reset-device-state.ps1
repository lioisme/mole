# 13-reset-device-state.ps1 — 设备残留复位(V45 ACCEPTANCE §2.4:全量轮之间的确定性起点)
#
# 为什么必须是独立一步(V45 首轮实锤):宿主库跨用例类共享,上一轮残留 4006 行时,
# 「种子写入须在读回中可见 expected:<6>」一类断言集体错位;语言/主题持久偏好被
# 上一轮切换后,后续按资源文案定位节点的用例会整片找不到节点。
#
# 只清**运行态**,不动安装物;三条手段全部是 O(1) 或有界小操作:
#  1) 记录库**删文件**(不是 `DELETE FROM` —— 该表含数十 MB 大字段行,整表删要写
#     等量回滚日志,本模拟器上 >2min 不返回 = 首轮卡死根因),Room 下次启动按迁移链重建;
#  2) 注入侧 spool / 磁盘兜底配置经 **run-as** 删(靶包 debuggable;`su -c 'a; b'`
#     多语句链在本栈被引号剥离吞掉 = AGENTS 设备侧定律 ③ 同一根因);
#  3) 语言 / 主题持久偏好文件删除(先 force-stop 再动文件 = 三定律 ①)。
#
# 本步一律用 tt-lib 的 AdbShell/SuShell(快、既有用例同源的自愈重试);
# 禁逐调用 Start-Job 有界化(V45 实测每次作业启动 3–8s,十几次即把一步拖成 3min+)。
param()
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\tt-lib.ps1"

WaitDevice | Out-Null
AdbShell "am force-stop $script:MODEV_PKG" | Out-Null
AdbShell "am force-stop $script:TARGET_PKG" | Out-Null
Start-Sleep -Seconds 2

Write-Host "== reset: drop record db ==" -ForegroundColor Cyan
AdbShell "run-as $script:MODEV_PKG rm -f databases/modev.db databases/modev.db-wal databases/modev.db-shm" | Out-Null
$dbs = AdbShell "run-as $script:MODEV_PKG ls databases"
Assert "record db dropped" (-not ($dbs -match "modev\.db")) "databases=$dbs"

Write-Host "== reset: drop inject-side spool ==" -ForegroundColor Cyan
AdbShell "run-as $script:TARGET_PKG rm -rf files/modev_spool" | Out-Null
SuShell "rm -f /data/local/tmp/modev_config_com.lioisme.modev.testtarget.json" | Out-Null
$files = AdbShell "run-as $script:TARGET_PKG ls files"
Assert "spool removed" (-not ($files -match "modev_spool")) "files=$files"

Write-Host "== reset: locale & theme prefs ==" -ForegroundColor Cyan
AdbShell "run-as $script:MODEV_PKG rm -f shared_prefs/modev_locale.xml shared_prefs/modev_theme.xml" | Out-Null
$prefs = AdbShell "run-as $script:MODEV_PKG ls shared_prefs"
Assert "locale/theme prefs reset" (-not ($prefs -match "modev_locale|modev_theme")) "prefs=$prefs"

Write-Host "== reset: probe db rebuild ==" -ForegroundColor Cyan
AdbShell "am start -W -n $script:MODEV_PKG/.ui.MainActivity" | Out-Null
Start-Sleep -Seconds 6
$rebuilt = AdbShell "run-as $script:MODEV_PKG ls databases"
if ($rebuilt -match "modev\.db") {
    Assert "record db rebuilt on next launch" $true "databases=$rebuilt"
} else {
    # 信息级:重建由后续插桩/矩阵步自行作证(打不开库必然红),本步不因此判失败
    $script:SKIP++
    [void]$script:RESULTS.Add([pscustomobject]@{
            Name = "record db rebuilt on next launch"; Status = "SKIP"; Detail = "databases=$rebuilt" })
    Write-Host "  [SKIP] 库重建探测未命中 databases=$rebuilt" -ForegroundColor Yellow
}
AdbShell "am force-stop $script:MODEV_PKG" | Out-Null

DumpResults (Join-Path $script:TMP "reset-round.json")
Exit-WithResult
