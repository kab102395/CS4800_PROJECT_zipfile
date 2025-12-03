package com.stanstate.ttt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache for game states to improve performance
 * Reduces database hits during active gameplay
 */
public class GameStateCache {
    private static GameStateCache instance;
    private final ConcurrentHashMap<String, GameState> activeGames;
    private final ConcurrentHashMap<String, PlayerSession> activeSessions;
    private final DatabaseManager dbManager;
    private final ScheduledExecutorService syncScheduler;
    
    // Game state cache entry
    public static class GameState {
        public String matchId;
        public String sessionId1;
        public String sessionId2;
        public String board;
        public String currentPlayer;
        public String status;
        public String result;
        public long lastUpdate;
        public String player1Name;
        public String player2Name;
        
        public GameState(String matchId, String sessionId1, String sessionId2, 
                        String player1Name, String player2Name) {
            this.matchId = matchId;
            this.sessionId1 = sessionId1;
            this.sessionId2 = sessionId2;
            this.player1Name = player1Name;
            this.player2Name = player2Name;
            this.board = ".........";
            this.currentPlayer = "X";
            this.status = "active";
            this.result = "ongoing";
            this.lastUpdate = System.currentTimeMillis();
        }
    }
    
    // Player session cache entry
    public static class PlayerSession {
        public String sessionId;
        public String playerName;
        public String connectionStatus;
        public long lastHeartbeat;
        public String currentMatch;
        
        public PlayerSession(String sessionId, String playerName) {
            this.sessionId = sessionId;
            this.playerName = playerName;
            this.connectionStatus = "connected";
            this.lastHeartbeat = System.currentTimeMillis();
        }
    }
    
    private GameStateCache() {
        this.activeGames = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        this.dbManager = DatabaseManager.getInstance();
        this.syncScheduler = Executors.newScheduledThreadPool(2);
        
        // Enable selective database sync for completed games only
        // No background sync - only sync when games finish
        
        // Keep session cleanup for expired sessions every 5 minutes
        syncScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 300, 300, TimeUnit.SECONDS);
        
        System.out.println("GameStateCache initialized with SELECTIVE database sync for completed games");
    }
    
    public static synchronized GameStateCache getInstance() {
        if (instance == null) {
            instance = new GameStateCache();
        }
        return instance;
    }
    
    // Game state operations (in-memory)
    public void createGame(String matchId, String sessionId1, String sessionId2, 
                          String player1Name, String player2Name) {
        GameState game = new GameState(matchId, sessionId1, sessionId2, player1Name, player2Name);
        activeGames.put(matchId, game);
        
        // Update player sessions
        PlayerSession session1 = activeSessions.get(sessionId1);
        PlayerSession session2 = activeSessions.get(sessionId2);
        if (session1 != null) session1.currentMatch = matchId;
        if (session2 != null) session2.currentMatch = matchId;
        
        System.out.println("Created active game: " + matchId + " with players " + player1Name + " vs " + player2Name);
    }
    
    public void createGameWaiting(String matchId, String sessionId1, String player1Name) {
        GameState game = new GameState(matchId, sessionId1, null, player1Name, null);
        game.status = "waiting";  // Set status to waiting
        activeGames.put(matchId, game);
        
        // Update player session
        PlayerSession session1 = activeSessions.get(sessionId1);
        if (session1 != null) session1.currentMatch = matchId;
        
        System.out.println("Created waiting game: " + matchId + " hosted by " + player1Name);
    }
    
    public GameState getGame(String matchId) {
        return activeGames.get(matchId);
    }
    
    public GameState getGameBySessionId(String sessionId) {
        for (GameState game : activeGames.values()) {
            if (sessionId.equals(game.sessionId1) || sessionId.equals(game.sessionId2)) {
                return game;
            }
        }
        return null;
    }
    
    public java.util.List<GameState> getWaitingGames() {
        return activeGames.values().stream()
            .filter(game -> "waiting".equals(game.status))
            .collect(java.util.stream.Collectors.toList());
    }
    
    public boolean makeMove(String matchId, int position, String playerId) {
        GameState game = activeGames.get(matchId);
        if (game == null || !"active".equals(game.status)) {
            return false;
        }
        
        // Validate move
        if (position < 0 || position > 8) {
            return false;
        }
        
        if (game.board.charAt(position) != '.') {
            return false; // Position already taken
        }
        
        // Check if it's the player's turn
        String expectedPlayer = "X".equals(game.currentPlayer) ? game.sessionId1 : game.sessionId2;
        
        if (!playerId.equals(expectedPlayer)) {
            return false; // Not this player's turn
        }
        
        // Make the move
        StringBuilder boardBuilder = new StringBuilder(game.board);
        boardBuilder.setCharAt(position, game.currentPlayer.charAt(0));
        game.board = boardBuilder.toString();
        
        // Check for game end
        String result = checkGameResult(game.board);
        if (!"ongoing".equals(result)) {
            game.status = "finished";
            game.result = result;
            
            // Save completed game to database and update player stats
            saveCompletedGameToDatabase(game);
            System.out.println("Game " + matchId + " finished with result: " + result);
        } else {
            // Switch turns
            game.currentPlayer = "X".equals(game.currentPlayer) ? "O" : "X";
        }
        
        game.lastUpdate = System.currentTimeMillis();
        System.out.println("Move made in game " + matchId + ": position " + position + " by " + playerId);
        return true;
    }
    
    public void updatePlayerSession(String sessionId, String playerName) {
        PlayerSession session = activeSessions.computeIfAbsent(sessionId, 
            k -> new PlayerSession(sessionId, playerName));
        session.lastHeartbeat = System.currentTimeMillis();
        session.connectionStatus = "connected";
        if (playerName != null) {
            session.playerName = playerName;
        }
    }
    
    private String checkGameResult(String board) {
        // Check rows
        for (int i = 0; i < 3; i++) {
            if (board.charAt(i*3) != '.' && 
                board.charAt(i*3) == board.charAt(i*3+1) && 
                board.charAt(i*3+1) == board.charAt(i*3+2)) {
                return board.charAt(i*3) + "_wins";
            }
        }
        
        // Check columns
        for (int i = 0; i < 3; i++) {
            if (board.charAt(i) != '.' && 
                board.charAt(i) == board.charAt(i+3) && 
                board.charAt(i+3) == board.charAt(i+6)) {
                return board.charAt(i) + "_wins";
            }
        }
        
        // Check diagonals
        if (board.charAt(0) != '.' && board.charAt(0) == board.charAt(4) && board.charAt(4) == board.charAt(8)) {
            return board.charAt(0) + "_wins";
        }
        if (board.charAt(2) != '.' && board.charAt(2) == board.charAt(4) && board.charAt(4) == board.charAt(6)) {
            return board.charAt(2) + "_wins";
        }
        
        // Check for draw
        if (!board.contains(".")) {
            return "draw";
        }
        
        return "ongoing";
    }
    
    private void syncToDatabase() {
        try {
            System.out.println("Syncing " + activeGames.size() + " games to database...");
            
            for (GameState game : activeGames.values()) {
                try (Connection conn = dbManager.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "MERGE INTO game_matches " +
                        "(match_id, player1_session, player2_session, status, current_turn, board, result, last_move_at) " +
                        "KEY(match_id) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)"
                    );
                    stmt.setString(1, game.matchId);
                    stmt.setString(2, game.sessionId1);
                    stmt.setString(3, game.sessionId2);
                    stmt.setString(4, game.status);
                    stmt.setString(5, game.currentPlayer);
                    stmt.setString(6, game.board);
                    stmt.setString(7, game.result);
                    stmt.executeUpdate();
                } catch (Exception e) {
                    System.err.println("Error syncing game " + game.matchId + ": " + e.getMessage());
                }
            }
            
            // Sync player sessions
            for (PlayerSession session : activeSessions.values()) {
                try (Connection conn = dbManager.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "MERGE INTO player_sessions " +
                        "(session_id, player_name, connection_status, last_heartbeat) " +
                        "KEY(session_id) VALUES (?, ?, ?, CURRENT_TIMESTAMP)"
                    );
                    stmt.setString(1, session.sessionId);
                    stmt.setString(2, session.playerName);
                    stmt.setString(3, session.connectionStatus);
                    stmt.executeUpdate();
                } catch (Exception e) {
                    System.err.println("Error syncing session " + session.sessionId + ": " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error during database sync: " + e.getMessage());
        }
    }
    
    // Save completed game and update player statistics
    private void saveCompletedGameToDatabase(GameState game) {
        try (Connection conn = dbManager.getConnection()) {
            // Ensure player sessions exist for FK integrity
            if (game.sessionId1 != null) {
                PreparedStatement sessionUpsert1 = conn.prepareStatement(
                    "MERGE INTO player_sessions (session_id, player_name, connection_status, last_heartbeat) " +
                    "KEY(session_id) VALUES (?, ?, 'connected', CURRENT_TIMESTAMP)"
                );
                sessionUpsert1.setString(1, game.sessionId1);
                sessionUpsert1.setString(2, game.player1Name);
                sessionUpsert1.executeUpdate();
            }
            if (game.sessionId2 != null) {
                PreparedStatement sessionUpsert2 = conn.prepareStatement(
                    "MERGE INTO player_sessions (session_id, player_name, connection_status, last_heartbeat) " +
                    "KEY(session_id) VALUES (?, ?, 'connected', CURRENT_TIMESTAMP)"
                );
                sessionUpsert2.setString(1, game.sessionId2);
                sessionUpsert2.setString(2, game.player2Name);
                sessionUpsert2.executeUpdate();
            }

            // Save game match to database
            PreparedStatement matchStmt = conn.prepareStatement(
                "MERGE INTO game_matches " +
                "(match_id, player1_session, player2_session, status, current_turn, board, result, last_move_at) " +
                "KEY(match_id) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)"
            );
            matchStmt.setString(1, game.matchId);
            matchStmt.setString(2, game.sessionId1);
            matchStmt.setString(3, game.sessionId2);
            matchStmt.setString(4, game.status);
            matchStmt.setString(5, game.currentPlayer);
            matchStmt.setString(6, game.board);
            matchStmt.setString(7, game.result);
            matchStmt.executeUpdate();
            
            System.out.println("Saved completed game " + game.matchId + " to database");
            
            // Update player statistics
            updatePlayerStats(conn, game.player1Name, game.result, "X");
            updatePlayerStats(conn, game.player2Name, game.result, "O");
            
        } catch (Exception e) {
            System.err.println("Error saving completed game " + game.matchId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Update individual player statistics
    private void updatePlayerStats(Connection conn, String playerName, String gameResult, String playerMark) {
        try {
            // Create or get existing stats
            PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT * FROM player_stats WHERE player_name = ?"
            );
            checkStmt.setString(1, playerName);
            ResultSet rs = checkStmt.executeQuery();
            
            int totalGames = 0;
            int wins = 0;
            int losses = 0;
            int draws = 0;
            
            if (rs.next()) {
                totalGames = rs.getInt("total_games");
                wins = rs.getInt("wins");
                losses = rs.getInt("losses");
                draws = rs.getInt("draws");
            }
            
            // Update stats based on game result
            totalGames++;
            
            if ("draw".equals(gameResult)) {
                draws++;
            } else if (gameResult.startsWith(playerMark)) {
                // Player won (result is "X_wins" or "O_wins")
                wins++;
            } else {
                // Player lost
                losses++;
            }
            
            // Calculate win rate
            double winRate = totalGames > 0 ? (double) wins / totalGames : 0.0;
            
            // Save updated stats
            PreparedStatement updateStmt = conn.prepareStatement(
                "MERGE INTO player_stats " +
                "(player_name, total_games, wins, losses, draws, win_rate, last_game) " +
                "KEY(player_name) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)"
            );
            updateStmt.setString(1, playerName);
            updateStmt.setInt(2, totalGames);
            updateStmt.setInt(3, wins);
            updateStmt.setInt(4, losses);
            updateStmt.setInt(5, draws);
            updateStmt.setDouble(6, winRate);
            updateStmt.executeUpdate();
            
            System.out.println("Updated stats for " + playerName + ": " + wins + "W-" + losses + "L-" + draws + "D (WR: " + String.format("%.1f%%", winRate * 100) + ")");
            
        } catch (Exception e) {
            System.err.println("Error updating stats for " + playerName + ": " + e.getMessage());
        }
    }
    
    private void cleanupExpiredSessions() {
        long expiredThreshold = System.currentTimeMillis() - (5 * 60 * 1000); // 5 minutes
        
        activeSessions.entrySet().removeIf(entry -> {
            PlayerSession session = entry.getValue();
            if (session.lastHeartbeat < expiredThreshold) {
                System.out.println("Removing expired session: " + session.sessionId);
                return true;
            }
            return false;
        });
        
        // Remove finished games older than 1 hour
        long gameExpiredThreshold = System.currentTimeMillis() - (60 * 60 * 1000); // 1 hour
        activeGames.entrySet().removeIf(entry -> {
            GameState game = entry.getValue();
            if ("finished".equals(game.status) && game.lastUpdate < gameExpiredThreshold) {
                System.out.println("Removing old finished game: " + game.matchId);
                return true;
            }
            return false;
        });
    }
    
    public void shutdown() {
        if (syncScheduler != null) {
            syncScheduler.shutdown();
            try {
                if (!syncScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    syncScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                syncScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
