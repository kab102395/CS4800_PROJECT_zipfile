# Turn Logic Debug Test
Write-Host "=== Turn Logic Debug Test ===" -ForegroundColor Cyan

$baseUrl = "http://127.0.0.1:8081"

# Create test sessions
$session1 = "turn-test-1-$(Get-Date -Format 'HHmmss')"
$session2 = "turn-test-2-$(Get-Date -Format 'HHmmss')"

Write-Host "Player 1 (X): $session1" -ForegroundColor Yellow
Write-Host "Player 2 (O): $session2" -ForegroundColor Yellow

# Create and join match
Write-Host "`n[SETUP] Creating and joining match..." -ForegroundColor Magenta
$createBody = @{
    sessionId = $session1
    playerName = "Player1"
    matchName = "Turn Test"
} | ConvertTo-Json

$createResponse = Invoke-WebRequest -Uri "$baseUrl/api/create-match" -Method POST -Body $createBody -ContentType "application/json"
$matchData = $createResponse.Content | ConvertFrom-Json
$matchId = $matchData.matchId

$joinBody = @{
    sessionId = $session2
    playerName = "Player2"
    matchId = $matchId
} | ConvertTo-Json

$joinResponse = Invoke-WebRequest -Uri "$baseUrl/api/join-match" -Method POST -Body $joinBody -ContentType "application/json"
Write-Host "Match setup complete: $matchId" -ForegroundColor Green

# Test turn sequence
Write-Host "`n[TURN SEQUENCE TEST]" -ForegroundColor Magenta

# Move 1: Player 1 (X) should be able to move
Write-Host "Move 1 - Player 1 (X) to cell 4:" -ForegroundColor Yellow
$move1 = @{
    sessionId = $session1
    matchId = $matchId
    cell = 4
} | ConvertTo-Json

$response1 = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $move1 -ContentType "application/json"
$result1 = $response1.Content | ConvertFrom-Json
Write-Host "  Result: success=$($result1.success)" -ForegroundColor $(if ($result1.success) { "Green" } else { "Red" })

# Move 2: Player 1 (X) should NOT be able to move again
Write-Host "Move 2 - Player 1 (X) to cell 0 (should fail - not their turn):" -ForegroundColor Yellow
$move2 = @{
    sessionId = $session1
    matchId = $matchId
    cell = 0
} | ConvertTo-Json

$response2 = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $move2 -ContentType "application/json"
$result2 = $response2.Content | ConvertFrom-Json
Write-Host "  Result: success=$($result2.success)" -ForegroundColor $(if (-not $result2.success) { "Green" } else { "Red" })
if ($result2.success) {
    Write-Host "  ❌ BUG: Player 1 was allowed to move twice in a row!" -ForegroundColor Red
} else {
    Write-Host "  ✅ Turn validation working correctly" -ForegroundColor Green
}

# Move 3: Player 2 (O) should be able to move
Write-Host "Move 3 - Player 2 (O) to cell 0:" -ForegroundColor Yellow
$move3 = @{
    sessionId = $session2
    matchId = $matchId
    cell = 0
} | ConvertTo-Json

$response3 = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $move3 -ContentType "application/json"
$result3 = $response3.Content | ConvertFrom-Json
Write-Host "  Result: success=$($result3.success)" -ForegroundColor $(if ($result3.success) { "Green" } else { "Red" })
if (-not $result3.success) {
    Write-Host "  ❌ BUG: Player 2 cannot make their turn!" -ForegroundColor Red
} else {
    Write-Host "  ✅ Player 2 can move correctly" -ForegroundColor Green
}

# Move 4: Player 2 (O) should NOT be able to move again
Write-Host "Move 4 - Player 2 (O) to cell 1 (should fail - not their turn):" -ForegroundColor Yellow
$move4 = @{
    sessionId = $session2
    matchId = $matchId
    cell = 1
} | ConvertTo-Json

$response4 = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $move4 -ContentType "application/json"
$result4 = $response4.Content | ConvertFrom-Json
Write-Host "  Result: success=$($result4.success)" -ForegroundColor $(if (-not $result4.success) { "Green" } else { "Red" })
if ($result4.success) {
    Write-Host "  ❌ BUG: Player 2 was allowed to move twice in a row!" -ForegroundColor Red
} else {
    Write-Host "  ✅ Turn validation working correctly" -ForegroundColor Green
}

# Move 5: Player 1 (X) should be able to move again
Write-Host "Move 5 - Player 1 (X) to cell 1:" -ForegroundColor Yellow
$move5 = @{
    sessionId = $session1
    matchId = $matchId
    cell = 1
} | ConvertTo-Json

$response5 = Invoke-WebRequest -Uri "$baseUrl/api/move" -Method POST -Body $move5 -ContentType "application/json"
$result5 = $response5.Content | ConvertFrom-Json
Write-Host "  Result: success=$($result5.success)" -ForegroundColor $(if ($result5.success) { "Green" } else { "Red" })
if (-not $result5.success) {
    Write-Host "  ❌ BUG: Player 1 cannot make their second turn!" -ForegroundColor Red
} else {
    Write-Host "  ✅ Turn alternation working correctly" -ForegroundColor Green
}

Write-Host "`n=== Turn Logic Test Complete ===" -ForegroundColor Cyan
Write-Host "Summary of expected results:" -ForegroundColor Yellow
Write-Host "  Move 1 (P1): ✅ Success" -ForegroundColor Yellow
Write-Host "  Move 2 (P1): ❌ Fail (not their turn)" -ForegroundColor Yellow
Write-Host "  Move 3 (P2): ✅ Success" -ForegroundColor Yellow
Write-Host "  Move 4 (P2): ❌ Fail (not their turn)" -ForegroundColor Yellow
Write-Host "  Move 5 (P1): ✅ Success" -ForegroundColor Yellow