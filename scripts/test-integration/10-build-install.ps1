# 10-build-install.ps1 — 构建并安装 Modev(modern x86_64 debug)+ ModevTestTarget(debug)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tt-lib.ps1"
$root = Resolve-Path "$PSScriptRoot\..\.."

Write-Host "== gradle assemble ==" -ForegroundColor Cyan
Push-Location $root
cmd /c "gradlew.bat :app:assembleModernDebug :testtarget:assembleDebug --console=plain -q --no-build-cache"
$code = $LASTEXITCODE
Pop-Location
Assert "assemble modern debug + testtarget" ($code -eq 0) "gradlew 退出码 $code"
if ($script:FAIL -gt 0) {
    # 构建不过 = 无从安装:台账先落盘再退出,编排器据台账判 FAIL(不靠 throw)
    DumpResults (Join-Path $script:TMP "install-round.json")
    Exit-WithResult
}

Write-Host "== install ==" -ForegroundColor Cyan
$modevApk = Join-Path $root "app\build\outputs\apk\modern\debug\app-modern-x86_64-debug.apk"
$ttApk = Join-Path $root "testtarget\build\outputs\apk\debug\testtarget-debug.apk"
Reconnect
# V45 T0.7/QA-1:每步落逐断言台账(编排器只认台账;「退出码 0 且无台账」= 不可判定 = FAIL)
$modevOut = (& $script:ADB -s $script:DEVICE install -r $modevApk 2>&1 | ForEach-Object { "$_" }) -join "`n"
Assert "install modev apk" ($modevOut -match "Success") ("尾部:" + ($modevOut -split "`n" | Select-Object -Last 1))
$ttOut = (& $script:ADB -s $script:DEVICE install -r $ttApk 2>&1 | ForEach-Object { "$_" }) -join "`n"
Assert "install testtarget apk" ($ttOut -match "Success") ("尾部:" + ($ttOut -split "`n" | Select-Object -Last 1))

AdbShell "pm grant $script:TARGET_PKG android.permission.READ_PHONE_STATE" | Out-Null
AdbShell "am force-stop $script:TARGET_PKG" | Out-Null

# 装机回读证据:包在位且 versionName 可读(不信任 install 文本)
$dump = AdbShell "dumpsys package $script:MODEV_PKG"
Assert "modev package readable after install" ($dump -match "versionName") ""
# V45 实锤:`install -r` 报 Success **不等于**设备上的 dex 就是本次构建产物
# (连两轮 DataSettingsScreenTest NoSuchMethodError,设备 dex 落后两个构造器参数)。
# 唯一可信判据 = 产物摘要与设备上 base.apk 摘要逐字节对账。
$localMd5 = (Get-FileHash -Algorithm MD5 -LiteralPath $modevApk).Hash.ToLower()
$apkPath = ((AdbShell "pm path $script:MODEV_PKG") -replace "`r", "" -split "`n" |
    Where-Object { $_ -match "^package:" } | Select-Object -First 1) -replace "^package:", ""
$devMd5 = ""
if ("$apkPath" -ne "") {
    $devMd5 = ((AdbShell "md5sum '$apkPath'") -replace "`r", "" -split "\s+")[0]
}
Assert "installed modev apk digest == build" ($devMd5 -eq $localMd5) "dev='$devMd5' local='$localMd5' path='$apkPath'"
$tdump = AdbShell "dumpsys package $script:TARGET_PKG"
Assert "testtarget package readable after install" ($tdump -match "versionName") ""

DumpResults (Join-Path $script:TMP "install-round.json")
Exit-WithResult
