package com.stanstate.ttt;
import com.google.gson.*;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;

public class Server extends WebSocketServer {
  private static WebSocketNotifier wsNotifier;
  
  public Server(int port) { 
    super(new InetSocketAddress(port)); 
    if (wsNotifier == null) {
      wsNotifier = new WebSocketNotifier();
    }
  }
  
  public static WebSocketNotifier getNotifier() {
    return wsNotifier;
  }
  
  @Override 
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    System.out.println("=== WebSocket onOpen ===");
    System.out.println("Client connected: " + conn.getRemoteSocketAddress());
    
    try {
      // Send welcome message
      String welcomeMessage = "{\"t\":\"server_hello\",\"msg\":\"Server can send to client\"}";
      conn.send(welcomeMessage);
      System.out.println("Welcome message sent to client");
      
    } catch (Exception e) {
      System.out.println("Error in onOpen: " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  @Override 
  public void onMessage(WebSocket conn, String message) {
    System.out.println("=== WebSocket onMessage ===");
    System.out.println("Received from " + conn.getRemoteSocketAddress() + ": " + message);
    
    try {
      // Parse message to get session ID and handle different message types
      if (message.contains("sessionId") || message.contains("t")) {
        System.out.println("Processing structured message...");
        Gson gson = new Gson();
        JsonObject msg = gson.fromJson(message, JsonObject.class);
        
        // Handle heartbeat responses
        if (msg.has("t") && "heartbeat_response".equals(msg.get("t").getAsString())) {
          if (msg.has("sessionId")) {
            String sessionId = msg.get("sessionId").getAsString();
            wsNotifier.handleHeartbeatResponse(sessionId);
            System.out.println("Processed heartbeat response from " + sessionId);
          }
          return;
        }
        
        // Handle session registration
        if (msg.has("sessionId")) {
          String sessionId = msg.get("sessionId").getAsString();
          System.out.println("Extracted sessionId: " + sessionId);
          wsNotifier.registerConnection(sessionId, conn);
          System.out.println("Registered WebSocket for session: " + sessionId);
          
          // Send enhanced welcome message with connection info
          JsonObject response = new JsonObject();
          response.addProperty("t", "connection_established");
          response.addProperty("sessionId", sessionId);
          response.addProperty("message", "WebSocket connection established successfully");
          response.addProperty("timestamp", System.currentTimeMillis());
          response.addProperty("serverVersion", "1.0.0");
          conn.send(response.toString());
        }
        
        // Handle connection quality feedback
        if (msg.has("t") && "connection_quality".equals(msg.get("t").getAsString())) {
          if (msg.has("sessionId") && msg.has("quality")) {
            String sessionId = msg.get("sessionId").getAsString();
            double quality = msg.get("quality").getAsDouble();
            updateConnectionQuality(sessionId, quality);
          }
        }
        
      } else {
        // Handle legacy or simple messages
        System.out.println("Message does not contain sessionId: " + message);
        JsonObject response = new JsonObject();
        response.addProperty("t", "echo");
        response.addProperty("original", message);
        response.addProperty("timestamp", System.currentTimeMillis());
        conn.send(response.toString());
      }
      
    } catch (JsonSyntaxException e) {
      System.err.println("Invalid JSON received: " + e.getMessage());
      try {
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("t", "error");
        errorResponse.addProperty("message", "Invalid JSON format");
        errorResponse.addProperty("timestamp", System.currentTimeMillis());
        conn.send(errorResponse.toString());
      } catch (Exception se) {
        System.err.println("Failed to send error response: " + se.getMessage());
      }
    } catch (Exception e) {
      System.err.println("Error processing message: " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  @Override 
  public void onClose(WebSocket conn, int code, String reason, boolean remote) { 
    System.out.println("=== WebSocket onClose ===");
    System.out.println("WebSocket disconnected: " + conn.getRemoteSocketAddress() + 
                      " code=" + code + " reason=" + reason);
    
    // Find and unregister the session by checking all connections
    String disconnectedSession = findSessionByConnection(conn);
    if (disconnectedSession != null) {
      wsNotifier.unregisterConnection(disconnectedSession);
      System.out.println("Unregistered session: " + disconnectedSession);
    }
  }
  
  @Override 
  public void onError(WebSocket conn, Exception ex) { 
    System.err.println("=== WebSocket onError ===");
    System.err.println("WebSocket error: " + ex.getMessage());
    ex.printStackTrace();
    
    // Try to find and handle the errored connection
    String sessionId = findSessionByConnection(conn);
    if (sessionId != null) {
      System.err.println("Error on session: " + sessionId);
      // Update connection quality to reflect error
      updateConnectionQuality(sessionId, 0.1);
    }
  }
  
  @Override
  public void onStart() {
    System.out.println("=== WebSocket Server Started ===");
    System.out.println("Server listening on: " + getAddress());
    setConnectionLostTimeout(0);
    setConnectionLostTimeout(100);
  }
  
  private String findSessionByConnection(WebSocket conn) {
    // This is a simplified approach - in a real system we'd need better tracking
    // For now, return null and rely on cleanup tasks
    return null;
  }
  
  private void updateConnectionQuality(String sessionId, double quality) {
    try {
      DatabaseManager dbManager = DatabaseManager.getInstance();
      try (java.sql.Connection conn = dbManager.getConnection()) {
        java.sql.PreparedStatement stmt = conn.prepareStatement(
          "UPDATE connection_health SET connection_quality = ? WHERE session_id = ?"
        );
        stmt.setDouble(1, quality);
        stmt.setString(2, sessionId);
        stmt.executeUpdate();
        System.out.println("Updated connection quality for " + sessionId + " to " + quality);
      }
    } catch (java.sql.SQLException e) {
      System.err.println("Failed to update connection quality: " + e.getMessage());
    }
  }
}