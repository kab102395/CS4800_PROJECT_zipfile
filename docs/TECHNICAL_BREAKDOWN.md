Stan State Mini Game Collection - Technical Breakdown

================================================================================
1. NETWORKING PROTOCOL
================================================================================

PROTOCOL OVERVIEW

The system uses two complementary networking protocols:

HTTP/REST (Stateless Request-Response)
- Port: 8081
- Used for: Authentication, score submission, statistics retrieval, leaderboards
- Format: JSON request/response bodies
- Stateless: Each request contains all information needed; server doesn't maintain per-request state

WebSocket (Stateful Real-Time)
- Port: 8080
- Used for: TTT game notifications, player move broadcasts, heartbeats, connection health
- Format: JSON messages, bidirectional
- Stateful: Connection persists; server tracks session and game state


HTTP ENDPOINTS & PROTOCOL FLOW

Authentication Flow:

Client: POST /api/register
  Body: { username, passwordHash, email }
  
Server: HTTP 200/201 or 409 (conflict if exists)
  Body: { success, message, userId }

Client: POST /api/login
  Body: { username, passwordHash }
  
Server: HTTP 200 or 401 (unauthorized)
  Body: { success, sessionId, message }


Score Submission Protocol:

Client: POST /api/user/{username}/score
  Headers: Content-Type: application/json
  Body: { gameName, score, level }
  
Server Enqueues to AsyncScoreTracker
  
Server Response (immediate): HTTP 200
  Body: { success: true, message: "Score submitted" }

Background (AsyncScoreTracker):
  1. Dequeue score from queue
  2. Read current stats from DB
  3. Compute new aggregates (plays+1, best, avg, total)
  4. MERGE into user_game_stats
  5. Mark score as processed


Stats Retrieval Protocol:

Client: GET /api/user/{username}/all-stats
  
Server: HTTP 200
  Body:
    runner: { plays: 5, best: 100, avg: 80, total: 400 }
    goose: { plays: 3, best: 50, avg: 40, total: 120 }
    (all games)
  
If user_game_stats row missing:
  - Fallback: Query user_game_scores directly
  - Compute aggregates on-the-fly
  - Return computed stats


Leaderboard Protocol:

Client: GET /api/leaderboard/{gameName}?limit=10
  
Server: HTTP 200
  Body:
    leaderboard:
      { rank: 1, username: "Alice", score: 1000, plays: 50 }
      { rank: 2, username: "Bob", score: 950, plays: 48 }


WEBSOCKET PROTOCOL (TTT REAL-TIME)

Connection Handshake:

Client: GET /ws HTTP/1.1
        Upgrade: websocket
        Connection: Upgrade
        Sec-WebSocket-Key: [base64]
        
Server: HTTP/1.1 101 Switching Protocols
        Upgrade: websocket
        Connection: Upgrade
        Sec-WebSocket-Accept: [computed]

(Connection now open for bidirectional messages)


Message Types:

Client to Server: Game Move
type: "move"
matchId: "match-uuid-1234"
player: "Alice"
move: "board[4]"
timestamp: 1700000000

Server to All Players: Game Update
type: "gameUpdate"
matchId: "match-uuid-1234"
opponent: "Bob"
move: "board[0]"
gameState: "running"
yourTurn: true
boardState: [["X", "O", null], ...]

Client to Server: Heartbeat (every 30s)
type: "heartbeat"
sessionId: "session-uuid-5678"
timestamp: 1700000000

Server to Client: Heartbeat Ack
type: "heartbeatAck"
latency: 12

Server to Client: Game End Notification
type: "gameEnd"
matchId: "match-uuid-1234"
winner: "Alice"
reason: "three-in-a-row"
finalScore: { "Alice": 1, "Bob": 0 }


HTTP STATUS CODES & ERROR HANDLING

200 - OK: Successful read operation
201 - Created: User registered, match created
400 - Bad Request: Missing/invalid fields, negative score, invalid game name
401 - Unauthorized: Login failed, session expired
409 - Conflict: Duplicate user registration
500 - Server Error: Unexpected exception, DB connection failed


RETRY LOGIC (Exponential Backoff)

Client attempts request
  |
  v
Server responds 5xx -> Retry delay = 1s, attempt 1
  |
  v
Server responds 5xx -> Retry delay = 2s, attempt 2
  |
  v
Server responds 5xx -> Retry delay = 4s, attempt 3
  |
  v
Max retries (3) reached -> Return error to user


================================================================================
2. GAME LOGIC
================================================================================

GAME STATE MODEL

Each game follows a standard state machine:

Created -> Lobby (waiting for players)
  |
  v
Started (in-progress)
  |
  v
Completed (winner determined)
  |
  v
Scored (score saved, stats updated)


PER-GAME LOGIC

Tic Tac Toe (TTT)
- Players: 2 (Alice vs Bob)
- Board: 3x3 grid (9 cells)
- Win Condition: Three marks in a row (horizontal, vertical, diagonal)
- Server Role: Validate moves, detect win, broadcast state, determine winner, submit score


Move Validation:

Move received: player=Alice, move=board[4] (center)

Validate:
  1. Is it player's turn? (alternates X/O)
  2. Is board[4] empty? (not "X" or "O")
  3. Within bounds? (0-8)
  
If valid:
  - Update board[4] = "X"
  - Check for win (3 in row)
  - If win: End game, submit score
  - Else: Switch turn to Bob
  - Broadcast new board state

If invalid:
  - Send error message
  - Request re-submission


Runner
- Player: Single (auto-plays)
- Score: 1 point per submission
- Server Role: Auto-submit score=1 on game load, aggregate stats, display leaderboard


Pong
- Players: 1 (human vs AI)
- Score: Points based on rally length or ball control
- Server Role: Receive final score, validate, submit, update leaderboard

Goose Hunt
- Players: 1 (hunter vs geese)
- Score: Geese killed / game duration
- Server Role: Accept and aggregate scores

Puzzle
- Players: 1
- Score: Time to solve or pieces placed
- Server Role: Accept and aggregate scores

Space Shooter
- Players: 1
- Score: Enemies destroyed or waves completed
- Server Role: Accept and aggregate scores


WIN/LOSS LOGIC

TTT Win Detection:

After each move, check all win patterns:

Patterns:
  Rows: [0,1,2], [3,4,5], [6,7,8]
  Columns: [0,3,6], [1,4,7], [2,5,8]
  Diagonals: [0,4,8], [2,4,6]

For each pattern:
  If board[pattern[0]] == board[pattern[1]] == board[pattern[2]]:
    Return Winner = board[pattern[0]]

If all 9 cells filled and no win:
  Return Draw

Else:
  Continue game, switch turn


SCORE AGGREGATION LOGIC

For each user/game combination:

plays = COUNT(scores)
best = MAX(score)
avg = AVG(score) = TOTAL / plays
total = SUM(score)
lastPlay = MAX(timestamp)

Example (Runner with scores [1, 1, 1]):
plays = 3
best = 1
avg = 3/3 = 1
total = 3


================================================================================
3. DATA MODEL
================================================================================

DATABASE SCHEMA (From schema_viewer.html)

users Table
Core user information and authentication.
- user_id (INT, PK, AUTO) -> Unique user identifier
- username (TEXT, UNIQUE, NOT NULL) -> Login name
- password_hash (TEXT, NOT NULL) -> Hashed password
- email (TEXT, UNIQUE) -> Contact email
- created_at (TIMESTAMP) -> Account creation time
- last_login (TIMESTAMP) -> Most recent login
- is_active (BOOLEAN) -> Account status

Why: Central user registry; password never stored raw


user_game_scores Table
Raw individual score submissions from all games.
- score_id (INT, PK, AUTO) -> Submission ID
- user_id (INT, FK to users) -> Which user
- game_name (TEXT) -> Game played (runner, tictactoe, goose, pong, puzzle, space)
- score (INT) -> Points earned
- level (INT) -> Difficulty level
- timestamp (TIMESTAMP) -> When submitted
- duration_seconds (INT) -> How long game took

Why: Audit trail; source of truth; fallback for stats if aggregation fails


user_game_stats Table
Aggregated statistics per user per game.
- stat_id (INT, PK, AUTO) -> Stat record ID
- user_id (INT, FK to users) -> Which user
- game_name (TEXT) -> Which game
- total_plays (INT) -> Number of times played
- best_score (INT) -> Highest score
- average_score (REAL) -> Mean score
- total_score (INT) -> Sum of all scores
- wins (INT) -> Wins (for competitive games)
- losses (INT) -> Losses
- draws (INT) -> Draws
- last_played (TIMESTAMP) -> Most recent play
- UNIQUE(user_id, game_name) -> One row per user/game

Why: Fast leaderboard/stats queries; pre-computed aggregates


player_sessions Table
Active player WebSocket and connection state.
- session_id (TEXT, PK) -> Unique session identifier
- player_name (TEXT) -> Player username
- connected_at (TIMESTAMP) -> Connection start
- last_heartbeat (TIMESTAMP) -> Most recent heartbeat
- websocket_id (TEXT) -> WebSocket connection ID
- connection_status (TEXT) -> active, disconnected, reconnecting
- retry_count (INT) -> Failed connection attempts

Why: Track active players; manage WebSocket connections; detect dead sessions


game_matches Table
TTT game match records and state.
- match_id (TEXT, PK) -> Unique match identifier
- player1_session (TEXT, FK to player_sessions) -> First player
- player2_session (TEXT, FK to player_sessions) -> Second player
- board (TEXT) -> Current board state (3x3 grid)
- current_turn (TEXT) -> Whose turn it is
- status (TEXT) -> in_progress, completed, abandoned
- result (TEXT) -> winner_player1, winner_player2, draw
- created_at (TIMESTAMP) -> Match start
- updated_at (TIMESTAMP) -> Last change
- last_move_at (TIMESTAMP) -> Most recent move time
- state_version (INT) -> Optimistic locking version

Why: Game history; replay capability; state tracking


game_moves Table
Individual moves within a TTT match.
- move_id (INT, PK, AUTO) -> Move record ID
- match_id (TEXT, FK to game_matches) -> Which match
- session_id (TEXT, FK to player_sessions) -> Who moved
- cell_position (INT) -> Board position (0-8)
- mark (TEXT) -> X or O
- timestamp (TIMESTAMP) -> When moved
- state_version (INT) -> Board version after this move
- validated (BOOLEAN) -> Move validation passed

Why: Move history; replay; cheat detection; pattern analysis


pending_notifications Table
Queued messages waiting to be sent to players.
- id (INT, PK, AUTO) -> Notification record ID
- session_id (TEXT, FK to player_sessions) -> Target player
- notification_type (TEXT) -> move_update, game_end, opponent_joined, etc.
- data (TEXT) -> JSON payload
- created_at (TIMESTAMP) -> When queued
- attempts (INT) -> Delivery attempts made
- max_attempts (INT) -> Max retries allowed
- next_retry (TIMESTAMP) -> When to retry
- delivered (BOOLEAN) -> Successfully sent

Why: Reliable message delivery; retry on connection failure


connection_health Table
WebSocket connection quality metrics.
- session_id (TEXT, PK) -> Which session
- last_ping (TIMESTAMP) -> Last ping sent
- last_pong (TIMESTAMP) -> Last pong received
- ping_count (INT) -> Total pings sent
- missed_pings (INT) -> Unanswered pings
- connection_quality (REAL) -> 0-1 score (pong_count / ping_count)

Why: Monitor connection health; detect latency; trigger cleanup


lobby_state Table
Current matchmaking queue (single record, always id=1).
- id (INT, PK) -> Always 1
- waiting_player_id (TEXT, FK to player_sessions) -> Player searching for match
- waiting_player_name (TEXT) -> Player name
- waiting_since (TIMESTAMP) -> How long waiting

Why: Simple queue for TTT matchmaking; cache-backed


player_stats Table
Player overview statistics (legacy/denormalized).
- player_name (TEXT, PK) -> Unique player
- total_games (INT) -> All games played (all games)
- wins (INT) -> Total wins
- losses (INT) -> Total losses
- draws (INT) -> Total draws
- win_rate (REAL) -> wins / (wins + losses + draws)
- last_game (TIMESTAMP) -> Most recent game

Why: Quick access to overall player profile


schema_version Table
Database schema migration tracking.
- version (INT, PK) -> Schema version number

Why: Track DB migrations; ensure schema consistency on startup


ENTITY RELATIONSHIPS

users (1) -----> (many) user_game_scores (submitted scores)
                     |
                     +---> (aggregated into) user_game_stats (1 per game)

users (1) -----> (many) player_sessions (login sessions)
                     |
                     +---> (many) game_matches (as player1 or player2)
                                   |
                                   +---> (many) game_moves

player_sessions (many) ----> (many) game_matches
                                    |
                                    +---> (many) pending_notifications (outbound)
                                    |
                                    +---> (1) connection_health (metrics)

lobby_state (1) ----> (1) player_sessions (waiting player)


DATA FLOW THROUGH SYSTEM

1. Player Login
   user record exists/created -> player_sessions record created -> session_id returned

2. Player In Lobby
   session added to lobby_state (waiting_player_id)

3. Match Found
   game_matches record created with player1_session, player2_session
   lobby_state cleared
   pending_notifications created (opponent_joined) for both players

4. TTT Move
   game_moves record inserted -> cell_position, mark, timestamp
   game_matches.current_turn updated
   game_matches.state_version incremented
   game_matches.updated_at updated
   pending_notifications created (move_update) for opponent

5. Match End
   game_matches.status = completed
   game_matches.result = winner/draw
   user_game_scores inserted (winner score=1, loser score=0)
   pending_notifications created (game_end) for both players
   AsyncScoreTracker picks up user_game_scores
   user_game_stats aggregated (total_plays++, best/avg/total updated, wins/losses/draws++)

6. Leaderboard Query
   read from user_game_stats (fast) -> sort by best_score -> return top N


================================================================================
4. GUI SPECIFICS
================================================================================

FRONTEND ARCHITECTURE (DEFOLD)

Defold Game Loop (60 FPS):
  1. Input: Check keypresses (W, A, S, D, ESC, Enter, T)
  2. Logic: Update player position, game state
  3. Physics: Collision detection
  4. Render: Draw sprites, text, UI
  5. Receive: WebSocket messages, input events, collisions


SCREEN FLOW

Login Screen

Render:
  - Input field (text invisible for privacy)
  - "Enter username" prompt

Input:
  - Type username (invisible)
  - Press Enter -> Send username + default hash to /api/login
  - Wait for 200 response

Result:
  - 200 OK: Transition to Quad screen
  - 401: Show "Login failed", retry


Quad (Hub) Screen

Render:
  - Player sprite in center
  - 6 tables around player
  - Stats indicator (T key hint)

Input:
  - WASD/Arrows: Move player
  - Enter (at table): Enter game
  - T: Open stats page
  - ESC: Logout

Tables:
  Table 1  Pong
  Table 2  Goose Hunt
  Table 3  Tic Tac Toe
  Table 4  Puzzle
  Table 5: Spaceship
  Table 6: Runner


TTT Game Screen

Render:
  - 3x3 board grid
  - Player marks (X/O)
  - Opponent marks
  - Turn indicator
  - Score display

Input:
  - Mouse click on cell -> Send /ws move message
  - Wait for gameUpdate message
  - ESC: Forfeit, return to Quad

Network:
  - Receive: gameUpdate (opponent move, board)
  - Send: move (your move)
  - Receive: gameEnd (winner)


Runner Screen

Render:
  - Player sprite
  - Platform/obstacles
  - Score counter

Input:
  - Space/Touch: Jump
  - ESC: Return to Quad

Network:
  - On load: Auto-submit score=1
  - Trigger stats aggregation


Stats Screen (React)

Render:
  - Table of games (columns: Game, Plays, Best, Avg, Total)
  - Data for: runner, tictactoe, goose, pong, puzzle, space, quad
  - Auto-refresh every 10s
  - Manual "Load" button

Network:
  - GET /api/user/{username}/all-stats
  - Parse response JSON
  - Render rows
  - Repeat every 10s


UI STATE MODELS

Player State:
username, currentScreen, position (x, y), direction (dx, dy)
stats: {runner: {plays, best, avg, total}, ...}

Game Match State (TTT):
matchId, player1, player2, currentPlayer, board (3x3), winner, gameState

Stats State (React):
username, stats, lastUpdated, loading


================================================================================
5. HARDWARE & SOFTWARE REQUIREMENTS
================================================================================

BACKEND REQUIREMENTS

Software:
- Java Runtime: JDK 17+ (verify: java -version)
- Gradle: 7.x+ (wrapper included)
- Database: H2 embedded (auto-created at database/ttt_game.*)

Hardware:
- CPU: 1+ cores (recommended: 2+ for concurrent requests)
- RAM: 256MB minimum, 512MB typical, 1GB recommended
- Disk: 100MB+ free (H2 database 10-50MB, Gradle cache 50-200MB)

Network:
- Ports: 8080 (WebSocket), 8081 (HTTP REST)
- Bandwidth: ~1 KB per score submission (negligible)


FRONTEND REQUIREMENTS (DEFOLD CLIENT)

Software:
- Defold: 1.11.x (latest stable)
- Editor: VS Code or Defold IDE
- Extensions: extension-websocket, extension-spine (included in game.project)

Hardware:
- CPU: 1+ cores (recommended: 2+ for input/physics threading)
- RAM: 200MB minimum, 300-500MB typical
- GPU: Integrated GPU sufficient (dedicated recommended)

Operating System:
- Windows: 10+ (verified on Windows 11)
- macOS: 10.13+ (tested on M1/M2)
- Linux: Ubuntu 18.04+ (tested on Ubuntu 20.04)

Network:
- Connectivity: Reach backend server (localhost or LAN IP)
- Localhost: 8080, 8081 for local development
- LAN IP: e.g., 192.168.1.100:8081 for network play


STATS UI (REACT PAGE)

Software:
- Browser: Chrome, Edge, Safari, Firefox (modern versions, ES6+ support)
- HTML/CSS/JavaScript: Bundled in single file (open main/Quad/stats_react.html)

Hardware:
- CPU: Minimal (fetch + render only)
- RAM: <50MB for React app
- Disk: Not required

Network:
- File protocol: file:///path/to/stats_react.html (works)
- HTTP server: python -m http.server 8000 then http://localhost:8000


DEVELOPMENT MACHINE (RECOMMENDED)

OS: Windows 11 / macOS 12+ / Ubuntu 22.04
RAM: 16GB (8GB works, less comfortable)
CPU: 4+ cores (Gradle build, Defold editor, browser)
Disk: 256GB SSD

Software Stack:
- JDK 17+ (backend)
- Gradle 7.x (included)
- Defold 1.11.x (frontend)
- VS Code + extensions
- PowerShell 5.1+

Optional:
- Java IDE (Eclipse, IntelliJ)
- Git client
- Postman (API testing)
- Browser DevTools


DEPLOYMENT SCENARIOS

Local Development (Single Machine)
- Laptop with 8GB RAM, 2 cores
- Defold client running
- Java backend running (ports 8080, 8081)
- Browser open to stats page
- All on localhost

LAN Play (Multiple Machines)
- Server PC: Run backend (e.g., 192.168.1.100:8081)
- Client PCs: Configure backend URL to server IP
- Defold: Update API endpoints
- Browser: Access from any machine
- All on same local network

Production Deployment
- Linux server (cloud or on-prem)
- CPU: 2+ cores
- RAM: 2GB (JVM + DB, multiple concurrent)
- Disk: 50GB (database growth, logs)
- Requirements: JDK 17+, firewall rules, TLS certificates, reverse proxy, backups


MINIMUM VS. RECOMMENDED SPECS

Backend JVM: 256MB min, 1GB recommended
Defold Client: 300MB min, 500MB recommended
Database: 10MB min, 100MB recommended
CPU: 1 core min, 4+ cores recommended
Network: 1 Mbps min, 10 Mbps recommended
Storage (Dev): 256MB min, 1GB recommended


TIME ESTIMATES

DEVELOPMENT (COMPLETED)

Phase 1 - Backend setup: 2-3 days -> Complete
Phase 2 - REST API endpoints: 3-4 days -> Complete
Phase 3 - WebSocket server: 2-3 days -> Complete
Phase 4 - AsyncScoreTracker: 1-2 days -> Complete
Phase 5 - Database schema: 2 days -> Complete
Phase 6 - Defold frontend: 3-4 days -> Complete
Phase 7 - Games integration: 5-7 days -> Complete
Phase 8 - TTT game logic: 2-3 days -> Complete
Phase 9 - Stats UI: 1-2 days -> Complete
Phase 10 - Testing: 3-5 days -> Complete

Total: 24-33 days estimated, Complete


DEPLOYMENT TIME

Extract files: 15 min
Backend build: 5 min
Frontend build: 3 min
Database init: <1 min
Start backend: <1 min
Start client: <1 min
Verify: 5 min

Total: ~30 minutes (first-time)


FUTURE ENHANCEMENTS

Multiplayer lobby: 2-3 days, Medium
User profiles: 1-2 days, Low
Global leaderboard: 1 day, Low
Replay system: 2-3 days, Medium
Authentication (bcrypt): 1-2 days, Low
HTTPS/TLS: 1 day, Low
Admin dashboard: 2-3 days, Medium
Database sharding: 5-7 days, Hard
Mobile app: 10-14 days, Very Hard


================================================================================
SUMMARY: ALL COMPONENTS
================================================================================

Component: Backend API
Protocol: HTTP/REST
Port: 8081
Hardware: 1+ core, 256MB RAM
Software: Java 17+, H2
Latency: <100ms

Component: WebSocket
Protocol: WS
Port: 8080
Hardware: 1+ core, 256MB RAM
Software: Java 17+
Latency: <10ms

Component: Defold Client
Protocol: REST + WS
Port: N/A
Hardware: 1+ core, 300MB RAM
Software: Defold 1.11.x
Latency: N/A

Component: Stats UI
Protocol: HTTP GET
Port: N/A
Hardware: Minimal
Software: Browser
Latency: <500ms

Component: Database
Protocol: N/A (local)
Port: N/A
Hardware: N/A
Software: H2 embedded
Latency: <1ms queries

================================================================================
