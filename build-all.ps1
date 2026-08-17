# ============================================================================
#  Modev one-click build: release/debug x arm64-v8a/x86_64  (4 APKs)
#  - No merged/universal APK (ABI splits, universal disabled in build config)
#  - Self-signed with project-local keystores (auto-generated on first run)
#  - Neutral environment: no global tools installs, no global config changes,
#    --no-daemon (no lingering gradle daemon), env vars restored on exit.
#    The only shared resource is the additive ~/.gradle dependency cache,
#    which is safe by design and never affects other projects' builds.
#  Prereq: JDK 17+ and Android SDK (platform android-36, build-tools >=34).
# ============================================================================

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Dist = Join-Path $Root "dist"

Write-Host "==> Modev build (release/debug x arm64-v8a/x86_64)" -ForegroundColor Cyan

# preserve caller's env, restore in finally
$oldJavaHome   = $env:JAVA_HOME
$oldAndroidHome = $env:ANDROID_HOME
$oldAndroidSdk  = $env:ANDROID_SDK_ROOT
$oldLoc = Get-Location

try {
    # ---------- 1. locate JDK ----------
    $javaHome = $oldJavaHome
    if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
        if ($javaCmd) {
            $javaHome = Split-Path -Parent (Split-Path -Parent $javaCmd.Source)
        } else {
            throw "JDK not found. Set JAVA_HOME or put java on PATH."
        }
    }
    $javaBin = Join-Path $javaHome "bin"
    $javaVer = (& cmd /c "`"$javaBin\java.exe`" -version 2>&1" | Select-Object -First 1)
    Write-Host "[1/4] JDK: $javaVer"
    $env:JAVA_HOME = $javaHome  # in-process only

    # ---------- 2. locate Android SDK ----------
    $sdk = $null
    $lp = Join-Path $Root "local.properties"
    if (Test-Path $lp) {
        $line = Get-Content $lp | Where-Object { $_ -match "^sdk\.dir=" } | Select-Object -First 1
        if ($line) {
            $raw = $line.Substring("sdk.dir=".Length)
            $sdk = $raw -replace '\\\\', '\' -replace '\\:', ':' -replace '\\=', '='
        }
    }
    if (-not $sdk) { $sdk = $oldAndroidHome }
    if (-not $sdk) { $sdk = $oldAndroidSdk }
    if (-not $sdk -or -not (Test-Path $sdk)) {
        throw "Android SDK not found. Fix local.properties or set ANDROID_HOME."
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk

    if (-not (Test-Path (Join-Path $sdk "platforms\android-36"))) {
        throw "Missing SDK platform android-36. Install via SDK Manager (script keeps env neutral and does not auto-install)."
    }
    $buildTools = $null
    foreach ($v in @("36.0.0", "35.0.1", "35.0.0", "34.0.0")) {
        if (Test-Path (Join-Path $sdk "build-tools\$v")) { $buildTools = Join-Path $sdk "build-tools\$v"; break }
    }
    if (-not $buildTools) { throw "Missing Android build-tools (>=34). Install via SDK Manager." }
    $apksigner = Join-Path $buildTools "apksigner.bat"
    Write-Host "[2/4] SDK: $sdk | build-tools: $(Split-Path $buildTools -Leaf)"

    # ---------- 3. project-local signing keys (auto-generate once) ----------
    $keystoreDir = Join-Path $Root "keystore"
    $propsFile = Join-Path $keystoreDir "keystore.properties"
    if (-not (Test-Path $propsFile)) {
        New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
        $relAlias = "modev"
        $dbgAlias  = "modev-debug"
        $relPass = [System.Guid]::NewGuid().ToString("N").Substring(0, 24)
        $dbgPass = [System.Guid]::NewGuid().ToString("N").Substring(0, 24)
        $keytool = Join-Path $javaBin "keytool.exe"
        & $keytool -genkeypair -v -keystore (Join-Path $keystoreDir "release.keystore") `
            -alias $relAlias -keyalg RSA -keysize 2048 -validity 10000 `
            -storepass $relPass -keypass $relPass `
            -dname "CN=Modev,OU=Dev,O=Lioisme,L=Unknown,ST=Unknown,C=CN" -noprompt | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Failed to create release keystore" }
        & $keytool -genkeypair -v -keystore (Join-Path $keystoreDir "debug.keystore") `
            -alias $dbgAlias -keyalg RSA -keysize 2048 -validity 10000 `
            -storepass $dbgPass -keypass $dbgPass `
            -dname "CN=Modev Debug,OU=Dev,O=Lioisme,L=Unknown,ST=Unknown,C=CN" -noprompt | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Failed to create debug keystore" }
        @"
storeFile=keystore/release.keystore
storePassword=$relPass
keyAlias=$relAlias
keyPassword=$relPass
debugStoreFile=keystore/debug.keystore
debugStorePassword=$dbgPass
debugKeyAlias=$dbgAlias
debugKeyPassword=$dbgPass
"@ | Set-Content -Path $propsFile -Encoding ascii
        Write-Host "[3/4] Project-local keystores created. Credentials: $propsFile (back it up; never commit to VCS)" -ForegroundColor Yellow
    } else {
        Write-Host "[3/4] Using existing signing config: $propsFile"
    }

    # ---------- 4. build (ABI splits produce 4 single-ABI APKs) ----------
    # modern 为默认双框架变体;只构建 modern,避免 8 个 APK
    Write-Host "[4/4] Building modern:assembleRelease + modern:assembleDebug (--no-daemon) ..."
    Push-Location $Root
    & (Join-Path $Root "gradlew.bat") :app:assembleModernRelease :app:assembleModernDebug --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit=$LASTEXITCODE)" }
    Pop-Location

    # ---------- collect & verify ----------
    $ver = $null
    $vm = Select-String -Path (Join-Path $Root "app\build.gradle.kts") -Pattern 'versionName\s*=\s*"([^"]+)"'
    if ($vm) { $ver = $vm.Matches[0].Groups[1].Value }
    if (-not $ver) { $ver = "0.1.0" }

    New-Item -ItemType Directory -Force -Path $Dist | Out-Null
    $map = [ordered]@{
        "app\build\outputs\apk\modern\release\app-modern-arm64-v8a-release.apk" = "Modev-${ver}-release-arm64-v8a.apk"
        "app\build\outputs\apk\modern\release\app-modern-x86_64-release.apk"    = "Modev-${ver}-release-x86_64.apk"
        "app\build\outputs\apk\modern\debug\app-modern-arm64-v8a-debug.apk"     = "Modev-${ver}-debug-arm64-v8a.apk"
        "app\build\outputs\apk\modern\debug\app-modern-x86_64-debug.apk"        = "Modev-${ver}-debug-x86_64.apk"
    }

    $results = @()
    foreach ($rel in $map.Keys) {
        $src = Join-Path $Root $rel
        if (-not (Test-Path $src)) { throw "Missing APK: $src" }
        $dst = Join-Path $Dist $map[$rel]
        Copy-Item -Path $src -Destination $dst -Force
        $sizeMB = [math]::Round((Get-Item $dst).Length / 1MB, 2)
        $verified = $false
        $sign = "n/a"
        if (Test-Path $apksigner) {
            $certs = (& cmd /c "`"$apksigner`" verify --print-certs `"$dst`" 2>&1")
            if ($LASTEXITCODE -eq 0) {
                $verified = $true
                $dn = ($certs | Where-Object { $_ -match "Signer #1 certificate DN:" }) -join " "
                $sign = "OK ($dn)"
            } else {
                $sign = "FAIL"
            }
        }
        $results += "  [$(if ($verified) {'OK'} else {'FAIL'})] $($map[$rel])  (${sizeMB} MB)  sign=$sign"
    }

    Write-Host ""
    Write-Host "==> Build complete. APKs in: $Dist" -ForegroundColor Green
    $results | ForEach-Object { Write-Host $_ }
} finally {
    Pop-Location -ErrorAction SilentlyContinue
    if ($null -eq $oldJavaHome) { Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue } else { $env:JAVA_HOME = $oldJavaHome }
    if ($null -eq $oldAndroidHome) { Remove-Item Env:ANDROID_HOME -ErrorAction SilentlyContinue } else { $env:ANDROID_HOME = $oldAndroidHome }
    if ($null -eq $oldAndroidSdk) { Remove-Item Env:ANDROID_SDK_ROOT -ErrorAction SilentlyContinue } else { $env:ANDROID_SDK_ROOT = $oldAndroidSdk }
}