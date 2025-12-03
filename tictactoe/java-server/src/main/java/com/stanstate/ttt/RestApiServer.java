package com.stanstate.ttt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import spark.Spark;
import java.util.UUID;

public class RestApiServer {
    private final GameService gameService;
    private final Gson gson;
    private final int port;
    private final AsyncScoreTracker scoreTracker;
    
    public RestApiServer(int port) {
        this.port = port;
        
        // Get the WebSocket notifier, fallback to new instance if needed
        WebSocketNotifier notifier = Server.getNotifier();
        if (notifier == null) {
            System.out.println("Warning: Server.getNotifier() returned null, creating new WebSocketNotifier");
            notifier = new WebSocketNotifier();
        }
        
        DatabaseManager dbManager = DatabaseManager.getInstance();
        this.gameService = new GameService(dbManager, notifier);
        this.scoreTracker = new AsyncScoreTracker(dbManager);
        this.gson = new Gson();
        System.out.println("RestApiServer initialized with port " + port);
    }
    
    public void start() {
        Spark.port(port);
        setupRoutes();
        System.out.println("HTTP API server started on port " + port);
    }
    
    private void setupRoutes() {
        // Enable CORS for all routes
        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        // Health check endpoint
        Spark.get("/health", (request, response) -> {
            response.type("application/json");
            JsonObject healthResponse = new JsonObject();
            healthResponse.addProperty("status", "ok");
            healthResponse.addProperty("message", "Server is running");
            return gson.toJson(healthResponse);
        });
        
        // Handle preflight requests
        Spark.options("/*", (request, response) -> {
            return "OK";
        });
        
        // Join game endpoint
        Spark.post("/api/join", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== JOIN REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String sessionId = requestBody.has("sessionId") ? 
                    requestBody.get("sessionId").getAsString() : 
                    UUID.randomUUID().toString();
                String playerName = requestBody.has("name") ? 
                    requestBody.get("name").getAsString() : 
                    "Player-" + sessionId.substring(0, 8);
                
                System.out.println("Session ID: " + sessionId + ", Player: " + playerName);
                
                String matchId = gameService.joinGame(sessionId, playerName).get();
                System.out.println("Join result - Match ID: " + matchId);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("sessionId", sessionId);
                responseJson.addProperty("matchId", matchId);
                
                return gson.toJson(responseJson);
            } catch (Exception e) {
                System.out.println("JOIN ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Get available matches endpoint
        Spark.get("/api/matches", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== GET MATCHES REQUEST ===");
                
                // Get available matches from GameService
                JsonObject matchesResponse = gameService.getAvailableMatches().get();
                System.out.println("Available matches response: " + matchesResponse);
                
                return gson.toJson(matchesResponse);
            } catch (Exception e) {
                System.out.println("GET MATCHES ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Create match endpoint
        Spark.post("/api/create-match", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== CREATE MATCH REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String sessionId = requestBody.get("sessionId").getAsString();
                String playerName = requestBody.get("playerName").getAsString();
                String matchName = requestBody.has("matchName") ? requestBody.get("matchName").getAsString() : playerName + "'s Game";
                
                System.out.println("Create match - Session: " + sessionId + ", Player: " + playerName + ", Match: " + matchName);
                
                String matchId = gameService.createMatch(sessionId, playerName, matchName).get();
                System.out.println("Created match: " + matchId);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("matchId", matchId);
                responseJson.addProperty("message", "Match created successfully");
                
                return gson.toJson(responseJson);
            } catch (Exception e) {
                System.out.println("CREATE MATCH ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Join specific match endpoint
        Spark.post("/api/join-match", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== JOIN SPECIFIC MATCH REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String sessionId = requestBody.get("sessionId").getAsString();
                String playerName = requestBody.get("playerName").getAsString();
                String matchId = requestBody.get("matchId").getAsString();
                
                System.out.println("Join specific match - Session: " + sessionId + ", Player: " + playerName + ", Match: " + matchId);
                
                boolean success = gameService.joinSpecificMatch(sessionId, playerName, matchId).get();
                System.out.println("Join specific match result: " + success);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", success);
                if (success) {
                    responseJson.addProperty("matchId", matchId);
                    responseJson.addProperty("message", "Joined match successfully");
                } else {
                    responseJson.addProperty("error", "Failed to join match - may be full or not exist");
                }
                
                return gson.toJson(responseJson);
            } catch (Exception e) {
                System.out.println("JOIN SPECIFIC MATCH ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Make move endpoint
        Spark.post("/api/move", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== MOVE REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String sessionId = requestBody.get("sessionId").getAsString();
                String matchId = requestBody.get("matchId").getAsString();
                int cell = requestBody.get("cell").getAsInt();
                
                System.out.println("Move request - Session: " + sessionId + ", Match: " + matchId + ", Cell: " + cell);
                
                boolean success = gameService.makeMove(sessionId, matchId, cell).get();
                System.out.println("Move result: " + success);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", success);
                
                return gson.toJson(responseJson);
            } catch (Exception e) {
                System.out.println("MOVE ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Game state polling endpoint - replaces WebSocket notifications
        Spark.get("/api/game-state/:sessionId", (request, response) -> {
            response.type("application/json");
            
            try {
                String sessionId = request.params(":sessionId");
                System.out.println("=== GAME STATE POLL ===");
                System.out.println("Session: " + sessionId);
                
                // Get current game state for this session
                JsonObject gameState = gameService.getGameStateForSession(sessionId).get();
                System.out.println("Game state response: " + gameState);
                
                return gson.toJson(gameState);
            } catch (Exception e) {
                System.out.println("GAME STATE POLL ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Player statistics endpoint
        Spark.get("/api/stats/:playerName", (request, response) -> {
            response.type("application/json");
            
            try {
                String playerName = request.params(":playerName");
                System.out.println("=== PLAYER STATS REQUEST ===");
                System.out.println("Player: " + playerName);
                
                JsonObject stats = gameService.getPlayerStats(playerName).get();
                System.out.println("Stats response: " + stats);
                
                return gson.toJson(stats);
            } catch (Exception e) {
                System.out.println("PLAYER STATS ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });

        // Health check
        Spark.get("/api/health", (req, res) -> {
            res.type("application/json");
            JsonObject health = new JsonObject();
            health.addProperty("status", "OK");
            health.addProperty("timestamp", System.currentTimeMillis());
            return gson.toJson(health);
        });
        
        // ==================== USER AUTHENTICATION ENDPOINTS ====================
        
        // Register new user
        Spark.post("/api/register", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== REGISTER REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String username = requestBody.get("username").getAsString();
                String passwordHash = requestBody.get("passwordHash").getAsString();
                String email = requestBody.has("email") ? requestBody.get("email").getAsString() : "";
                
                DatabaseManager db = DatabaseManager.getInstance();
                
                // Check if user already exists
                if (db.userExists(username)) {
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("success", false);
                    errorResponse.addProperty("error", "Username already exists");
                    response.status(409);
                    return gson.toJson(errorResponse);
                }
                
                Integer userId = db.registerUser(username, passwordHash, email);
                
                if (userId != null) {
                    JsonObject successResponse = new JsonObject();
                    successResponse.addProperty("success", true);
                    successResponse.addProperty("userId", userId);
                    successResponse.addProperty("username", username);
                    System.out.println("User registered successfully: " + username + " (ID: " + userId + ")");
                    return gson.toJson(successResponse);
                } else {
                    throw new Exception("Failed to create user");
                }
            } catch (Exception e) {
                System.out.println("REGISTER ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Login user
        Spark.post("/api/login", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== LOGIN REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String username = requestBody.get("username").getAsString();
                String passwordHash = requestBody.get("passwordHash").getAsString();
                
                DatabaseManager db = DatabaseManager.getInstance();
                Integer userId = db.authenticateUser(username, passwordHash);
                
                if (userId != null) {
                    JsonObject successResponse = new JsonObject();
                    successResponse.addProperty("success", true);
                    successResponse.addProperty("userId", userId);
                    successResponse.addProperty("username", username);
                    System.out.println("User logged in successfully: " + username + " (ID: " + userId + ")");
                    return gson.toJson(successResponse);
                } else {
                    JsonObject errorResponse = new JsonObject();
                    errorResponse.addProperty("success", false);
                    errorResponse.addProperty("error", "Invalid username or password");
                    response.status(401);
                    System.out.println("Login failed for user: " + username);
                    return gson.toJson(errorResponse);
                }
            } catch (Exception e) {
                System.out.println("LOGIN ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Get user stats
        Spark.get("/api/user/:userId/stats", (request, response) -> {
            response.type("application/json");
            
            try {
                int userId = Integer.parseInt(request.params(":userId"));
                System.out.println("=== USER STATS REQUEST ===");
                System.out.println("User ID: " + userId);
                
                DatabaseManager db = DatabaseManager.getInstance();
                String stats = db.getUserStats(userId);
                
                return stats;
            } catch (Exception e) {
                System.out.println("USER STATS ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Submit game score by username (MUST be before :userId route for correct matching)
        Spark.post("/api/user/:username/score", (request, response) -> {
            response.type("application/json");
            
            try {
                String username = request.params(":username");
                long requestStartTime = System.currentTimeMillis();
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String gameName = requestBody.get("gameName").getAsString();
                int score = requestBody.get("score").getAsInt();
                int level = requestBody.has("level") ? requestBody.get("level").getAsInt() : 1;
                
                // Queue score by username - user creation/lookup happens in async writer thread
                // This ensures NO blocking database operations in the REST thread
                boolean queued = scoreTracker.queueScoreByUsername(username, gameName, score, level);
                
                if (!queued) {
                    throw new Exception("Score queue is full");
                }
                
                long duration = System.currentTimeMillis() - requestStartTime;
                
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Score queued for async processing");
                successResponse.addProperty("username", username);
                successResponse.addProperty("queued", true);
                successResponse.addProperty("processingTimeMs", duration);
                
                if (duration > 50) {
                    System.out.println("Score queued " + duration + "ms for user " + username + " in " + gameName);
                }
                
                return gson.toJson(successResponse);
            } catch (Exception e) {
                System.out.println("SUBMIT SCORE ERROR: " + e.getMessage());
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Submit game score by numeric user ID
        Spark.post("/api/user/:userId/score", (request, response) -> {
            response.type("application/json");
            
            try {
                int userId = Integer.parseInt(request.params(":userId"));
                long requestStartTime = System.currentTimeMillis();
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String gameName = requestBody.get("gameName").getAsString();
                int score = requestBody.get("score").getAsInt();
                int level = requestBody.has("level") ? requestBody.get("level").getAsInt() : 1;
                
                // Queue score for async processing instead of blocking on database write
                boolean queued = scoreTracker.queueScore(userId, gameName, score, level, "user-" + userId);
                
                if (!queued) {
                    throw new Exception("Score queue is full");
                }
                
                long duration = System.currentTimeMillis() - requestStartTime;
                
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Score submitted for processing");
                successResponse.addProperty("userId", userId);
                successResponse.addProperty("queued", true);
                successResponse.addProperty("processingTimeMs", duration);
                
                return gson.toJson(successResponse);
            } catch (Exception e) {
                System.out.println("SUBMIT SCORE ERROR: " + e.getMessage());
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Get top scores for a game
        Spark.get("/api/leaderboard/:gameName", (request, response) -> {
            response.type("application/json");
            
            try {
                String gameName = request.params(":gameName");
                int limit = request.queryParams("limit") != null ? 
                    Integer.parseInt(request.queryParams("limit")) : 10;
                
                System.out.println("=== LEADERBOARD REQUEST ===");
                System.out.println("Game: " + gameName + ", Limit: " + limit);
                
                DatabaseManager db = DatabaseManager.getInstance();
                String leaderboard = db.getTopScores(gameName, limit);
                
                return leaderboard;
            } catch (Exception e) {
                System.out.println("LEADERBOARD ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Get user's per-game statistics
        Spark.get("/api/user/:userId/game-stats", (request, response) -> {
            response.type("application/json");
            
            try {
                int userId = Integer.parseInt(request.params(":userId"));
                DatabaseManager db = DatabaseManager.getInstance();
                String gameStats = db.getUserGameStats(userId);
                
                return gameStats;
            } catch (Exception e) {
                System.out.println("USER GAME STATS ERROR: " + e.getMessage());
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Get async score tracker statistics
        Spark.get("/api/admin/score-queue-stats", (request, response) -> {
            response.type("application/json");
            return gson.toJson(scoreTracker.getStats());
        });
        
        // Get all user stats across all games (goose, tictactoe, puzzle, pong, space)
        Spark.get("/api/user/:username/all-stats", (request, response) -> {
            response.type("application/json");
            
            try {
                String username = request.params(":username");
                DatabaseManager db = DatabaseManager.getInstance();
                boolean existedBefore = db.userExists(username);
                
                // Look up user; auto-create if missing so new names are handled immediately
                Integer userId = db.ensureUserExists(username);
                if (userId == null) {
                    JsonObject notFound = new JsonObject();
                    notFound.addProperty("success", false);
                    notFound.addProperty("error", "User could not be created for stats");
                    response.status(500);
                    return gson.toJson(notFound);
                }
                
                // Get per-game stats
                JsonObject allStats = new JsonObject();
                allStats.addProperty("username", username);
                allStats.addProperty("userId", userId);
                allStats.addProperty("autoCreated", !existedBefore);
                
                // Games to track
                String[] games = {"goose", "tictactoe", "puzzle", "pong", "space", "quad", "runner"};
                JsonObject gameStats = new JsonObject();
                
                for (String game : games) {
                    String stats = db.getGameStats(userId, game);
                    if (stats != null) {
                        gameStats.add(game, gson.fromJson(stats, com.google.gson.JsonObject.class));
                    } else {
                        System.out.println("Stats missing for user " + username + " game " + game + " (possible no plays yet)");
                    }
                }
                
                allStats.add("games", gameStats);
                allStats.addProperty("success", true);
                return gson.toJson(allStats);
                
            } catch (Exception e) {
                System.out.println("ALL STATS ERROR: " + e.getMessage());
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Admin endpoint: Clean up bad scores for a user/game combination
        Spark.post("/api/admin/cleanup/:username/:game", (request, response) -> {
            response.type("application/json");
            
            try {
                String username = request.params(":username");
                String gameName = request.params(":game");
                
                DatabaseManager db = DatabaseManager.getInstance();
                Integer userId = db.getUserIdByUsername(username);
                
                if (userId == null) {
                    JsonObject notFound = new JsonObject();
                    notFound.addProperty("success", false);
                    notFound.addProperty("error", "User not found: " + username);
                    response.status(404);
                    return gson.toJson(notFound);
                }
                
                // Delete all scores for this game
                int deletedScores = db.deleteGameScores(userId, gameName);
                
                // Delete stats for this game
                int deletedStats = db.deleteGameStats(userId, gameName);
                
                System.out.println("CLEANUP: Deleted " + deletedScores + " scores and " + deletedStats + " stat records for user " + username + " in game " + gameName);
                
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Cleanup completed");
                successResponse.addProperty("scoresDeleted", deletedScores);
                successResponse.addProperty("statsDeleted", deletedStats);
                successResponse.addProperty("username", username);
                successResponse.addProperty("game", gameName);
                
                return gson.toJson(successResponse);
            } catch (Exception e) {
                System.out.println("CLEANUP ERROR: " + e.getMessage());
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        System.out.println("REST API server started on http://localhost:" + port);
    }
    
    public void stop() {
        scoreTracker.shutdown();
        Spark.stop();
    }
}
