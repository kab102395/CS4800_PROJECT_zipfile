# WebSocket Notification Debug Test
Write-Host "=== WebSocket Notification Flow Test ===" -ForegroundColor Cyan

$baseUrl = "http://127.0.0.1:8081"

# Create two test sessions
$session1 = "debug-session-1-$(Get-Date -Format 'HHmmss')"
$session2 = "debug-session-2-$(Get-Date -Format 'HHmmss')"

Write-Host "Test Sessions: $session1, $session2" -ForegroundColor Yellow

# Step 1: Create match
Write-Host "`n[STEP 1] Creating match with session: $session1" -ForegroundColor Magenta
$createBody = @{
    sessionId = $session1
    playerName = "DebugPlayer1"
    matchName = "Debug Match"
} | ConvertTo-Json

$createResponse = Invoke-WebRequest -Uri "$baseUrl/api/create-match" -Method POST -Body $createBody -ContentType "application/json"
$matchData = $createResponse.Content | ConvertFrom-Json
$matchId = $matchData.matchId
Write-Host "Created match: $matchId" -ForegroundColor Green

# Step 2: Join match
Write-Host "`n[STEP 2] Joining match with session: $session2" -ForegroundColor Magenta
$joinBody = @{
    sessionId = $session2
    playerName = "DebugPlayer2"
    matchId = $matchId
} | ConvertTo-Json

$joinResponse = Invoke-WebRequest -Uri "$baseUrl/api/join-match" -Method POST -Body $joinBody -ContentType "application/json"
Write-Host "Joined match successfully" -ForegroundColor Green

# Step 3: Wait a moment for WebSocket registration (simulate client behavior)
Write-Host "`n[STEP 3] Waiting for WebSocket registration simulation..." -ForegroundColor Magenta
Start-Sleep -Seconds 2

# Step 4: Make first move (should trigger notifyGameState)
Write-Host "`n[STEP 4] Making first move (Player 1 - X, cell 4)" -ForegroundColor Magenta
$moveBody = @{
    sessionId = $session1
    matchId = $matchId
    cell = 4
} | ConvertTo-Json

$moveResponse = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $moveBody -ContentType "application/json"
$moveData = $moveResponse.Content | ConvertFrom-Json

Write-Host "Move response: success=$($moveData.success)" -ForegroundColor Green
Write-Host "" -ForegroundColor Yellow
Write-Host "=== CHECK SERVER CONSOLE FOR DEBUG OUTPUT ===" -ForegroundColor Red
Write-Host "Look for:" -ForegroundColor Yellow
Write-Host "  1. '=== NOTIFYING GAME STATE ===' message" -ForegroundColor Yellow
Write-Host "  2. Board state and turn information" -ForegroundColor Yellow
Write-Host "  3. 'Sending to Player1/Player2' messages" -ForegroundColor Yellow
Write-Host "  4. WebSocket delivery success/failure" -ForegroundColor Yellow
Write-Host "  5. '=== GAME STATE NOTIFICATIONS SENT ===' confirmation" -ForegroundColor Yellow

# Step 5: Make second move
Write-Host "`n[STEP 5] Making second move (Player 2 - O, cell 0)" -ForegroundColor Magenta
$moveBody2 = @{
    sessionId = $session2
    matchId = $matchId
    cell = 0
} | ConvertTo-Json

$moveResponse2 = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $moveBody2 -ContentType "application/json"
$moveData2 = $moveResponse2.Content | ConvertFrom-Json

Write-Host "Move 2 response: success=$($moveData2.success)" -ForegroundColor Green
Write-Host "`n=== WebSocket Flow Test Complete ===" -ForegroundColor Cyan