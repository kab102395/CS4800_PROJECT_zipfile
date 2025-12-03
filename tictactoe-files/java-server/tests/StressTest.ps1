# TicTacToe Stress Testing Script
# Tests multiple concurrent connections to the server

param(
    [int]$NumPlayers = 5,
    [string]$ServerUrl = "http://127.0.0.1:8081"
)

Write-Host "=== TicTacToe Stress Testing ===" -ForegroundColor Green
Write-Host "Testing with $NumPlayers concurrent players" -ForegroundColor Yellow
Write-Host "Server URL: $ServerUrl" -ForegroundColor Yellow

$results = @()
$jobs = @()

# Create multiple concurrent connection jobs
for ($i = 1; $i -le $NumPlayers; $i++) {
    $job = Start-Job -ScriptBlock {
        param($playerNum, $url)
        
        $sessionId = "stress-test-session-$playerNum"
        $playerName = "StressPlayer$playerNum"
        
        try {
            # Test 1: Join Game
            $joinBody = @{
                sessionId = $sessionId
                name = $playerName
            } | ConvertTo-Json
            
            $joinStart = Get-Date
            $joinResponse = Invoke-WebRequest -Uri "$url/api/join" -Method POST -Body $joinBody -ContentType "application/json" -TimeoutSec 10
            $joinTime = (Get-Date) - $joinStart
            
            # Test 2: Get Game State  
            $stateStart = Get-Date
            $stateResponse = Invoke-WebRequest -Uri "$url/api/game-state/$sessionId" -Method GET -ContentType "application/json" -TimeoutSec 10
            $stateTime = (Get-Date) - $stateStart
            
            # Test 3: Get Player Stats
            $statsStart = Get-Date
            $statsResponse = Invoke-WebRequest -Uri "$url/api/stats/$playerName" -Method GET -ContentType "application/json" -TimeoutSec 10
            $statsTime = (Get-Date) - $statsStart
            
            return @{
                Player = $playerNum
                Success = $true
                JoinTime = $joinTime.TotalMilliseconds
                StateTime = $stateTime.TotalMilliseconds  
                StatsTime = $statsTime.TotalMilliseconds
                JoinStatus = $joinResponse.StatusCode
                StateStatus = $stateResponse.StatusCode
                StatsStatus = $statsResponse.StatusCode
                Error = $null
            }
        }
        catch {
            return @{
                Player = $playerNum
                Success = $false
                Error = $_.Exception.Message
                JoinTime = 0
                StateTime = 0
                StatsTime = 0
                JoinStatus = 0
                StateStatus = 0
                StatsStatus = 0
            }
        }
    } -ArgumentList $i, $ServerUrl
    
    $jobs += $job
    Write-Host "Started stress test for Player $i" -ForegroundColor Cyan
}

# Wait for all jobs to complete
Write-Host "Waiting for all tests to complete..." -ForegroundColor Yellow
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

# Analyze results
Write-Host "`n=== Stress Test Results ===" -ForegroundColor Green

$successCount = ($results | Where-Object { $_.Success }).Count
$failureCount = $results.Count - $successCount

Write-Host "Total Players: $($results.Count)" -ForegroundColor White
Write-Host "Successful: $successCount" -ForegroundColor Green  
Write-Host "Failed: $failureCount" -ForegroundColor Red

if ($successCount -gt 0) {
    $avgJoinTime = ($results | Where-Object { $_.Success } | Measure-Object -Property JoinTime -Average).Average
    $avgStateTime = ($results | Where-Object { $_.Success } | Measure-Object -Property StateTime -Average).Average
    $avgStatsTime = ($results | Where-Object { $_.Success } | Measure-Object -Property StatsTime -Average).Average
    
    Write-Host "`nPerformance Metrics:" -ForegroundColor Yellow
    Write-Host "  Average Join Time: $([math]::Round($avgJoinTime, 2)) ms" -ForegroundColor White
    Write-Host "  Average State Time: $([math]::Round($avgStateTime, 2)) ms" -ForegroundColor White
    Write-Host "  Average Stats Time: $([math]::Round($avgStatsTime, 2)) ms" -ForegroundColor White
}

if ($failureCount -gt 0) {
    Write-Host "`nFailure Details:" -ForegroundColor Red
    $results | Where-Object { -not $_.Success } | ForEach-Object {
        Write-Host "  Player $($_.Player): $($_.Error)" -ForegroundColor Red
    }
}

Write-Host "`n=== Stress Test Complete ===" -ForegroundColor Green