# Stan State Mini Game Collection - Server Architecture

## Overview

The backend is a multithreaded Java server that handles game state, user authentication, score tracking, and statistics for the Stan State Mini Game Collection. It uses an embedded H2 database, REST APIs via Spark framework, and WebSocket support for real-time game notifications.

**Key Components:**
- REST API Server (Spark) on port 8081
- WebSocket Server on port 8080
- H2 Embedded Database with connection pooling
- Asynchronous score tracking with queue processing
- Session management and lobby state caching

---

## Database Architecture

### Setup

The database is an embedded H2 database stored as file-based persistence at `database/ttt_game.*`. No external database server is required.

**Why H2?**
- Lightweight, embedded (no separate process)
- MySQL compatibility mode for familiar SQL
- Automatic initialization and schema migration
- ACID transactions for data consistency
- File-based persistence survives server restarts

### Schema

The database contains the following core tables:

**users**
- Stores user credentials and metadata
- Primary key: username (case-sensitive)
- Fields: username, passwordHash, email, created, autoCreated (flag for API-created users)

**user_game_scores**
- Individual score submissions from games
- Primary key: id (auto-increment)
- Fields: username, gameName, score, level, timestamp, processed (flag for AsyncScoreTracker)
- Used for raw score data before aggregation

**user_game_stats**
- Aggregated statistics per game per user
- Primary key: (username, gameName)
- Fields: plays (count), best (max score), avg (average score), total (sum of scores), lastPlay (timestamp)
- Updated when scores are submitted or aggregated

**player_sessions**
- Active user sessions and login state
- Primary key: sessionId (UUID)
- Fields: username, loginTime, lastHeartbeat, isActive

**game_matches**
- Historical game match records
- Primary key: matchId (UUID)
- Fields: gameName, player1, player2, startTime, endTime, winner, moves

**game_moves**
- Detailed move logs for complex games (TTT)
- Primary key: moveId (UUID)
- Fields: matchId, player, move, timestamp, sequence

**pending_notifications**
- Queue for WebSocket notifications not yet sent
- Primary key: notificationId (UUID)
- Fields: targetSession, message, created, retries

**connection_health**
- Tracks WebSocket connection quality
- Primary key: sessionId
- Fields: lastHeartbeat, packetsSent, packetsReceived, dropRate

**lobby_state** (cache-backed)
- Current game lobby state
- Fields: gameName, players, status, createdAt

**player_stats**
- Fallback aggregated stats when user_game_stats missing
- Computed on-the-fly from user_game_scores

**schema_version**
- Tracks database migration version
- Ensures proper schema initialization

### Connection Pooling

The database uses a **HikariCP connection pool** with:
- **Pool size:** 10 connections
- **Why pooling?** Reusing connections is faster than creating new ones. Prevents connection exhaustion under load.
- **Thread-safe:** Each thread gets a connection from the pool; returned after use.

### MERGE Upserts

Most write operations use SQL `MERGE` statements for **idempotency**:

```sql
MERGE INTO user_game_stats 
  (username, gameName, plays, best, avg, total, lastPlay)
  VALUES (?, ?, ?, ?, ?, ?, ?)
  KEY (username, gameName)
```

**Why?**
- Prevents duplicate inserts if a request is retried
- Atomically updates or inserts in a single operation
- Ensures consistency without complex logic

---

## Multi-Threading Architecture

### Single-Writer Pattern for Score Processing

The most critical aspect of the server is **AsyncScoreTracker**, which uses a **single-writer, multiple-reader** pattern:

**Problem it solves:**
- Concurrent score submissions could cause race conditions
- Database writes could interleave, causing inconsistency
- Stats aggregation could read partial data

**Solution: Single Writer Thread**
```
Client 1 --\
Client 2 ----> Queue -----> Single Writer Thread -----> Database
Client 3 ---/                (Serialized commits)
```

**How it works:**
1. Score submissions are placed in a **thread-safe queue** (BlockingQueue)
2. A **dedicated writer thread** processes the queue sequentially
3. Each score is committed to the database atomically
4. No locks needed—serialization prevents conflicts

**Why this approach?**
- **Lock-free:** Avoids deadlocks and lock contention
- **Ordered:** Scores processed in FIFO order
- **Testable:** Easy to verify order and consistency
- **Simple:** No complex locking logic

### WebSocket Server Threading

The WebSocket server uses **Jetty's thread pool**:
- Accepts client connections on a thread
- Handles each connection's messages on a thread
- Broadcasts notifications via a separate notification thread
- Threads are pooled and reused

**Flow for TTT game notifications:**
```
Player makes move ---> WebSocket handler thread
                            |
                            v
                    Update game state
                            |
                            v
                    Create notification -----> Notification thread
                            |
                            v
                    Send to other player
```

### REST API Server Threading

The REST API (Spark + Jetty) uses a **thread-per-request model**:
- Each HTTP request gets a thread from the Jetty thread pool (default 200 threads)
- Thread processes the request (query DB, compute stats)
- Thread returns to pool

**Why thread-per-request for REST?**
- HTTP requests are short-lived
- I/O waits naturally yield control
- Thread pool prevents resource exhaustion

---

## Database Operations and Why They Work

### Score Submission Flow

```
Client POST /api/user/{u}/score
  |
  v
RestApiServer.submitScore()
  |
  +---> Parse request body
  |
  +---> Insert into user_game_scores (AsyncScoreTracker queue)
  |
  +---> Return 200 OK immediately
  |
  (Background)
  +---> AsyncScoreTracker writer thread dequeues
        |
        +---> Read current stats for user/game
        |
        +---> Recompute plays, best, avg, total
        |
        +---> MERGE into user_game_stats
        |
        +---> Mark in_game_scores as processed
```

**Why return immediately?**
- Client doesn't wait for database commit
- User perceives fast response
- Writer thread commits in background

### Stats Aggregation (Fallback)

When `/api/user/{u}/all-stats` is called:

```
SELECT * FROM user_game_stats WHERE username = ?
  |
  v
If row missing:
  +---> SELECT SUM(score), MAX(score), AVG(score), COUNT(*)
        FROM user_game_scores
        WHERE username = ? AND gameName = ?
  |
  v
Return computed stats to client
```

**Why this fallback?**
- If AsyncScoreTracker is behind, we still show recent data
- Queries database directly instead of waiting for async processing
- Ensures eventual consistency

### Connection Management

Each thread gets a connection from the pool:

```
Thread A: SELECT FROM users WHERE username = ?
  |
  +---> Get connection from pool (or wait if exhausted)
  |
  +---> Execute query
  |
  +---> Return connection to pool
  |
Thread B (waiting): Receives returned connection
```

**Pool protects against:**
- Connection exhaustion (max 10 concurrent DB operations)
- Slow clients holding resources
- Runaway threads from exhausting system memory

---

## Networking: HTTP REST API

### Request/Response Flow

**Client sends:**
```http
POST /api/user/TestUser/score HTTP/1.1
Host: localhost:8081
Content-Type: application/json

{
  "gameName": "runner",
  "score": 42,
  "level": 1
}
```

**Server processes:**
1. Spark framework routes to `scoreController.handleSubmit()`
2. Extract username from URL, body from request
3. Validate input (non-null, positive score)
4. Enqueue to AsyncScoreTracker
5. Return response

**Server responds:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "message": "Score submitted"
}
```

### Supported Endpoints

**Authentication:**
- `POST /api/register` - Create user account
- `POST /api/login` - Authenticate and create session

**Score Management:**
- `POST /api/user/{username}/score` - Submit score
- `GET /api/leaderboard/{gameName}?limit=5` - Get top scores

**Statistics:**
- `GET /api/user/{username}/all-stats` - Get user stats across all games
- `GET /api/user/{username}/stats/{gameName}` - Get stats for one game

**Health:**
- `GET /api/health` - Server status check

### HTTP Status Codes

- `200 OK` - Request succeeded
- `201 Created` - Resource created
- `400 Bad Request` - Invalid input (missing fields, negative score)
- `401 Unauthorized` - Login failed or session expired
- `409 Conflict` - Duplicate user (already registered)
- `500 Internal Server Error` - Unexpected error

---

## Networking: WebSocket Real-Time

### WebSocket Connection Flow

**Handshake:**
```
Client: GET /ws HTTP/1.1
        Upgrade: websocket
        Connection: Upgrade
        
Server: HTTP/1.1 101 Switching Protocols
        Upgrade: websocket
        Connection: Upgrade
```

**Connection established:** Both sides can send/receive messages at any time.

### WebSocket Message Format

**Client sends (TTT game move):**
```json
{
  "type": "move",
  "matchId": "uuid-1234",
  "player": "Player1",
  "move": "board[0]",
  "timestamp": 1700000000
}
```

**Server broadcasts to other player:**
```json
{
  "type": "gameUpdate",
  "matchId": "uuid-1234",
  "opponent": "Player1",
  "move": "board[0]",
  "gameState": "running",
  "yourTurn": true
}
```

### Heartbeat and Health Monitoring

```
Client sends heartbeat every 30 seconds
  |
  v
Server receives, updates connection_health.lastHeartbeat
  |
  v
Server checks all connections every 60 seconds
  |
  v
If lastHeartbeat > 120 seconds ago:
  +---> Mark connection as dead
  +---> Clean up session
  +---> Notify other players
```

**Why heartbeats?**
- Detect dead connections (network outage, client crash)
- Prevent zombie connections consuming server memory
- Allow graceful cleanup of game state

---

## Why This Architecture Works

### 1. Concurrency Without Locks

**Problem:** Multiple clients submitting scores simultaneously could corrupt stats.

**Solution:** Single-writer queue serializes all score commits.

**Benefit:** No deadlocks, simple reasoning, easy to debug.

### 2. Responsive API

**Problem:** Score commits are slow (DB I/O). Clients would wait.

**Solution:** Return immediately, process asynchronously in background.

**Benefit:** Clients perceive fast responses. Server handles burst load by queueing.

### 3. Eventual Consistency

**Problem:** Stats aggregation could read before processing completes.

**Solution:** Fallback computation if stats row missing.

**Benefit:** Clients always get accurate data, even if slightly delayed.

### 4. Connection Pooling

**Problem:** Creating new DB connections is expensive.

**Solution:** Reuse 10 connections across all threads.

**Benefit:** Fast queries, bounded resource usage, prevents exhaustion.

### 5. Embedded Database

**Problem:** Setting up external MySQL is complex for testing/deployment.

**Solution:** H2 embedded with file persistence.

**Benefit:** Single jar deployment, automatic schema migration, no ops overhead.

### 6. Thread-Per-Request (REST) + Single-Writer (Scores)

**Problem:** REST is naturally concurrent. Scores need serialization.

**Solution:** REST uses threads for I/O concurrency. Scores funnel through single writer.

**Benefit:** Best of both worlds—responsive API + safe database.

---

## API Call Examples

### PowerShell Example: Submit Score and Check Stats

```powershell
$Base = "http://localhost:8081"
$User = "TestPlayer"
$Headers = @{ "Content-Type" = "application/json" }

# Register user
$registerBody = @{ username = $User; passwordHash = "hash123"; email = "" } | ConvertTo-Json
Invoke-WebRequest "$Base/api/register" -Method Post -Headers $Headers -Body $registerBody

# Submit score
$scoreBody = @{ gameName = "runner"; score = 50; level = 2 } | ConvertTo-Json
Invoke-WebRequest "$Base/api/user/$User/score" -Method Post -Headers $Headers -Body $scoreBody

# Fetch stats
$statsResp = Invoke-WebRequest "$Base/api/user/$User/all-stats" -Method Get
$stats = $statsResp.Content | ConvertFrom-Json
Write-Host "Runner stats: $($stats.runner | ConvertTo-Json)"
```

### Python Example: Concurrent Score Submission

```python
import requests
import concurrent.futures

BASE = "http://localhost:8081"
USER = "LoadUser"

def submit_score(score_value):
    url = f"{BASE}/api/user/{USER}/score"
    data = {"gameName": "runner", "score": score_value, "level": 1}
    return requests.post(url, json=data).status_code

# Submit 20 scores concurrently
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
    futures = [executor.submit(submit_score, i) for i in range(1, 21)]
    results = [f.result() for f in concurrent.futures.as_completed(futures)]
    print(f"Submitted {len(results)} scores, all {results[0]} status")
```

### WebSocket Example: TTT Game Notification (JavaScript)

```javascript
// Client connects to WebSocket
const ws = new WebSocket("ws://localhost:8080/ws");

ws.onopen = () => {
  console.log("Connected to server");
  
  // Send a game move
  ws.send(JSON.stringify({
    type: "move",
    matchId: "game-1",
    player: "Alice",
    move: "board[4]",  // Center square
    timestamp: Date.now()
  }));
};

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  
  if (msg.type === "gameUpdate") {
    console.log(`Opponent ${msg.opponent} played: ${msg.move}`);
    console.log(`Your turn: ${msg.yourTurn}`);
  }
};

ws.onerror = (error) => {
  console.error("WebSocket error:", error);
};
```

---

## Performance Characteristics

### Throughput

- **Score submission:** ~100-500 scores/second (single writer thread limited by DB I/O)
- **REST requests:** ~1000s/second (thread pool scales with hardware)
- **WebSocket messages:** Limited by client bandwidth, not server CPU

### Latency

- **Score submit response:** <50ms (immediate, queued)
- **Stats fetch:** <100ms (DB query, possibly computed fallback)
- **WebSocket message:** <10ms (in-memory broadcast)

### Resource Usage

- **Memory:** ~100MB JVM + database file size (typically 10-100MB)
- **Database connections:** Capped at 10 (from pool)
- **Threads:** ~100-200 (Jetty + AsyncScoreTracker + WebSocket handlers)

---

## Troubleshooting

### High Latency or Timeouts

**Cause:** Database connection pool exhausted.

**Fix:** Reduce concurrent request count or increase pool size in code.

### Duplicate Score Issues

**Cause:** AsyncScoreTracker queue backed up.

**Fix:** Monitor queue depth; if persistent, scores are being submitted faster than DB can commit.

### Missing Stats Aggregation

**Cause:** AsyncScoreTracker behind; fallback aggregation didn't run.

**Fix:** Normal behavior; stats will appear after writer catches up. Query `/api/user/{u}/all-stats` which triggers fallback.

### WebSocket Disconnects

**Cause:** Client-side timeout or network interruption.

**Fix:** Implement client-side reconnect logic. Server cleans up after 120 seconds.

---

## Security Considerations

**Current Implementation (Draft):**
- Passwords stored as plain hash (no salt or bcrypt)
- No HTTPS/TLS (suitable for local testing only)
- No rate limiting on API endpoints
- Sessions not validated on requests

**For Production:**
- Implement bcrypt with salt
- Enable TLS/HTTPS
- Add rate limiting (e.g., 10 requests/second per IP)
- Validate session token on protected endpoints
- Add CORS headers for cross-origin clients
