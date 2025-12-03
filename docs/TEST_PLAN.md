# Stan State Mini Game Collection - Test Plan (Draft)

Use this as a checklist. Update with real results and expand cases per game/API.

### White-Box Tests
- Java backend:
  - Unit-ish: DatabaseManager.getGameStats aggregation when stats row missing (runner).
  - AsyncScoreTracker queue processing (multiple submissions, ensure commit).
  - RestApiServer endpoints: register/login, score submit, all-stats.
  - WebSocket notifier: heartbeat handling (smoke).
- Defold scripts:
  - Loader: proxy switching, ESC behavior, table routing.
  - Quad stats trigger: T opens stats page with correct username.
  - Runner init: auto score submit triggers once per load.

### Black-Box Tests
- End-to-end: login (invisible field), enter Quad, launch each table, submit scores, view stats in React page.
- Runner: entering posts score=1 and appears in stats for the same username.
- API: /api/user/{u}/all-stats returns runner entry with plays>=1 after submission.
- Error handling: invalid username stats request returns autoCreated flag and no crash.

### Regression Tests
- Ensure Linker removal doesn't break loader (no linker proxy).
- Stats page still loads other games (goose, pong, puzzle, space, ttt, quad).
- T key still opens stats popup after runner changes.

### Load Testing (light)
- Hit /api/user/{u}/score with multiple concurrent requests (e.g., 20) and verify no 500s.
- AsyncScoreTracker queue stats: queued==processed under burst (manual observation).

### Test Matrix (sample)
- Browsers for stats: Chrome/Edge (file:// and http://localhost).
- OS: Windows 10/11 (Defold runtime).
- Users: new user auto-created vs existing user.
- Games: goose/pong/puzzle/space/ttt/runner.

### Test Code Examples
- Backend smoke (PowerShell):
  `
  # Submit runner score
  $u="TestUser"
  Invoke-RestMethod "http://localhost:8081/api/user/$u/score" -Method Post -Body (@{gameName="runner";score=5;level=1}|ConvertTo-Json) -ContentType "application/json"
  # Fetch stats
  Invoke-RestMethod "http://localhost:8081/api/user/$u/all-stats"
  `
- Simple concurrency (PowerShell):
  `
  1..20 | ForEach-Object {
    Start-Job { Invoke-RestMethod "http://localhost:8081/api/user/LoadUser/score" -Method Post -Body (@{gameName="runner";score=$_;level=1}|ConvertTo-Json) -ContentType "application/json" }
  }
  `

### Notes
- Login field is invisible; tests must account for case-sensitive usernames.
- Runner stats known gap: was not showing plays; backend now aggregates missing stats - verify manually.

### Execution Results (latest run)
- Date: (fill in current run)
- tests/run-tests.ps1 (requires server up at http://localhost:8081 before running):
  - Build: PASS (Gradle build -x test).
  - Register/Login: PARTIAL. TestRunnerUser OK; LoadUser/EdgeCaseUser hit 401 after 409/500 register warnings (needs manual user setup or adjusted auth).
  - Score submissions across games: PASS (warnings ignored).
  - Burst runner submissions: PASS.
  - Leaderboards/Stats after submissions: PASS.
  - Fallback aggregation check: PASS.
  - Action: create/clear test users to avoid 401/409 noise; rerun for clean pass.
- tests/run-schema-tests.ps1 (requires H2 jar in Gradle cache and server-initialized DB):
  - H2 jar found, but org.h2.tools.Shell rejects INFORMATION_SCHEMA queries ("Feature not supported") so table check failed (reported missing users).
  - Action: replace Shell with a driver-based query (System.Data + H2 driver) or adjust SQL to Shell-supported commands before relying on results.
