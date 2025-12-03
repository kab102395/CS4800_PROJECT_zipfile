# Project Guide (Draft)

This document describes how to run, test, and understand the multi‑game Defold client and the Java backend. Fill in the placeholders for per‑game descriptions/controls once finalized.

## Overview
- Defold client with multiple mini-games: Goose Hunt, Pong, Puzzle, Space Shooter, Tic Tac Toe, Quad hub, Runner. Linker removed.
- Java backend (H2) for auth/scores/stats; REST via Spark, WebSocket server for TTT notifications/heartbeats.
- Stats UI: React page at `main/Quad/stats_react.html` (opened via T in Quad).

## Prerequisites
- **Frontend**: Defold 1.11.x. Dependencies already listed in `game.project` (`extension-websocket`, `extension-spine` though runner currently uses sprites).
- **Backend**: Java 17+, Gradle wrapper. H2 embedded (no external DB install needed). Ports: WS 8080, HTTP 8081 by default.

## Running the Client (Defold)
1. Open `game.project` in Defold.
2. Target collection: `/main/collections/main.collection`.
3. Run/Debug.
4. Login screen: input text is invisible; type desired username (case-sensitive), press Enter to save/login, then ESC to go to the Quad.
5. Quad:
   - Press T to open stats (launches React stats page).
   - Approach a table and press Enter to launch:
     - Table1: Pong
     - Table2: Goose Hunt
     - Table3: Tic Tac Toe
     - Table4: Puzzle
     - Table5: (Linker removed)
     - Table6: Runner

## Running the Backend
- CLI: `cd tictactoe/java-server && ./gradlew installDist` then `build/install/ttt-server/bin/ttt-server.bat` (ports 8080/8081) or run `./gradlew run` with args `<wsPort> <httpPort>`.
- Eclipse: Import Gradle project, main class `com.stanstate.ttt.Main`, Java 17 runtime, run.
- DB: H2 file DB at `database/ttt_game.*` (auto-created/migrated). MySQL compatibility mode.

## Backend Architecture
- Connection pool (10 connections), single-writer queue for scores (`AsyncScoreTracker`), MERGE upserts for idempotent session/match/state writes.
- Tables (see `docs/schema_viewer.html` for quick reference):
  - `users`, `user_game_scores`, `user_game_stats`, `player_sessions`, `game_matches`, `game_moves`, `pending_notifications`, `connection_health`, `lobby_state`, `player_stats`, `schema_version`.
- Stats endpoint: `/api/user/{username}/all-stats` (auto-creates missing users). Runner is included.
- Fallback aggregation: if stats row missing but scores exist, backend computes plays/best/avg/total on the fly.

## Known Issues (update as fixed)
- Login UI: invisible text; case-sensitive; requires Enter then ESC.
- Runner visuals: placeholder sprites/background; clean-up needed.
- Runner stats: score save confirmed; stats sometimes lag—use exact username; backend now aggregates missing stats.
- ESC behavior: previously exited unexpectedly; monitor after proxy/name fixes.

## Per-Game Controls (summary)
- **Goose Hunt**: Move with WASD/Arrow keys; Space/Click to fire/throw; ESC to return to Quad.
- **Pong**: Player paddle up/down with W/S or Arrow Up/Down; ESC to return.
- **Puzzle**: Mouse/touch to interact and place pieces; ESC to return.
- **Space Shooter**: Move with WASD/Arrow keys; Space to fire; ESC to submit score and return.
- **Tic Tac Toe**: Mouse/touch to place mark; ESC to return.
- **The Quad (hub)**: Move with WASD/Arrow keys; Enter at a table to launch game; T to open stats popup.
- **Runner**: Move/jump with Space/Touch (single-button jump); auto-submits score=1 on entry for API test; ESC to return.

## Stats UI
- React stats page `main/Quad/stats_react.html` (opened via `stats_launcher.html`). Auto-refresh 10s; manual Load. Games shown: goose, tictactoe, puzzle, pong, space, quad, runner.

## Schema Viewer
- See `docs/schema_viewer.html` for a static HTML overview of DB tables/columns. Serve via file:// or any static host.

## Testing
- See `docs/TEST_PLAN.md` and `tests/run-tests.ps1` for test guidance and scripts.
