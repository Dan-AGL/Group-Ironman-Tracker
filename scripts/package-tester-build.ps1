$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$distRoot = Join-Path $repoRoot "build\tester-package"
$bundleRoot = Join-Path $distRoot "gimtracker-tester"
$zipPath = Join-Path $distRoot "gimtracker-tester.zip"

Write-Host "Building runnable tester jar..."
& (Join-Path $repoRoot "gradlew.bat") shadowJar --no-daemon

$jar = Get-ChildItem (Join-Path $repoRoot "build\libs") -Filter "*-all.jar" |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "No runnable shadow jar found in build\\libs."
}

if (Test-Path $distRoot) {
    Remove-Item $distRoot -Recurse -Force
}

New-Item -ItemType Directory -Path $bundleRoot | Out-Null

Copy-Item $jar.FullName (Join-Path $bundleRoot $jar.Name)
Copy-Item (Join-Path $repoRoot "TESTER_SETUP.md") $bundleRoot

$launcher = @'
@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
for %%F in ("%SCRIPT_DIR%*-all.jar") do set "JAR_NAME=%%~nxF"

if not defined JAR_NAME (
  echo Could not find the tester jar in this folder.
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found on PATH.
  echo Install Temurin JDK 21 or 22 and then run this file again.
  pause
  exit /b 1
)

java -ea -jar "%SCRIPT_DIR%%JAR_NAME%" --developer-mode --debug
'@

Set-Content -Path (Join-Path $bundleRoot "launch-plugin.bat") -Value $launcher -NoNewline

Compress-Archive -Path (Join-Path $bundleRoot "*") -DestinationPath $zipPath -Force

Write-Host "Tester package created:"
Write-Host $zipPath
