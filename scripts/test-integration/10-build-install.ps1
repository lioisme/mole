# 10-build-install.ps1 — 构建并安装 Modev(modern x86_64 debug)+ ModevTestTarget(debug)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tt-lib.ps1"
$root = Resolve-Path "$PSScriptRoot\..\.."

Write-Host "== gradle assemble ==" -ForegroundColor Cyan
Push-Location $root
cmd /c "gradlew.bat :app:assembleModernDebug :testtarget:assembleDebug --console=plain -q"
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw "assemble failed ($code)" }

Write-Host "== install ==" -ForegroundColor Cyan
$modevApk = Join-Path $root "app\build\outputs\apk\modern\debug\app-modern-x86_64-debug.apk"
$ttApk = Join-Path $root "testtarget\build\outputs\apk\debug\testtarget-debug.apk"
Reconnect
& $script:ADB -s $script:DEVICE install -r $modevApk 2>&1 | Select-Object -Last 1
& $script:ADB -s $script:DEVICE install -r $ttApk 2>&1 | Select-Object -Last 1
AdbShell "pm grant $script:TARGET_PKG android.permission.READ_PHONE_STATE" | Out-Null
AdbShell "am force-stop $script:TARGET_PKG" | Out-Null
Write-Host "install done" -ForegroundColor Green
