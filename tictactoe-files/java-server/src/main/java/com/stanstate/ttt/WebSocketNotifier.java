package com.stanstate.ttt;

import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

public class WebSocketNotifier {
    private final DatabaseManager dbManager;
    private final Map<String, WebSocket> sessionConnections;
    private final ScheduledExecutorService retryScheduler;
    private final ScheduledExecutorService heartbeatScheduler;
    
    public WebSocketNotifier() {
        this.dbManager = DatabaseManager.getInstance();
        this.sessionConnections = new ConcurrentHashMap<>();
        this.retryScheduler = Executors.newScheduledThreadPool(2);
        this.heartbeatScheduler = Executors.newScheduledThreadPool(1);
        
        // Start retry and heartbeat tasks
        startRetryTask();
        startHeartbeatTask();
    }
    
    public WebSocketNotifier(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.sessionConnections = new ConcurrentHashMap<>();
        this.retryScheduler = Executors.newScheduledThreadPool(2);
        this.heartbeatScheduler = Executors.newScheduledThreadPool(1);
        
        startRetryTask();
        startHeartbeatTask();
    }
    
    public void registerConnection(String sessionId, WebSocket connection) {
        sessionConnections.put(sessionId, connection);
        System.out.println("Registered WebSocket for session: " + sessionId);
        
        // Update connection status in database
        dbManager.updateHeartbeat(sessionId);
        
        // Send any pending messages that were waiting for this connection
        sendPendingMessages(sessionId);
        
        // Send connection confirmation
        try {
            JsonObject confirmMessage = new JsonObject();
            confirmMessage.addProperty("t", "connection_confirmed");
            confirmMessage.addProperty("sessionId", sessionId);
            confirmMessage.addProperty("timestamp", System.currentTimeMillis());
            connection.send(confirmMessage.toString());
        } catch (Exception e) {
            System.err.println("Failed to send connection confirmation: " + e.getMessage());
        }
    }
    
    public void unregisterConnection(String sessionId) {
        sessionConnections.remove(sessionId);
        System.out.println("Unregistered WebSocket for session: " + sessionId);
        
        // Update connection status in database
        try (Connection conn = dbManager.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_sessions SET connection_status = 'disconnected' WHERE session_id = ?"
            );
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update disconnection status: " + e.getMessage());
        }
    }
    
    // Enhanced message sending with retry logic and redundancy
    public void sendToSession(String sessionId, JsonObject message) {
        WebSocket connection = sessionConnections.get(sessionId);
        
        if (connection != null && connection.isOpen()) {
            try {
                String messageStr = message.toString();
                connection.send(messageStr);
                System.out.println("Sent message to " + sessionId + ": " + messageStr);
                
                // Update last successful communication
                dbManager.updateHeartbeat(sessionId);
                
            } catch (Exception e) {
                System.err.println("Failed to send message to " + sessionId + ": " + e.getMessage());
                // Store as pending for retry
                storePendingNotification(sessionId, message.get("t").getAsString(), message.toString());
            }
        } else {
            System.out.println("No active connection for " + sessionId + ", storing as pending");
            // Store message for when connection returns
            storePendingNotification(sessionId, message.get("t").getAsString(), message.toString());
        }
    }
    
    // Store notification in database for retry/redundancy
    public void storePendingNotification(String sessionId, String type, String data) {
        try (Connection conn = dbManager.getConnection()) {
            // H2 uses CURRENT_TIMESTAMP instead of datetime()
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO pending_notifications (session_id, notification_type, data, attempts, next_retry) " +
                "VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP + INTERVAL '5' SECOND)"
            );
            stmt.setString(1, sessionId);
            stmt.setString(2, type);
            stmt.setString(3, data);
            stmt.executeUpdate();
            System.out.println("Stored pending notification for " + sessionId + " type: " + type);
        } catch (SQLException e) {
            System.err.println("Failed to store pending notification: " + e.getMessage());
        }
    }
    
    public void notifyWaiting(String sessionId) {
        JsonObject notification = new JsonObject();
        notification.addProperty("t", "waiting");
        notification.addProperty("msg", "Waiting for opponent...");
        sendToSession(sessionId, notification);
    }
    
    public void notifyMatchStart(String matchId) {
        try (Connection conn = dbManager.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT player1_session, player2_session FROM game_matches WHERE match_id = ?"
            );
            stmt.setString(1, matchId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String player1 = rs.getString("player1_session");
                String player2 = rs.getString("player2_session");
                
                // Notify player 1 (X)
                JsonObject p1Notification = new JsonObject();
                p1Notification.addProperty("t", "match_start");
                p1Notification.addProperty("match_id", matchId);
                p1Notification.addProperty("your_mark", "X");
                p1Notification.addProperty("opponent_mark", "O");
                sendToSession(player1, p1Notification);
                
                // Notify player 2 (O)
                JsonObject p2Notification = new JsonObject();
                p2Notification.addProperty("t", "match_start");
                p2Notification.addProperty("match_id", matchId);
                p2Notification.addProperty("your_mark", "O");
                p2Notification.addProperty("opponent_mark", "X");
                sendToSession(player2, p2Notification);
                
                // Send initial game state
                notifyGameState(matchId, ".........", "X", "ongoing");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void notifyGameState(String matchId, String board, String nextTurn, String status) {
        try (Connection conn = dbManager.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT player1_session, player2_session FROM game_matches WHERE match_id = ?"
            );
            stmt.setString(1, matchId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String player1 = rs.getString("player1_session");
                String player2 = rs.getString("player2_session");
                
                JsonObject stateNotification = new JsonObject();
                if (status.equals("ongoing")) {
                    stateNotification.addProperty("t", "game_state");
                    stateNotification.addProperty("board", board);
                    stateNotification.addProperty("current_turn", nextTurn);
                    stateNotification.addProperty("status", "ongoing");
                } else {
                    stateNotification.addProperty("t", "game_over");
                    stateNotification.addProperty("board", board);
                    stateNotification.addProperty("status", status);
                }
                
                sendToSession(player1, stateNotification);
                sendToSession(player2, stateNotification);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Enhanced pending message system using database
    public void sendPendingMessages(String sessionId) {
        try (Connection conn = dbManager.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, notification_type, data FROM pending_notifications " +
                "WHERE session_id = ? AND delivered = FALSE ORDER BY created_at"
            );
            stmt.setString(1, sessionId);
            ResultSet rs = stmt.executeQuery();
            
            List<Integer> deliveredIds = new ArrayList<>();
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String data = rs.getString("data");
                
                try {
                    WebSocket connection = sessionConnections.get(sessionId);
                    if (connection != null && connection.isOpen()) {
                        connection.send(data);
                        deliveredIds.add(id);
                        System.out.println("Delivered pending message to " + sessionId + ": " + data);
                    } else {
                        break; // Connection lost, stop trying
                    }
                } catch (Exception e) {
                    System.err.println("Failed to deliver pending message: " + e.getMessage());
                    break;
                }
            }
            
            // Mark delivered messages
            if (!deliveredIds.isEmpty()) {
                for (int id : deliveredIds) {
                    PreparedStatement markDelivered = conn.prepareStatement(
                        "UPDATE pending_notifications SET delivered = TRUE WHERE id = ?"
                    );
                    markDelivered.setInt(1, id);
                    markDelivered.executeUpdate();
                }
                System.out.println("Marked " + deliveredIds.size() + " messages as delivered for " + sessionId);
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to send pending messages: " + e.getMessage());
        }
    }
    
    // Background task management
    private void startRetryTask() {
        retryScheduler.scheduleAtFixedRate(this::retryPendingNotifications, 10, 10, TimeUnit.SECONDS);
    }
    
    private void startHeartbeatTask() {
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeats, 30, 30, TimeUnit.SECONDS);
    }
    
    private void retryPendingNotifications() {
        try (Connection conn = dbManager.getConnection()) {
            // H2 uses CURRENT_TIMESTAMP instead of datetime()
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, session_id, notification_type, data, attempts " +
                "FROM pending_notifications " +
                "WHERE delivered = FALSE AND attempts < max_attempts AND next_retry <= CURRENT_TIMESTAMP"
            );
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String sessionId = rs.getString("session_id");
                String data = rs.getString("data");
                int attempts = rs.getInt("attempts");
                
                WebSocket connection = sessionConnections.get(sessionId);
                if (connection != null && connection.isOpen()) {
                    try {
                        connection.send(data);
                        
                        // Mark as delivered
                        PreparedStatement markDelivered = conn.prepareStatement(
                            "UPDATE pending_notifications SET delivered = TRUE WHERE id = ?"
                        );
                        markDelivered.setInt(1, id);
                        markDelivered.executeUpdate();
                        
                        System.out.println("Retry successful for session " + sessionId);
                        
                    } catch (Exception e) {
                        // Update retry count and schedule next retry
                        int nextRetrySeconds = (int) Math.pow(2, attempts + 1); // Exponential backoff
                        PreparedStatement updateRetry = conn.prepareStatement(
                            "UPDATE pending_notifications SET attempts = attempts + 1, " +
                            "next_retry = CURRENT_TIMESTAMP + INTERVAL ? SECOND WHERE id = ?"
                        );
                        updateRetry.setInt(1, nextRetrySeconds);
                        updateRetry.setInt(2, id);
                        updateRetry.executeUpdate();
                        
                        System.err.println("Retry failed for session " + sessionId + ", attempt " + (attempts + 1));
                    }
                } else {
                    // No connection, schedule for later retry
                    int nextRetrySeconds = 30; // Wait longer if no connection
                    PreparedStatement updateRetry = conn.prepareStatement(
                        "UPDATE pending_notifications SET next_retry = CURRENT_TIMESTAMP + INTERVAL ? SECOND WHERE id = ?"
                    );
                    updateRetry.setInt(1, nextRetrySeconds);
                    updateRetry.setInt(2, id);
                    updateRetry.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Retry task failed: " + e.getMessage());
        }
    }
    
    private void sendHeartbeats() {
        for (Map.Entry<String, WebSocket> entry : sessionConnections.entrySet()) {
            String sessionId = entry.getKey();
            WebSocket connection = entry.getValue();
            
            if (connection.isOpen()) {
                try {
                    JsonObject heartbeat = new JsonObject();
                    heartbeat.addProperty("t", "heartbeat");
                    heartbeat.addProperty("timestamp", System.currentTimeMillis());
                    connection.send(heartbeat.toString());
                    
                    // Update ping count in database
                    try (Connection conn = dbManager.getConnection()) {
                        PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE connection_health SET last_ping = CURRENT_TIMESTAMP, ping_count = ping_count + 1 " +
                            "WHERE session_id = ?"
                        );
                        stmt.setString(1, sessionId);
                        stmt.executeUpdate();
                    }
                    
                } catch (Exception e) {
                    System.err.println("Failed to send heartbeat to " + sessionId + ": " + e.getMessage());
                    // Mark connection as potentially dead
                    try (Connection conn = dbManager.getConnection()) {
                        PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE connection_health SET missed_pings = missed_pings + 1, " +
                            "connection_quality = connection_quality * 0.9 WHERE session_id = ?"
                        );
                        stmt.setString(1, sessionId);
                        stmt.executeUpdate();
                    } catch (SQLException se) {
                        System.err.println("Failed to update missed ping count: " + se.getMessage());
                    }
                }
            }
        }
    }
    
    public void handleHeartbeatResponse(String sessionId) {
        try (Connection conn = dbManager.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE connection_health SET last_pong = CURRENT_TIMESTAMP, " +
                "missed_pings = 0, connection_quality = MIN(1.0, connection_quality + 0.1) " +
                "WHERE session_id = ?"
            );
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
            
            // Update player session heartbeat as well
            dbManager.updateHeartbeat(sessionId);
            
        } catch (SQLException e) {
            System.err.println("Failed to handle heartbeat response: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        retryScheduler.shutdown();
        heartbeatScheduler.shutdown();
        try {
            if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryScheduler.shutdownNow();
            heartbeatScheduler.shutdownNow();
        }
    }
    
    // Helper method for Server class to access session connections
    public Map<String, WebSocket> getSessionConnections() {
        return sessionConnections;
    }
}