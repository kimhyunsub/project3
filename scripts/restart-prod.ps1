Param()

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot "target\admin-web-0.0.1-SNAPSHOT.jar"
$outLog = Join-Path $projectRoot "admin-web.out.log"
$errLog = Join-Path $projectRoot "admin-web.err.log"
$restartLog = Join-Path $projectRoot "admin-web.restart.log"

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$FailureMessage
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

Write-Host "==> Admin web production restart started" -ForegroundColor Cyan
Write-Host "Project root: $projectRoot"

Set-Location $projectRoot

if (Test-Path $restartLog) { Remove-Item $restartLog -Force }
Start-Transcript -Path $restartLog -Force

try {

$currentCommit = (git rev-parse HEAD).Trim()
if (-not $currentCommit) {
    throw "Could not resolve current git commit."
}

Write-Host "==> Restarting current commit: $currentCommit" -ForegroundColor Yellow

Write-Host "==> Stopping existing admin-web process" -ForegroundColor Yellow
Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -eq "java.exe" -and
        $_.CommandLine -like "*admin-web-0.0.1-SNAPSHOT.jar*"
    } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force
    }

Start-Sleep -Seconds 2

Write-Host "==> Building jar" -ForegroundColor Yellow
Invoke-Step -Command "mvn" -Arguments @("clean", "package") -FailureMessage "mvn clean package failed."

if (-not (Test-Path $jarPath)) {
    throw "Jar file was not created. Expected path: $jarPath"
}

if (Test-Path $outLog) { Remove-Item $outLog -Force }
if (Test-Path $errLog) { Remove-Item $errLog -Force }

Write-Host "==> Starting admin-web" -ForegroundColor Yellow
Start-Process -FilePath "java" `
    -ArgumentList @(
        "-jar",
        $jarPath,
        "--spring.profiles.active=prod",
        "--server.port=8081",
        "--spring.datasource.url=jdbc:postgresql://localhost:5432/attendance_db",
        "--spring.datasource.username=attendance_user",
        "--spring.datasource.password=change-this-db-password"
    ) `
    -WorkingDirectory $projectRoot `
    -WindowStyle Hidden `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog

Start-Sleep -Seconds 5

$portCheck = netstat -ano | findstr :8081
if ([string]::IsNullOrWhiteSpace($portCheck)) {
    Write-Host "==> Admin web did not start. Check logs:" -ForegroundColor Red
    Write-Host $outLog
    Write-Host $errLog
    exit 1
}

Write-Host "==> Admin web production restart finished" -ForegroundColor Green
Write-Host "Restarted commit: $currentCommit"
Write-Host "Login URL: http://localhost:8081/login"
Write-Host "Output log: $outLog"
Write-Host "Error log: $errLog"
Write-Host "Restart log: $restartLog"
}
finally {
    Stop-Transcript | Out-Null
}
