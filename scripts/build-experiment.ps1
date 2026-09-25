param([string]$MavenSettings = '')

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pom = Join-Path $root 'experiments\spring-cloud-demo\pom.xml'
$arguments = @('-ntp', '-f', $pom)
if ($MavenSettings) { $arguments += @('-s', $MavenSettings) }
$arguments += @('-DskipTests', 'package')
& mvn @arguments
if ($LASTEXITCODE -ne 0) { throw '实验服务构建失败。' }

foreach ($module in @('gateway-service', 'order-service', 'inventory-service', 'notification-consumer')) {
    $jar = Join-Path $root "experiments\spring-cloud-demo\$module\target\$module-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "缺少实验服务 jar：$module" }
}
Write-Host '实验服务已构建，可以启动 Runtime。'
