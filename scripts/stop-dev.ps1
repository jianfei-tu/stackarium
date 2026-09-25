param([switch]$StopDatabase)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stateFile = Join-Path $repo '.local\dev\processes.json'

function Stop-OwnedProcess([int]$processId, [string]$start, [string]$label) {
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $process) { Write-Host "$label 已停止。"; return }
    $actual = $process.StartTime.ToUniversalTime()
    $recorded = [datetime]::Parse($start).ToUniversalTime()
    if ([math]::Abs(($actual - $recorded).TotalSeconds) -gt 2) {
        Write-Warning "$label PID 已被其他进程复用，未停止。"
        return
    }
    Stop-Process -Id $processId -Force
    Write-Host "$label 已停止。"
}

if (Test-Path -LiteralPath $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    Stop-OwnedProcess $state.frontendPid $state.frontendStart 'Stackarium 前端'
    Stop-OwnedProcess $state.backendPid $state.backendStart 'Stackarium 后端'
    Remove-Item -LiteralPath $stateFile
} else {
    Write-Host '没有由脚本记录的 Stackarium 开发进程。'
}

if ($StopDatabase) {
    Push-Location $repo
    try {
        docker compose stop mysql
        if ($LASTEXITCODE -ne 0) { throw '平台 MySQL 停止失败。' }
        Write-Host '平台 MySQL 已停止。'
    } finally { Pop-Location }
} else {
    Write-Host '平台 MySQL 保持运行。'
}
