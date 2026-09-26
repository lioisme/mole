# ============================================================================
#  gen-testtarget-certs.ps1 — 重生成 testtarget 的三件 TLS 靶素材
#
#  为什么值得存在:这三件是**提交进仓库的密钥库**,而此前没有任何脚本能再造它们
#  —— 既不可复现,又对贡献者构成「仓库里躺着路不知道哪来的私钥」的观感。
#  它们是**测试替身,不是身份凭证**:口令硬编码 `modevtest`,有效期 10 年,
#  全库无任何用例钉指纹/序列号(已核),因此重生成零波及。
#
#  两枚 p12 的语义差别就是靶点本身,别改:
#    testserver_ip.p12     SAN = dns:localhost + ip:127.0.0.1  ⇒ 主机名校验通过
#    testserver_nohost.p12 SAN = dns:wrong.invalid             ⇒ 主机名校验必失败(E-SSL 靶)
#  @raw/testserver_ip.crt 是 testtarget 的 network_security_config 信任锚,
#  必须与 ip 那枚同一密钥对 ⇒ 由同一次 genkeypair 导出。
#
#  口令/别名从 testtarget/src/main/java/.../NetTargets.kt 的 tlsServerFactory 读取,
#  两处若有一处改动,这里同步(别名对 KeyManagerFactory 是透明的,口令不是)。
# ============================================================================

$ErrorActionPreference = "Stop"
$Root  = Split-Path -Parent $PSScriptRoot
$Assets = Join-Path $Root "testtarget\src\main\assets"
$Raw    = Join-Path $Root "testtarget\src\main\res\raw"

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) { throw "JDK not found. Set JAVA_HOME or put java on PATH." }
$keytool = Join-Path (Split-Path -Parent $javaCmd.Source) "keytool.exe"

$alias = "testserver"
$pass  = "modevtest"

function New-TestKeyPair([string]$P12, [string]$DName, [string]$San) {
    if (Test-Path $P12) { Remove-Item $P12 -Force }
    # keytool -v 把进度写 stderr;PS 5.1 在 EAP=Stop 下会把它当终止错误抛 ⇒ 沿用 build-all.ps1 的 Out-Null 形态
    & $keytool -genkeypair -v -keystore $P12 -storetype PKCS12 `
        -alias $alias -keyalg RSA -keysize 2048 -validity 3650 `
        -storepass $pass -keypass $pass -dname $DName -ext "SAN=$San" -noprompt | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "keytool -genkeypair 失败:$P12" }
}

Write-Host "==> 生成 testserver_ip.p12 + 信任锚 testserver_ip.crt" -ForegroundColor Cyan
$ipP12 = Join-Path $Assets "testserver_ip.p12"
New-TestKeyPair $ipP12 "CN=localhost" "dns:localhost,ip:127.0.0.1"
& $keytool -exportcert -rfc -keystore $ipP12 -storetype PKCS12 -alias $alias `
    -storepass $pass -file (Join-Path $Raw "testserver_ip.crt") | Out-Null
if ($LASTEXITCODE -ne 0) { throw "keytool -exportcert 失败" }

Write-Host "==> 生成 testserver_nohost.p12(SAN 故意不匹配 127.0.0.1)" -ForegroundColor Cyan
New-TestKeyPair (Join-Path $Assets "testserver_nohost.p12") "CN=wrong.invalid" "dns:wrong.invalid"

Write-Host "完成。三件素材已重生成;跑 20-run-listen.ps1 的 E-SSL-* 用例复验语义未变。" -ForegroundColor Green
