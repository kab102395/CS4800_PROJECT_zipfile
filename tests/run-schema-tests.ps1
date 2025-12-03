# Schema validation tests (PowerShell)
# Requires the Java server running on http://localhost:8081 with H2 file DB initialized.
# This script checks presence of expected tables/columns and simple constraints by querying H2 directly via JDBC (using h2*.jar from Gradle cache).

$ErrorActionPreference = "Stop"
$BaseDir = Join-Path $PSScriptRoot ".."
$DbPath = Join-Path $BaseDir "tictactoe/java-server/database/ttt_game"
$H2Jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.h2database\h2\*\*.jar" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "h2-*.jar" -and $_.Name -notlike "*sources*" -and $_.Name -notlike "*.pom" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $H2Jar) { throw "H2 jar not found. Build server first to populate Gradle cache." }

Write-Host "=== Schema Validation (JDBC) ===" -ForegroundColor Cyan
Write-Host "DB: $DbPath" -ForegroundColor Yellow

# Compile/run the Java validator
$javac = "javac"
$java  = "java"
pushd $PSScriptRoot
& $javac -cp $H2Jar.FullName SchemaValidator.java
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
& $java -cp ".;$($H2Jar.FullName)" SchemaValidator
if ($LASTEXITCODE -ne 0) { throw "SchemaValidator failed" }
popd

Write-Host "`nSchema validation completed." -ForegroundColor Green
