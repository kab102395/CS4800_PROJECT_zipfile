# Comprehensive WebSocket Communication Battery Test
Write-Host "=== TTT WebSocket Communication Battery Test ===" -ForegroundColor Cyan
Write-Host "Testing: Match creation -> WebSocket registration -> Move handling -> Game state notifications" -ForegroundColor Yellow

$baseUrl = "http://127.0.0.1:8081"
$wsUrl = "ws://127.0.0.1:8080"

# Test 1: Create Match and Check Response
Write-Host "`n[TEST 1] Creating match via HTTP..." -ForegroundColor Magenta
$session1 = "test-session-$(Get-Date -Format 'yyyyMMdd-HHmmss')-001"
$createBody = @{
    sessionId = $session1
    playerName = "TestPlayer1"
    matchName = "Test Match 1"
} | ConvertTo-Json

try {
    $createResponse = Invoke-WebRequest -Uri "$baseUrl/api/create-match" -Method POST -Body $createBody -ContentType "application/json"
    Write-Host "✅ Match creation HTTP status: $($createResponse.StatusCode)" -ForegroundColor Green
    Write-Host "✅ Match creation response: $($createResponse.Content)" -ForegroundColor Green
    
    $matchData = $createResponse.Content | ConvertFrom-Json
    $matchId = $matchData.matchId
    Write-Host "✅ Created match ID: $matchId" -ForegroundColor Green
} catch {
    Write-Host "❌ Match creation failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Test 2: Join Match with Second Player
Write-Host "`n[TEST 2] Joining match with second player..." -ForegroundColor Magenta
$session2 = "test-session-$(Get-Date -Format 'yyyyMMdd-HHmmss')-002"
$joinBody = @{
    sessionId = $session2
    playerName = "TestPlayer2"
    matchId = $matchId
} | ConvertTo-Json

try {
    $joinResponse = Invoke-WebRequest -Uri "$baseUrl/api/join-match" -Method POST -Body $joinBody -ContentType "application/json"
    Write-Host "✅ Match join HTTP status: $($joinResponse.StatusCode)" -ForegroundColor Green
    Write-Host "✅ Match join response: $($joinResponse.Content)" -ForegroundColor Green
} catch {
    Write-Host "❌ Match join failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Test 3: Test Move API (Player 1 - X)
Write-Host "`n[TEST 3] Testing move API for Player 1 (X)..." -ForegroundColor Magenta
$moveBody1 = @{
    sessionId = $session1
    matchId = $matchId
    cell = 4
} | ConvertTo-Json

try {
    $moveResponse1 = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $moveBody1 -ContentType "application/json"
    Write-Host "✅ Move 1 HTTP status: $($moveResponse1.StatusCode)" -ForegroundColor Green
    Write-Host "✅ Move 1 response: $($moveResponse1.Content)" -ForegroundColor Green
    
    $moveData1 = $moveResponse1.Content | ConvertFrom-Json
    if ($moveData1.success -eq $true) {
        Write-Host "✅ Player 1 move successful" -ForegroundColor Green
    } else {
        Write-Host "❌ Player 1 move failed" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Move 1 failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 4: Test Move API (Player 2 - O)
Write-Host "`n[TEST 4] Testing move API for Player 2 (O)..." -ForegroundColor Magenta
$moveBody2 = @{
    sessionId = $session2
    matchId = $matchId
    cell = 0
} | ConvertTo-Json

try {
    $moveResponse2 = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $moveBody2 -ContentType "application/json"
    Write-Host "✅ Move 2 HTTP status: $($moveResponse2.StatusCode)" -ForegroundColor Green
    Write-Host "✅ Move 2 response: $($moveResponse2.Content)" -ForegroundColor Green
} catch {
    Write-Host "❌ Move 2 failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 5: Test Invalid Move (Same Cell)
Write-Host "`n[TEST 5] Testing invalid move (same cell)..." -ForegroundColor Magenta
$invalidMoveBody = @{
    sessionId = $session1
    matchId = $matchId
    cell = 4  # Same cell as first move
} | ConvertTo-Json

try {
    $invalidMoveResponse = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $invalidMoveBody -ContentType "application/json"
    Write-Host "✅ Invalid move HTTP status: $($invalidMoveResponse.StatusCode)" -ForegroundColor Green
    Write-Host "✅ Invalid move response: $($invalidMoveResponse.Content)" -ForegroundColor Green
    
    $invalidMoveData = $invalidMoveResponse.Content | ConvertFrom-Json
    if ($invalidMoveData.success -eq $false) {
        Write-Host "✅ Invalid move correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "❌ Invalid move incorrectly accepted" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Invalid move test failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 6: Test Turn Validation
Write-Host "`n[TEST 6] Testing turn validation (wrong player)..." -ForegroundColor Magenta
$wrongTurnBody = @{
    sessionId = $session1  # Player 1 trying to move again
    matchId = $matchId
    cell = 1
} | ConvertTo-Json

try {
    $wrongTurnResponse = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $wrongTurnBody -ContentType "application/json"
    Write-Host "✅ Wrong turn HTTP status: $($wrongTurnResponse.StatusCode)" -ForegroundColor Green
    Write-Host "✅ Wrong turn response: $($wrongTurnResponse.Content)" -ForegroundColor Green
    
    $wrongTurnData = $wrongTurnResponse.Content | ConvertFrom-Json
    if ($wrongTurnData.success -eq $false) {
        Write-Host "✅ Wrong turn correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "❌ Wrong turn incorrectly accepted" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Wrong turn test failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 7: Complete a Full Game
Write-Host "`n[TEST 7] Playing complete game sequence..." -ForegroundColor Magenta
$moves = @(
    @{ session = $session1; cell = 1; player = "Player1(X)" },
    @{ session = $session2; cell = 2; player = "Player2(O)" },
    @{ session = $session1; cell = 5; player = "Player1(X)" },
    @{ session = $session2; cell = 8; player = "Player2(O)" },
    @{ session = $session1; cell = 3; player = "Player1(X)" }  # Should be winning move (1,5,3 diagonal)
)

foreach ($move in $moves) {
    Write-Host "Moving $($move.player) to cell $($move.cell)..." -ForegroundColor Yellow
    $moveBody = @{
        sessionId = $move.session
        matchId = $matchId
        cell = $move.cell
    } | ConvertTo-Json
    
    try {
        $moveResponse = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $moveBody -ContentType "application/json"
        $moveData = $moveResponse.Content | ConvertFrom-Json
        if ($moveData.success) {
            Write-Host "  ✅ $($move.player) move successful" -ForegroundColor Green
        } else {
            Write-Host "  ❌ $($move.player) move failed" -ForegroundColor Red
        }
    } catch {
        Write-Host "  ❌ $($move.player) move error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 500
}

# Test 8: Check Available Matches
Write-Host "`n[TEST 8] Checking available matches..." -ForegroundColor Magenta
try {
    $matchesResponse = Invoke-WebRequest -Uri "$baseUrl/api/matches" -Method GET
    Write-Host "✅ Matches API status: $($matchesResponse.StatusCode)" -ForegroundColor Green
    Write-Host "✅ Available matches: $($matchesResponse.Content)" -ForegroundColor Green
} catch {
    Write-Host "❌ Matches API failed: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== WebSocket Communication Battery Test Complete ===" -ForegroundColor Cyan
Write-Host "Check the server logs for detailed WebSocket notification debug output!" -ForegroundColor Yellow