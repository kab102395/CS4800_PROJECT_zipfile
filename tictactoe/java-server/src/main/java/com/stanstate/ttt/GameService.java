package com.stanstate.ttt;

import com.google.gson.JsonObject;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;

public class GameService {
    private final DatabaseManager dbManager;
    private final ExecutorService gameThreadPool;
    private final WebSocketNotifier wsNotifier;
    private final GameStateCache gameCache;
    
    // Enhanced lobby system with database persistence
    private volatile String waitingPlayerId = null;
    private volatile String waitingPlayerName = null;
    private final Object lobbyLock = new Object();
    
    public GameService() {
        this.dbManager = DatabaseManager.getInstance();
        this.wsNotifier = new WebSocketNotifier();
        this.gameCache = GameStateCache.getInstance();
        this.gameThreadPool = Executors.newFixedThreadPool(8); // Increased thread pool
        loadLobbyState(); // Load persistent lobby state
    }
    
    public GameService(DatabaseManager dbManager, WebSocketNotifier wsNotifier) {
        this.dbManager = dbManager;
        this.wsNotifier = wsNotifier;
        this.gameCache = GameStateCache.getInstance();
        this.gameThreadPool = Executors.newFixedThreadPool(8);
        loadLobbyState();
    }
    
    private void loadLobbyState() {
        // Initialize with empty lobby state - cache-based approach
        waitingPlayerId = null;
        waitingPlayerName = null;
        System.out.println("Lobby state initialized (cache-based)");
    }
    
    public CompletableFuture<String> joinGame(String sessionId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("=== GameService.joinGame START (Cache-Based) ===");
            System.out.println("SessionId: " + sessionId + ", PlayerName: " + playerName);
            
            // Update player session in cache
            gameCache.updatePlayerSession(sessionId, playerName);
            
            // CRITICAL: Use synchronized block to prevent race conditions
            synchronized (lobbyLock) {
                System.out.println("Acquired lobby lock. Current waiting player: " + waitingPlayerId);
                
                if (waitingPlayerId == null) {
                    // First player - put them in lobby
                    waitingPlayerId = sessionId;
                    waitingPlayerName = playerName;
                    System.out.println("Player " + playerName + " put in lobby (first player)");
                    
                    return "waiting";
                    
                } else if (!waitingPlayerId.equals(sessionId)) {
                    // Second player - create match immediately
                    String matchId = UUID.randomUUID().toString();
                    String player1Id = waitingPlayerId;
                    String player1Name = waitingPlayerName;
                    
                    System.out.println("Second player " + playerName + " arrived! Creating match with " + player1Name);
                    
                    // Clear lobby BEFORE creating match (important!)
                    waitingPlayerId = null;
                    waitingPlayerName = null;
                    
                    // Create match in cache
                    gameCache.createGame(matchId, player1Id, sessionId, player1Name, playerName);
                    
                    System.out.println("Match created: " + matchId);
                    System.out.println("Player1 (X): " + player1Name + " (" + player1Id + ")");
                    System.out.println("Player2 (O): " + playerName + " (" + sessionId + ")");
                    
                    return matchId;
                } else {
                    // Same player trying to join again - handle gracefully
                    System.out.println("Same player " + playerName + " rejoining - keeping in lobby");
                    return "waiting";
                }
            } // End synchronized block
        }, gameThreadPool);
    }
    
    public CompletableFuture<Boolean> makeMove(String sessionId, String matchId, int cellPosition) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("=== GameService.makeMove START (Cache-Based) ===");
            System.out.println("SessionId: " + sessionId + ", MatchId: " + matchId + ", Cell: " + cellPosition);
            
            // Use cache for fast move processing
            boolean success = gameCache.makeMove(matchId, cellPosition, sessionId);
            
            if (success) {
                System.out.println("MOVE SUCCESSFUL: Position " + cellPosition + " for player " + sessionId);
                
                // Check if game ended
                GameStateCache.GameState game = gameCache.getGame(matchId);
                if (game != null && "finished".equals(game.status)) {
                    System.out.println("GAME FINISHED: " + game.result);
                }
            } else {
                System.out.println("MOVE FAILED: Invalid move for position " + cellPosition);
            }
            
            return success;
        }, gameThreadPool);
    }
    
    private String checkGameStatus(String board) {
        // Check rows, columns, diagonals
        String[][] b = new String[3][3];
        for (int i = 0; i < 9; i++) {
            b[i/3][i%3] = String.valueOf(board.charAt(i));
        }
        
        // Check wins
        for (int i = 0; i < 3; i++) {
            if (!b[i][0].equals(".") && b[i][0].equals(b[i][1]) && b[i][1].equals(b[i][2])) return b[i][0] + "_wins";
            if (!b[0][i].equals(".") && b[0][i].equals(b[1][i]) && b[1][i].equals(b[2][i])) return b[0][i] + "_wins";
        }
        if (!b[0][0].equals(".") && b[0][0].equals(b[1][1]) && b[1][1].equals(b[2][2])) return b[0][0] + "_wins";
        if (!b[0][2].equals(".") && b[0][2].equals(b[1][1]) && b[1][1].equals(b[2][0])) return b[0][2] + "_wins";
        
        // Check draw
        if (!board.contains(".")) return "draw";
        
        return "ongoing";
    }
    
    private void notifyWaiting(String sessionId, String playerName) {
        try {
            JsonObject message = new JsonObject();
            message.addProperty("t", "waiting");  // Use "t" to match client expectations
            message.addProperty("message", "Waiting for opponent...");
            message.addProperty("playerName", playerName);
            message.addProperty("role", "waiting");
            wsNotifier.sendToSession(sessionId, message);
        } catch (Exception e) {
            System.out.println("Error notifying waiting: " + e.getMessage());
        }
    }
    
    private void notifyMatchStart(String matchId, String player1Id, String player1Name, String player2Id, String player2Name) {
        try {
            // Notify Player 1 (X)
            JsonObject p1Message = new JsonObject();
            p1Message.addProperty("t", "match_start");  // Use "t" to match client expectations
            p1Message.addProperty("matchId", matchId);
            p1Message.addProperty("yourMark", "X");
            p1Message.addProperty("yourTurn", true);
            p1Message.addProperty("yourName", player1Name);
            p1Message.addProperty("opponentName", player2Name);
            p1Message.addProperty("board", "         ");
            p1Message.addProperty("message", "Match started! You are X - make your move!");
            wsNotifier.sendToSession(player1Id, p1Message);
            
            // Notify Player 2 (O)  
            JsonObject p2Message = new JsonObject();
            p2Message.addProperty("t", "match_start");  // Use "t" to match client expectations
            p2Message.addProperty("matchId", matchId);
            p2Message.addProperty("yourMark", "O");
            p2Message.addProperty("yourTurn", false);
            p2Message.addProperty("yourName", player2Name);
            p2Message.addProperty("opponentName", player1Name);
            p2Message.addProperty("board", "         ");
            p2Message.addProperty("message", "Match started! You are O - waiting for " + player1Name + " to move...");
            wsNotifier.sendToSession(player2Id, p2Message);
            
        } catch (Exception e) {
            System.out.println("Error notifying match start: " + e.getMessage());
        }
    }
    
    private void notifyGameState(String matchId, String board, String currentTurn, String player1Id, String player2Id) {
        try {
            System.out.println("=== NOTIFYING GAME STATE ===");
            System.out.println("Match: " + matchId + ", Board: " + board + ", Turn: " + currentTurn);
            System.out.println("Player1: " + player1Id + ", Player2: " + player2Id);
            
            // Notify both players with updated game state
            JsonObject p1Message = new JsonObject();
            p1Message.addProperty("t", "game_update");  // Use "t" to match client expectations
            p1Message.addProperty("matchId", matchId);
            p1Message.addProperty("board", board);
            p1Message.addProperty("yourTurn", currentTurn.equals("X"));
            p1Message.addProperty("message", currentTurn.equals("X") ? "Your turn!" : "Waiting for opponent...");
            
            System.out.println("Sending to Player1 (" + player1Id + "): " + p1Message.toString());
            wsNotifier.sendToSession(player1Id, p1Message);
            
            JsonObject p2Message = new JsonObject();
            p2Message.addProperty("t", "game_update");  // Use "t" to match client expectations
            p2Message.addProperty("matchId", matchId);
            p2Message.addProperty("board", board);
            p2Message.addProperty("yourTurn", currentTurn.equals("O"));
            p2Message.addProperty("message", currentTurn.equals("O") ? "Your turn!" : "Waiting for opponent...");
            
            System.out.println("Sending to Player2 (" + player2Id + "): " + p2Message.toString());
            wsNotifier.sendToSession(player2Id, p2Message);
            
            System.out.println("=== GAME STATE NOTIFICATIONS SENT ===");
            
        } catch (Exception e) {
            System.out.println("Error notifying game state: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        gameThreadPool.shutdown();
    }
    
    // Enhanced notification methods with retry logic and redundancy
    private void notifyWaitingWithRetry(String sessionId, String playerName) {
        try {
            JsonObject message = new JsonObject();
            message.addProperty("t", "waiting");
            message.addProperty("message", "Waiting for opponent...");
            message.addProperty("playerName", playerName);
            message.addProperty("role", "waiting");
            message.addProperty("timestamp", System.currentTimeMillis());
            
            // Send immediately
            wsNotifier.sendToSession(sessionId, message);
            
            // Store as pending notification for redundancy
            wsNotifier.storePendingNotification(sessionId, "waiting", message.toString());
            
            System.out.println("Sent waiting notification to " + playerName + " with redundancy");
        } catch (Exception e) {
            System.err.println("Error in notifyWaitingWithRetry: " + e.getMessage());
            // Fallback - store as pending only
            try {
                JsonObject fallbackMessage = new JsonObject();
                fallbackMessage.addProperty("t", "waiting");
                fallbackMessage.addProperty("message", "Waiting for opponent...");
                fallbackMessage.addProperty("playerName", playerName);
                wsNotifier.storePendingNotification(sessionId, "waiting", fallbackMessage.toString());
            } catch (Exception fe) {
                System.err.println("Fallback notification also failed: " + fe.getMessage());
            }
        }
    }
    
    private void notifyMatchStartWithRetry(String matchId, String player1Id, String player1Name, String player2Id, String player2Name) {
        try {
            // Enhanced Player 1 (X) notification with redundancy
            JsonObject p1Message = new JsonObject();
            p1Message.addProperty("t", "match_start");
            p1Message.addProperty("matchId", matchId);
            p1Message.addProperty("yourMark", "X");
            p1Message.addProperty("yourTurn", true);
            p1Message.addProperty("yourName", player1Name);
            p1Message.addProperty("opponentName", player2Name);
            p1Message.addProperty("board", "         ");
            p1Message.addProperty("message", "Match started! You are X - make your move!");
            p1Message.addProperty("timestamp", System.currentTimeMillis());
            p1Message.addProperty("stateVersion", 1);
            
            // Send immediately and store as pending
            wsNotifier.sendToSession(player1Id, p1Message);
            wsNotifier.storePendingNotification(player1Id, "match_start", p1Message.toString());
            
            // Enhanced Player 2 (O) notification with redundancy
            JsonObject p2Message = new JsonObject();
            p2Message.addProperty("t", "match_start");
            p2Message.addProperty("matchId", matchId);
            p2Message.addProperty("yourMark", "O");
            p2Message.addProperty("yourTurn", false);
            p2Message.addProperty("yourName", player2Name);
            p2Message.addProperty("opponentName", player1Name);
            p2Message.addProperty("board", "         ");
            p2Message.addProperty("message", "Match started! You are O - waiting for " + player1Name + " to move...");
            p2Message.addProperty("timestamp", System.currentTimeMillis());
            p2Message.addProperty("stateVersion", 1);
            
            // Send immediately and store as pending
            wsNotifier.sendToSession(player2Id, p2Message);
            wsNotifier.storePendingNotification(player2Id, "match_start", p2Message.toString());
            
            System.out.println("Sent match start notifications with redundancy to both players");
            
        } catch (Exception e) {
            System.err.println("Error in notifyMatchStartWithRetry: " + e.getMessage());
            // Fallback - try to send basic notifications
            try {
                notifyMatchStart(matchId, player1Id, player1Name, player2Id, player2Name);
            } catch (Exception fe) {
                System.err.println("Fallback match start notification also failed: " + fe.getMessage());
            }
        }
    }
    
    // NEW: Get available matches for match browser (Cache-Based)
    public CompletableFuture<JsonObject> getAvailableMatches() {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("=== GameService.getAvailableMatches START (Cache-Based) ===");
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            
            com.google.gson.JsonArray matchesArray = new com.google.gson.JsonArray();
            
            // Get waiting games from cache
            var waitingGames = gameCache.getWaitingGames();
            
            for (var gameState : waitingGames) {
                JsonObject match = new JsonObject();
                match.addProperty("matchId", gameState.matchId);
                match.addProperty("hostName", gameState.player1Name);
                match.addProperty("createdAt", new java.util.Date(gameState.lastUpdate).toString());
                match.addProperty("playersCount", 1);
                match.addProperty("maxPlayers", 2);
                matchesArray.add(match);
            }
            
            response.add("matches", matchesArray);
            response.addProperty("totalMatches", matchesArray.size());
            
            System.out.println("Found " + matchesArray.size() + " available matches (cache-based)");
            return response;
            
        }, gameThreadPool);
    }
    
    // NEW: Create a new match that others can join (Cache-Based)
    public CompletableFuture<String> createMatch(String sessionId, String playerName, String matchName) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("=== GameService.createMatch START (Cache-Based) ===");
            System.out.println("SessionId: " + sessionId + ", PlayerName: " + playerName + ", MatchName: " + matchName);
            
            // Create new match with unique ID
            String matchId = UUID.randomUUID().toString();
            
            // Update player session in cache
            gameCache.updatePlayerSession(sessionId, playerName);
            
            // Create match in cache with waiting status (only one player initially)
            gameCache.createGameWaiting(matchId, sessionId, playerName);
            
            System.out.println("Created match: " + matchId + " hosted by " + playerName);
            
            return matchId;
        }, gameThreadPool);
    }
    
    // NEW: Join a specific existing match (Cache-Based)
    public CompletableFuture<Boolean> joinSpecificMatch(String sessionId, String playerName, String matchId) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("=== GameService.joinSpecificMatch START (Cache-Based) ===");
            System.out.println("SessionId: " + sessionId + ", PlayerName: " + playerName + ", MatchId: " + matchId);
            
            // Update player session in cache
            gameCache.updatePlayerSession(sessionId, playerName);
            
            // Get the game from cache
            GameStateCache.GameState game = gameCache.getGame(matchId);
            
            if (game == null) {
                System.out.println("JOIN SPECIFIC MATCH FAILED: Match not found");
                return false;
            }
            
            if (!"waiting".equals(game.status)) {
                System.out.println("JOIN SPECIFIC MATCH FAILED: Match status is " + game.status + " (not waiting)");
                return false;
            }
            
            if (game.sessionId2 != null) {
                System.out.println("JOIN SPECIFIC MATCH FAILED: Match already has 2 players");
                return false;
            }
            
            if (game.sessionId1.equals(sessionId)) {
                System.out.println("JOIN SPECIFIC MATCH FAILED: Cannot join own match");
                return false;
            }
            
            // Join the match as player 2
            game.sessionId2 = sessionId;
            game.player2Name = playerName;
            game.status = "active";
            game.lastUpdate = System.currentTimeMillis();
            
            System.out.println("Successfully joined match: " + matchId);
            System.out.println("Player1 (X): " + game.player1Name + " (" + game.sessionId1 + ")");
            System.out.println("Player2 (O): " + playerName + " (" + sessionId + ")");
            
            return true;
        }, gameThreadPool);
    }
    
    /**
     * NEW: Get current game state for a session - replaces WebSocket notifications
     */
    public CompletableFuture<JsonObject> getGameStateForSession(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("=== GET GAME STATE FOR SESSION (Cache-Based) ===");
            System.out.println("Session: " + sessionId);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("sessionId", sessionId);
            
            // Update player session heartbeat
            gameCache.updatePlayerSession(sessionId, "Player" + sessionId.substring(sessionId.length() - 4));
            
            // Find active match for this session
            GameStateCache.GameState game = gameCache.getGameBySessionId(sessionId);
            
            if (game != null) {
                System.out.println("Found active match: " + game.matchId);
                System.out.println("Status: " + game.status + ", Turn: " + game.currentPlayer);
                System.out.println("Board: " + game.board);
                
                response.addProperty("hasMatch", true);
                response.addProperty("matchId", game.matchId);
                response.addProperty("board", game.board);
                response.addProperty("status", game.status);
                response.addProperty("result", game.result != null ? game.result : "ongoing");
                
                // Determine if it's this player's turn
                boolean isPlayerOne = sessionId.equals(game.sessionId1);
                String playerMark = isPlayerOne ? "X" : "O";
                boolean isMyTurn = false;
                
                if ("active".equals(game.status)) {
                    isMyTurn = (isPlayerOne && "X".equals(game.currentPlayer)) || 
                              (!isPlayerOne && "O".equals(game.currentPlayer));
                }
                
                response.addProperty("yourTurn", isMyTurn);
                response.addProperty("yourMark", playerMark);
                response.addProperty("currentTurn", game.currentPlayer);
                
                String message;
                if ("waiting".equals(game.status)) {
                    message = "Waiting for opponent to join...";
                } else if ("active".equals(game.status)) {
                    message = isMyTurn ? "Your turn!" : "Waiting for opponent...";
                } else {
                    message = "Game finished: " + game.result;
                }
                response.addProperty("message", message);
                
                return response;
            } else {
                // No active match
                System.out.println("No active match found for session: " + sessionId);
                response.addProperty("hasMatch", false);
                response.addProperty("message", "No active match");
                
                return response;
            }
        }, gameThreadPool);
    }
    
    public CompletableFuture<JsonObject> getPlayerStats(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("=== GameService.getPlayerStats START ===");
            System.out.println("Player Name: " + playerName);
            
            JsonObject response = new JsonObject();
            
            try (Connection conn = dbManager.getConnection()) {
                String query = "SELECT * FROM player_stats WHERE player_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, playerName);
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            // Player stats exist
                            response.addProperty("success", true);
                            response.addProperty("found", true);
                            response.addProperty("playerName", rs.getString("player_name"));
                            response.addProperty("totalGames", rs.getInt("total_games"));
                            response.addProperty("wins", rs.getInt("wins"));
                            response.addProperty("losses", rs.getInt("losses"));
                            response.addProperty("draws", rs.getInt("draws"));
                            response.addProperty("winRate", rs.getDouble("win_rate"));
                            response.addProperty("lastGame", rs.getString("last_game"));
                            response.addProperty("createdAt", rs.getString("created_at"));
                            
                            System.out.println("Found stats for player: " + playerName);
                        } else {
                            // Player has no stats yet
                            response.addProperty("success", true);
                            response.addProperty("found", false);
                            response.addProperty("playerName", playerName);
                            response.addProperty("totalGames", 0);
                            response.addProperty("wins", 0);
                            response.addProperty("losses", 0);
                            response.addProperty("draws", 0);
                            response.addProperty("winRate", 0.0);
                            response.addProperty("message", "No games played yet");
                            
                            System.out.println("No stats found for player: " + playerName);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error getting player stats: " + e.getMessage());
                e.printStackTrace();
                response.addProperty("success", false);
                response.addProperty("error", e.getMessage());
            }
            
            return response;
        }, gameThreadPool);
    }
}