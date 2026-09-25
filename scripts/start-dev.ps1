param()

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stateDir = Join-Path $repo '.local\dev'
$stateFile = Join-Path $stateDir 'processes.json'
$envFile = Join-Path $repo '.env'
$backendDir = Join-Path $repo 'backend'
$frontendDir = Join-Path $repo 'frontend'

function Test-Port([int]$port) {
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $result = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        $connected = $result.AsyncWaitHandle.WaitOne(700) -and $client.Connected
        $client.Close()
        return $connected
    } catch { return $false }
}

function Test-Http([string]$url) {
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
    } catch { return $false }
}

function Wait-Http([string]$url, [string]$label, [int]$seconds) {
    for ($i = 0; $i -lt $seconds; $i++) {
        if (Test-Http $url) { return }
        Start-Sleep -Seconds 1
    }
    throw "$label 未能在 $seconds 秒内启动，请查看 .local/dev 中的日志。"
}

function Test-OwnedProcess([int]$processId, [string]$start) {
    if (-not $processId -or -not $start) { return $false }
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $process) { return $false }
    return [math]::Abs(($process.StartTime.ToUniversalTime() - [datetime]::Parse($start).ToUniversalTime()).TotalSeconds) -le 2
}

function New-LocalPassword {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw '请先安装并启动 Docker Desktop。' }
docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { throw '请先启动 Docker Desktop。' }

if (-not (Test-Path -LiteralPath $envFile)) {
    $example = [System.IO.File]::ReadAllText((Join-Path $repo '.env.example'), [System.Text.Encoding]::UTF8)
    $example = $example.Replace('replace-with-local-password', (New-LocalPassword))
    $example = $example.Replace('replace-with-another-local-password', (New-LocalPassword))
    [System.IO.File]::WriteAllText($envFile, $example, [System.Text.UTF8Encoding]::new($false))
    Write-Host '已生成本地 .env 和随机数据库密码。架构助手如需使用，可在其中填写三个 AI 配置项。'
}
foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $key = $Matches[1]
        $value = $Matches[2].Trim().Trim('"').Trim("'")
        if ($key -like 'STACKARIUM_*') { [Environment]::SetEnvironmentVariable($key, $value, 'Process') }
    }
}
foreach ($key in @('STACKARIUM_DB_PASSWORD', 'STACKARIUM_DB_ROOT_PASSWORD')) {
    $value = [Environment]::GetEnvironmentVariable($key, 'Process')
    if ([string]::IsNullOrWhiteSpace($value) -or $value.StartsWith('replace-with-')) {
        throw "本地 .env 缺少有效的 $key。"
    }
}

New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
if (Test-Path -LiteralPath $stateFile) {
    $prior = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    if ((Test-OwnedProcess $prior.backendPid $prior.backendStart) -and
        (Test-OwnedProcess $prior.frontendPid $prior.frontendStart) -and
        (Test-Http 'http://127.0.0.1:8080/api/v1/projects') -and
        (Test-Http 'http://127.0.0.1:5173')) {
        Write-Host 'Stackarium 已启动：'
        Write-Host 'http://127.0.0.1:5173'
        return
    }
    & (Join-Path $PSScriptRoot 'stop-dev.ps1') | Out-Null
}
if ((Test-Port 8080) -or (Test-Port 5173)) {
    throw '8080 或 5173 已被其他进程占用。脚本不会停止其他项目。'
}

Push-Location $repo
try {
    docker compose up -d mysql
    if ($LASTEXITCODE -ne 0) { throw '平台 MySQL 启动失败。' }
    $container = (docker compose ps -q mysql).Trim()
    if (-not $container) { throw '平台 MySQL 容器未创建。' }
    $healthy = $false
    for ($i = 0; $i -lt 90; $i++) {
        $health = (docker inspect --format '{{.State.Health.Status}}' $container).Trim()
        if ($health -eq 'healthy') { $healthy = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $healthy) { throw '平台 MySQL 未通过健康检查。' }
} finally { Pop-Location }

if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue)) { throw '找不到 Maven；请先安装 Maven 3.9+。' }
$settings = Join-Path $repo '.local\settings.xml'
$mavenArgs = @('-ntp', '-f', (Join-Path $backendDir 'pom.xml'), '-DskipTests', 'package')
if (Test-Path -LiteralPath $settings) { $mavenArgs = @('-s', $settings) + $mavenArgs }
& mvn.cmd @mavenArgs | Out-File -LiteralPath (Join-Path $stateDir 'build.log') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw '后端打包失败，请查看 .local/dev/build.log。' }

$jar = Get-ChildItem -LiteralPath (Join-Path $backendDir 'target') -Filter 'stackarium-backend-*.jar' |
    Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw '没有找到后端可执行 jar。' }
if (-not (Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules\vite\bin\vite.js'))) {
    if (-not (Get-Command pnpm.cmd -ErrorAction SilentlyContinue)) {
        throw '找不到 pnpm；请先安装 Node.js 和 pnpm。'
    }
    Push-Location $frontendDir
    try {
        & pnpm.cmd install --frozen-lockfile | Out-File -LiteralPath (Join-Path $stateDir 'frontend-install.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败，请查看 .local/dev/frontend-install.log。' }
    } finally { Pop-Location }
}

$backend = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar', $jar.FullName) `
    -WorkingDirectory $backendDir -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $stateDir 'backend.out.log') `
    -RedirectStandardError (Join-Path $stateDir 'backend.err.log')
try { Wait-Http 'http://127.0.0.1:8080/api/v1/projects' '后端' 90 }
catch { Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue; throw }

$frontend = Start-Process -FilePath (Get-Command node.exe).Source `
    -ArgumentList @('node_modules/vite/bin/vite.js', '--host', '127.0.0.1') `
    -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $stateDir 'frontend.out.log') `
    -RedirectStandardError (Join-Path $stateDir 'frontend.err.log')
try { Wait-Http 'http://127.0.0.1:5173' '前端' 40 }
catch {
    Stop-Process -Id $frontend.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue
    throw
}

@{
    backendPid = $backend.Id
    backendStart = (Get-Process -Id $backend.Id).StartTime.ToUniversalTime().ToString('o')
    frontendPid = $frontend.Id
    frontendStart = (Get-Process -Id $frontend.Id).StartTime.ToUniversalTime().ToString('o')
} | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding utf8

Write-Host 'Stackarium 已启动：'
Write-Host 'http://127.0.0.1:5173'
