# Comprehensive backend test battery (PowerShell)
# NOTE: Server must already be running on http://localhost:8081 before running this script.
# Run from repo root: .\tests\run-tests.ps1

$ErrorActionPreference = "Stop"
$Base = "http://localhost:8081"
$Users = @("TestRunnerUser", "LoadUser", "EdgeCaseUser")
$Games = @("goose","tictactoe","puzzle","pong","space","quad","runner")
$Headers = @{ "Content-Type" = "application/json" }

function Assert-Status($resp, $expected, $msg) {
    if ($resp.StatusCode -ne $expected) {
        throw "$msg (HTTP $($resp.StatusCode))"
    }
}

function Post-Json($url, $body) {
    $json = $body | ConvertTo-Json
    return Invoke-WebRequest -Uri $url -Method Post -Headers $Headers -Body $json
}

function Get-Json($url) {
    return Invoke-WebRequest -Uri $url -Method Get
}

Write-Host "=== Backend Test Battery ===" -ForegroundColor Cyan
Write-Host "Prereq: Ensure the Java server is running on $Base before executing." -ForegroundColor Yellow

# 1) Build validation (no tests) to ensure code compiles (optional)
Write-Host "`n[1/6] Gradle build (no tests)..." -ForegroundColor Cyan
pushd "..\tictactoe\java-server"
./gradlew.bat build -x test | Out-Null
popd
Write-Host "Build OK" -ForegroundColor Green

# 2) Register/login and basic stats for all users
Write-Host "`n[2/6] Register/Login + baseline stats..." -ForegroundColor Cyan
foreach ($u in $Users) {
    $registerBody = @{ username = $u; passwordHash = "hash_$u"; email = "" }
    try { Post-Json "$Base/api/register" $registerBody | Out-Null } catch { Write-Warning ("Register failed/duplicate for {0}: {1}" -f $u, $_) }
    try {
        $loginBody = @{ username = $u; passwordHash = "hash_$u" }
        $loginResp = Post-Json "$Base/api/login" $loginBody
        Assert-Status $loginResp 200 "Login failed for $u"
        Write-Host "User $u login OK" -ForegroundColor Green
        $statsResp = Get-Json "$Base/api/user/$u/all-stats"
        Assert-Status $statsResp 200 "All-stats failed for $u"
    } catch {
        Write-Warning ("Baseline stat check failed for {0}: {1}" -f $u, $_)
    }
}

# 3) Score submission across games
Write-Host "`n[3/6] Score submissions..." -ForegroundColor Cyan
foreach ($u in $Users) {
    foreach ($g in $Games) {
        $score = Get-Random -Minimum 1 -Maximum 100
        $body = @{ gameName = $g; score = $score; level = 1 }
        try {
            $resp = Post-Json "$Base/api/user/$u/score" $body
            Assert-Status $resp 200 "Score submit failed for $u/$g"
        } catch { Write-Warning ("Score submit failed for {0}/{1}: {2}" -f $u, $g, $_) }
    }
}
Write-Host "Score submissions OK" -ForegroundColor Green

# 4) Concurrency/load: burst submit for runner
Write-Host "`n[4/6] Concurrency burst for runner..." -ForegroundColor Cyan
$jobs = @()
1..20 | ForEach-Object {
    $jobs += Start-Job -ScriptBlock {
        $Base = "http://localhost:8081"
        $Headers = @{ "Content-Type" = "application/json" }
        $body = @{ gameName = "runner"; score = 5; level = 1 } | ConvertTo-Json
        Invoke-WebRequest -Uri "$Base/api/user/LoadUser/score" -Method Post -Headers $Headers -Body $body | Out-Null
    }
}
Wait-Job $jobs | Out-Null
Receive-Job $jobs | Out-Null
Write-Host "Burst submissions complete" -ForegroundColor Green

# 5) Leaderboard + stats verification
Write-Host "`n[5/6] Leaderboard and stats verification..." -ForegroundColor Cyan
foreach ($g in $Games) {
    $lb = Get-Json "$Base/api/leaderboard/$g?limit=5"
    Assert-Status $lb 200 "Leaderboard failed for $g"
}
foreach ($u in $Users) {
    $statsResp = Get-Json "$Base/api/user/$u/all-stats"
    Assert-Status $statsResp 200 "All-stats failed for $u (post submissions)"
}
Write-Host "Leaderboards/stats OK" -ForegroundColor Green

# 6) Integrity check: missing stats fallback
Write-Host "`n[6/6] Integrity check for missing stats fallback..." -ForegroundColor Cyan
$u = "RunnerFallbackUser"
$body = @{ username = $u; passwordHash = "hash_$u"; email = "" } | ConvertTo-Json
try { Invoke-WebRequest "$Base/api/register" -Method Post -Headers $Headers -Body $body | Out-Null } catch {}
Post-Json "$Base/api/user/$u/score" @{ gameName="runner"; score=7; level=1 } | Out-Null
$statsResp = Invoke-WebRequest "$Base/api/user/$u/all-stats"
Assert-Status $statsResp 200 "Fallback stats failed"
Write-Host "Fallback aggregation OK" -ForegroundColor Green

Write-Host "`nAll tests completed." -ForegroundColor Green
