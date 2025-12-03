# Launch TTT Server without killing existing Java processes
# This allows Defold to keep running while the server starts
# Auto-detects and uses the newest JAR file

Push-Location "c:\Users\kab10\OneDrive\Documents\GooseHunt\tictactoe\java-server"

Write-Host "Building TTT Server distribution (with dependencies)..." -ForegroundColor Cyan
.\gradlew.bat installDist -x test

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build successful! Using installed distribution..." -ForegroundColor Green
    $distExe = Join-Path "build\install\ttt-server\bin" "ttt-server.bat"

    if (Test-Path $distExe) {
        Write-Host "Launcher: $distExe" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Server starting on port 8081..." -ForegroundColor Yellow
        Write-Host "Press Ctrl+C to stop the server" -ForegroundColor Yellow
        Write-Host ""

        & $distExe
    } else {
        Write-Host "Distribution launcher not found! Expected at $distExe" -ForegroundColor Red
    }
} else {
    Write-Host "Build failed!" -ForegroundColor Red
}

Pop-Location
