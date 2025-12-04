Stan State Mini Game Collection - Test Plan (Draft)

Use this as a checklist. Update with real results and expand cases per game/API.


WHITE-BOX TESTS

Java backend:
  - Unit-ish: DatabaseManager.getGameStats aggregation when stats row missing (runner)
  - AsyncScoreTracker queue processing (multiple submissions, ensure commit)
  - RestApiServer endpoints: register/login, score submit, all-stats
  - WebSocket notifier: heartbeat handling (smoke)

Defold scripts:
  - Loader: proxy switching, ESC behavior, table routing
  - Quad stats trigger: T opens stats page with correct username
  - Runner init: auto score submit triggers once per load


BLACK-BOX TESTS

- End-to-end: login (invisible field), enter Quad, launch each table, submit scores, view stats in React page
- Runner: entering posts score=1 and appears in stats for the same username
- API: /api/user/{u}/all-stats returns runner entry with plays>=1 after submission
- Error handling: invalid username stats request returns autoCreated flag and no crash


REGRESSION TESTS

- Ensure Linker removal doesn't break loader (no linker proxy)
- Stats page still loads other games (goose, pong, puzzle, space, ttt, quad)
- T key still opens stats popup after runner changes


LOAD TESTING (LIGHT)

- Hit /api/user/{u}/score with multiple concurrent requests (e.g., 20) and verify no 500s
- AsyncScoreTracker queue stats: queued==processed under burst (manual observation)


TEST MATRIX (SAMPLE)

Browsers for stats: Chrome/Edge (file:// and http://localhost)
OS: Windows 10/11 (Defold runtime)
Users: new user auto-created vs existing user
Games: goose/pong/puzzle/space/ttt/runner


TEST CODE EXAMPLES

Backend Smoke Test (PowerShell):

  # Submit runner score
  ="TestUser"
  Invoke-RestMethod "http://localhost:8081/api/user//score" -Method Post -Body (@{gameName="runner";score=5;level=1}|ConvertTo-Json) -ContentType "application/json"
  
  # Fetch stats
  Invoke-RestMethod "http://localhost:8081/api/user//all-stats"


Simple Concurrency Test (PowerShell):

  1..20 | ForEach-Object {
    Start-Job { Invoke-RestMethod "http://localhost:8081/api/user/LoadUser/score" -Method Post -Body (@{gameName="runner";score=;level=1}|ConvertTo-Json) -ContentType "application/json" }
  }


NOTES

- Login field is invisible; tests must account for case-sensitive usernames
- Runner stats known gap: was not showing plays; backend now aggregates missing stats - verify manually
- AsyncScoreTracker must be running in background for stats aggregation
- Database must be initialized before running tests
- Each test user must be created or auto-created via login


EXECUTION RESULTS (LATEST RUN)

Date: (fill in current run)

tests/run-tests.ps1 (requires server up at http://localhost:8081 before running):
  - Build: PASS (Gradle build -x test)
  - Register/Login: PARTIAL. TestRunnerUser OK; LoadUser/EdgeCaseUser hit 401 after 409/500 register warnings (needs manual user setup or adjusted auth)
  - Score submissions across games: PASS (warnings ignored)
  - Burst runner submissions: PASS
  - Leaderboards/Stats after submissions: PASS
  - Fallback aggregation check: PASS
  - Action: create/clear test users to avoid 401/409 noise; rerun for clean pass

tests/run-schema-tests.ps1 (requires H2 jar in Gradle cache and server-initialized DB):
  - H2 jar found, but org.h2.tools.Shell rejects INFORMATION_SCHEMA queries ("Feature not supported") so table check failed (reported missing users)
  - Action: replace Shell with a driver-based query (System.Data + H2 driver) or adjust SQL to Shell-supported commands before relying on results


QUICK START FOR TESTING

1. Start backend server
   cd tictactoe/java-server
   ./gradlew run

2. Wait for "Server ready for production" message

3. In new terminal, run full test suite
   .\tests\run-tests.ps1

4. Check results - PASS/FAIL/WARNINGS

5. For schema validation (optional)
   .\tests\run-schema-tests.ps1


COMMON ISSUES

Problem: 401 Unauthorized errors during login test
Solution: Ensure valid test users exist or auto-created. Check password hash matches.

Problem: AsyncScoreTracker not updating stats
Solution: Verify AsyncScoreTracker thread is running (check server logs for "AsyncScoreTracker initialized")

Problem: WebSocket connection timeout
Solution: Check firewall allows port 8080. Verify server started with correct ports.

Problem: Database locked errors
Solution: Kill any running java processes. Delete database file to reset. Run ./gradlew clean

Problem: Gradle build failures
Solution: Delete build directory. Run ./gradlew clean; ./gradlew build -x test

