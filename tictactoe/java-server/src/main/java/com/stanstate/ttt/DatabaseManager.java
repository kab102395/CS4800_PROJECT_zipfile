package com.stanstate.ttt;

import java.sql.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseManager {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static DatabaseManager instance;
    private final ScheduledExecutorService cleanupScheduler;
    private ConnectionPool connectionPool;
    // Per-username locks to prevent race conditions in user registration
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    
    private DatabaseManager() {
        // Private constructor for singleton
        this.cleanupScheduler = Executors.newScheduledThreadPool(1);
        try {
            this.connectionPool = ConnectionPool.getInstance();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize connection pool", e);
        }
        startCleanupTask();
    }
    
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    public void initializeDatabase() {
        try (Connection conn = connectionPool.getConnection()) {
            // Check if database exists and get version
            int currentVersion = getDatabaseVersion(conn);
            System.out.println("Current database version: " + currentVersion);
            
            if (currentVersion == 0) {
                // Create fresh database with new schema
                createFreshDatabase(conn);
            } else {
                // Migrate existing database
                migrateDatabase(conn, currentVersion);
            }
            
            System.out.println("Database initialized successfully with enhanced schema");
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private int getDatabaseVersion(Connection conn) {
        try {
            // Create version table if it doesn't exist
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY
                )
            """);
            
            PreparedStatement stmt = conn.prepareStatement("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1");
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("version");
            }
            return 0;
        } catch (SQLException e) {
            return 0;
        }
    }
    
    private void createFreshDatabase(Connection conn) throws SQLException {
        System.out.println("Creating fresh database with enhanced schema...");
        
        // Enhanced player sessions table with connection tracking
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS player_sessions (
                session_id TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                websocket_id TEXT,
                connection_status TEXT DEFAULT 'connected',
                retry_count INTEGER DEFAULT 0
            )
        """);
        
        // Enhanced game matches table with state tracking
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS game_matches (
                match_id TEXT PRIMARY KEY,
                player1_session TEXT,
                player2_session TEXT,
                board TEXT DEFAULT '.........',
                current_turn TEXT DEFAULT 'X',
                status TEXT DEFAULT 'waiting',
                result TEXT DEFAULT 'ongoing',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_move_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                state_version INTEGER DEFAULT 1,
                FOREIGN KEY (player1_session) REFERENCES player_sessions(session_id),
                FOREIGN KEY (player2_session) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Enhanced game moves table with validation
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS game_moves (
                move_id INT AUTO_INCREMENT PRIMARY KEY,
                match_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                cell_position INTEGER NOT NULL,
                mark TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                state_version INTEGER DEFAULT 1,
                validated BOOLEAN DEFAULT FALSE,
                FOREIGN KEY (match_id) REFERENCES game_matches(match_id),
                FOREIGN KEY (session_id) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Enhanced pending notifications with retry logic
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS pending_notifications (
                id INT AUTO_INCREMENT PRIMARY KEY,
                session_id TEXT NOT NULL,
                notification_type TEXT NOT NULL,
                data TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                attempts INTEGER DEFAULT 0,
                max_attempts INTEGER DEFAULT 3,
                next_retry TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                delivered BOOLEAN DEFAULT FALSE,
                FOREIGN KEY (session_id) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Connection health monitoring table
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS connection_health (
                session_id TEXT PRIMARY KEY,
                last_ping TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_pong TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                ping_count INTEGER DEFAULT 0,
                missed_pings INTEGER DEFAULT 0,
                connection_quality REAL DEFAULT 1.0,
                FOREIGN KEY (session_id) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Lobby persistence table
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS lobby_state (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                waiting_player_id TEXT,
                waiting_player_name TEXT,
                waiting_since TIMESTAMP,
                FOREIGN KEY (waiting_player_id) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Player statistics table for tracking wins/losses/draws
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS player_stats (
                player_name TEXT PRIMARY KEY,
                total_games INTEGER DEFAULT 0,
                wins INTEGER DEFAULT 0,
                losses INTEGER DEFAULT 0,
                draws INTEGER DEFAULT 0,
                win_rate REAL DEFAULT 0.0,
                last_game TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // User accounts table for unified login system
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                email TEXT UNIQUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_login TIMESTAMP,
                is_active BOOLEAN DEFAULT TRUE
            )
        """);
        
        // User game scores table
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS user_game_scores (
                score_id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                game_name TEXT NOT NULL,
                score INTEGER NOT NULL,
                level INTEGER DEFAULT 1,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                duration_seconds INTEGER,
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )
        """);
        
        // User statistics aggregated table
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS user_stats (
                user_id INTEGER PRIMARY KEY,
                total_score INTEGER DEFAULT 0,
                total_games_played INTEGER DEFAULT 0,
                games_completed INTEGER DEFAULT 0,
                average_score REAL DEFAULT 0.0,
                last_played_game TEXT,
                last_played_at TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )
        """);
        
        // Per-game statistics table for tracking game-specific performance
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS user_game_stats (
                stat_id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                game_name TEXT NOT NULL,
                total_plays INTEGER DEFAULT 0,
                best_score INTEGER DEFAULT 0,
                average_score REAL DEFAULT 0.0,
                total_score INTEGER DEFAULT 0,
                wins INTEGER DEFAULT 0,
                losses INTEGER DEFAULT 0,
                draws INTEGER DEFAULT 0,
                last_played TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, game_name),
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )
        """);
        
        // Initialize lobby state
        conn.createStatement().execute("""
            MERGE INTO lobby_state (id, waiting_player_id, waiting_player_name, waiting_since) 
            KEY(id) VALUES (1, NULL, NULL, NULL)
        """);
        
        // Set database version
        conn.createStatement().execute("MERGE INTO schema_version (version) KEY(version) VALUES (5)");
        System.out.println("Fresh database created with version 5");
    }
    
    private void migrateDatabase(Connection conn, int currentVersion) throws SQLException {
        System.out.println("Migrating database from version " + currentVersion + "...");
        
        if (currentVersion < 2) {
            // Add missing columns to existing tables
            try {
                conn.createStatement().execute("ALTER TABLE player_sessions ADD COLUMN connection_status TEXT DEFAULT 'connected'");
                System.out.println("Added connection_status to player_sessions");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE player_sessions ADD COLUMN retry_count INTEGER DEFAULT 0");
                System.out.println("Added retry_count to player_sessions");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE pending_notifications ADD COLUMN attempts INTEGER DEFAULT 0");
                System.out.println("Added attempts to pending_notifications");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE pending_notifications ADD COLUMN delivered BOOLEAN DEFAULT FALSE");
                System.out.println("Added delivered to pending_notifications");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE game_matches ADD COLUMN result TEXT DEFAULT 'ongoing'");
                System.out.println("Added result to game_matches");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            // Create new tables if they don't exist
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS connection_health (
                    session_id TEXT PRIMARY KEY,
                    last_ping TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_pong TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    ping_count INTEGER DEFAULT 0,
                    missed_pings INTEGER DEFAULT 0,
                    connection_quality REAL DEFAULT 1.0
                )
            """);
            
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS lobby_state (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    waiting_player_id TEXT,
                    waiting_player_name TEXT,
                    waiting_since TIMESTAMP
                )
            """);
            
            conn.createStatement().execute("""
                MERGE INTO lobby_state (id, waiting_player_id, waiting_player_name, waiting_since) 
                KEY(id) VALUES (1, NULL, NULL, NULL)
            """);
        }
        
        if (currentVersion < 3) {
            // Add player statistics table for version 3
            System.out.println("Adding player statistics table for version 3...");
            
            try {
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        player_name TEXT PRIMARY KEY,
                        total_games INTEGER DEFAULT 0,
                        wins INTEGER DEFAULT 0,
                        losses INTEGER DEFAULT 0,
                        draws INTEGER DEFAULT 0,
                        win_rate REAL DEFAULT 0.0,
                        last_game TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);
                System.out.println("Player statistics table created successfully");
            } catch (SQLException e) {
                System.err.println("Failed to create player_stats table: " + e.getMessage());
            }
        }
        
        if (currentVersion < 4) {
            // Add user authentication and scoring tables for version 4
            System.out.println("Adding user authentication tables for version 4...");
            
            try {
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        user_id INT AUTO_INCREMENT PRIMARY KEY,
                        username TEXT UNIQUE NOT NULL,
                        password_hash TEXT NOT NULL,
                        email TEXT UNIQUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_login TIMESTAMP,
                        is_active BOOLEAN DEFAULT TRUE
                    )
                """);
                System.out.println("Users table created successfully");
            } catch (SQLException e) {
                System.err.println("Failed to create users table: " + e.getMessage());
            }
            
            try {
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS user_game_scores (
                        score_id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        game_name TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        level INTEGER DEFAULT 1,
                        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        duration_seconds INTEGER,
                        FOREIGN KEY (user_id) REFERENCES users(user_id)
                    )
                """);
                System.out.println("User game scores table created successfully");
            } catch (SQLException e) {
                System.err.println("Failed to create user_game_scores table: " + e.getMessage());
            }
            
            try {
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS user_stats (
                        user_id INTEGER PRIMARY KEY,
                        total_score INTEGER DEFAULT 0,
                        total_games_played INTEGER DEFAULT 0,
                        games_completed INTEGER DEFAULT 0,
                        average_score REAL DEFAULT 0.0,
                        last_played_game TEXT,
                        last_played_at TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(user_id)
                    )
                """);
                System.out.println("User stats table created successfully");
            } catch (SQLException e) {
                System.err.println("Failed to create user_stats table: " + e.getMessage());
            }
        }
        
        if (currentVersion < 5) {
            // Add per-game statistics table for version 5
            System.out.println("Adding per-game statistics table for version 5...");
            
            try {
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS user_game_stats (
                        stat_id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        game_name TEXT NOT NULL,
                        total_plays INTEGER DEFAULT 0,
                        best_score INTEGER DEFAULT 0,
                        average_score REAL DEFAULT 0.0,
                        total_score INTEGER DEFAULT 0,
                        wins INTEGER DEFAULT 0,
                        losses INTEGER DEFAULT 0,
                        draws INTEGER DEFAULT 0,
                        last_played TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(user_id, game_name),
                        FOREIGN KEY (user_id) REFERENCES users(user_id)
                    )
                """);
                System.out.println("Per-game statistics table created successfully");
            } catch (SQLException e) {
                System.err.println("Failed to create user_game_stats table: " + e.getMessage());
            }
            
            // Update schema version
            try {
                conn.createStatement().execute("MERGE INTO schema_version (version) KEY(version) VALUES (5)");
                System.out.println("Database migrated to version 5");
            } catch (SQLException e) {
                System.err.println("Failed to update schema version: " + e.getMessage());
            }
        }
    }
    
    private boolean isUniqueViolation(SQLException e) {
        String state = e.getSQLState();
        if (state != null && state.equals("23505")) { // Standard unique constraint violation
            return true;
        }
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("unique");
    }
    
    private boolean isLockingError(SQLException e) {
        String state = e.getSQLState();
        if (state != null && (state.equals("40001") || state.startsWith("HYT") || state.equals("90020"))) {
            return true;
        }
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("lock");
    }
    
    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }
    
    public ReadWriteLock getLock() {
        return lock;
    }
    
    private void startCleanupTask() {
        // Run cleanup every 30 seconds
        cleanupScheduler.scheduleAtFixedRate(this::cleanupDeadConnections, 30, 30, TimeUnit.SECONDS);
    }
    
    public void cleanupDeadConnections() {
        try (Connection conn = getConnection()) {
            // Mark connections as dead if no heartbeat for 2 minutes
            // H2 uses CURRENT_TIMESTAMP instead of datetime()
            PreparedStatement updateStale = conn.prepareStatement(
                "UPDATE player_sessions SET connection_status = 'disconnected' " +
                "WHERE connection_status = 'connected' AND last_heartbeat < CURRENT_TIMESTAMP - INTERVAL '2' MINUTE"
            );
            int staleCount = updateStale.executeUpdate();
            
            // Clean up old pending notifications (older than 10 minutes)
            PreparedStatement cleanNotifications = conn.prepareStatement(
                "DELETE FROM pending_notifications WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '10' MINUTE"
            );
            int cleanedNotifications = cleanNotifications.executeUpdate();
            
            // Reset lobby if waiting player is disconnected
            PreparedStatement resetLobby = conn.prepareStatement(
                "UPDATE lobby_state SET waiting_player_id = NULL, waiting_player_name = NULL, waiting_since = NULL " +
                "WHERE waiting_player_id IN (SELECT session_id FROM player_sessions WHERE connection_status = 'disconnected')"
            );
            resetLobby.executeUpdate();
            
            if (staleCount > 0 || cleanedNotifications > 0) {
                System.out.println("Cleanup: marked " + staleCount + " connections as stale, cleaned " + cleanedNotifications + " old notifications");
            }
        } catch (SQLException e) {
            System.err.println("Cleanup task failed: " + e.getMessage());
        }
    }
    
    public void updateHeartbeat(String sessionId) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_sessions SET last_heartbeat = CURRENT_TIMESTAMP, connection_status = 'connected' WHERE session_id = ?"
            );
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update heartbeat for " + sessionId + ": " + e.getMessage());
        }
    }
    
    // ==================== USER AUTHENTICATION METHODS ====================
    
    public Integer registerUser(String username, String passwordHash, String email) {
        // Get or create a lock for this specific username
        ReentrantLock userLock = userLocks.computeIfAbsent(username, k -> new ReentrantLock());
        
        // Acquire lock to serialize registration attempts for same username
        userLock.lock();
        try {
            // Double-check: user might have been created by another thread while waiting for lock
            Integer existingId = getUserIdByUsername(username);
            if (existingId != null) {
                System.out.println("User '" + username + "' already exists (ID: " + existingId + ")");
                return existingId;
            }
            
            // Now safely create the user
            int retries = 3;
            for (int i = 0; i < retries; i++) {
                try (Connection conn = getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS
                        );
                        stmt.setString(1, username);
                        stmt.setString(2, passwordHash);
                        stmt.setString(3, email);
                        stmt.executeUpdate();
                        
                        ResultSet rs = stmt.getGeneratedKeys();
                        if (rs.next()) {
                            int userId = rs.getInt(1);
                            // Initialize user stats in same transaction
                            initializeUserStats(conn, userId);
                            conn.commit();
                            System.out.println("User '" + username + "' created successfully with ID: " + userId);
                            return userId;
                        }
                        conn.commit();
                        return null;
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                } catch (SQLException e) {
                    // Handle UNIQUE constraint: shouldn't happen with lock, but be safe
                    if (isUniqueViolation(e)) {
                        System.out.println("UNEXPECTED: User '" + username + "' constraint violation despite lock");
                        Integer retryId = getUserIdByUsername(username);
                        if (retryId != null) {
                            return retryId;
                        }
                        return null;
                    }
                    // Handle database locked/timeouts: retry with backoff
                    if (isLockingError(e) && i < retries - 1) {
                        System.out.println("Database busy, retrying registration for '" + username + "' (attempt " + (i + 2) + "/" + retries + ")");
                        try {
                            Thread.sleep(100 * (i + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        System.err.println("Failed to register user '" + username + "': " + e.getMessage());
                        return null;
                    }
                }
            }
            return null;
        } finally {
            userLock.unlock();
        }
    }
    
    private void initializeUserStats(Connection conn, int userId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO user_stats (user_id) VALUES (?)"
        );
        stmt.setInt(1, userId);
        stmt.executeUpdate();
    }
    
    public Integer authenticateUser(String username, String passwordHash) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT user_id FROM users WHERE username = ? AND password_hash = ? AND is_active = TRUE"
            );
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                int userId = rs.getInt("user_id");
                // Update last login
                updateLastLogin(conn, userId);
                return userId;
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Failed to authenticate user: " + e.getMessage());
            return null;
        }
    }
    
    private void updateLastLogin(Connection conn, int userId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
            "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE user_id = ?"
        );
        stmt.setInt(1, userId);
        stmt.executeUpdate();
    }
    
    public Integer getUserIdByUsername(String username) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT user_id FROM users WHERE username = ? AND is_active = TRUE"
            );
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("user_id");
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Failed to get user ID: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Ensure a user exists for a given username (auto-provision if missing).
     * Useful for games that send scores/stats before explicit registration.
     */
    public Integer ensureUserExists(String username) {
        Integer existing = getUserIdByUsername(username);
        if (existing != null) {
            return existing;
        }
        System.out.println("Auto-provisioning user for stats: " + username);
        // Create with empty password hash for auto-provisioned stat users
        Integer created = registerUser(username, "", null);
        if (created == null) {
            System.err.println("Failed to auto-provision user for stats: " + username);
        }
        return created;
    }
    
    public boolean userExists(String username) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM users WHERE username = ? LIMIT 1"
            );
            stmt.setString(1, username);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }
    
    public void recordGameScore(int userId, String gameName, int score, int levelOrDuration) {
        System.out.println("DEBUG recordGameScore: START - userId=" + userId + ", game=" + gameName + ", score=" + score);
        int retries = 3;
        for (int i = 0; i < retries; i++) {
            try (Connection conn = getConnection()) {
                System.out.println("DEBUG: Got connection, attempt " + (i + 1) + "/" + retries);
                conn.setAutoCommit(false);
                try {
                    // Record the score
                    PreparedStatement scoreStmt = conn.prepareStatement(
                        "INSERT INTO user_game_scores (user_id, game_name, score, level) VALUES (?, ?, ?, ?)"
                    );
                    scoreStmt.setInt(1, userId);
                    scoreStmt.setString(2, gameName);
                    scoreStmt.setInt(3, score);
                    scoreStmt.setInt(4, levelOrDuration);
                    int rowsInserted = scoreStmt.executeUpdate();
                    System.out.println("DEBUG: Inserted " + rowsInserted + " score rows");
                    scoreStmt.close();
                    
                    // Update user stats (total across all games)
                    System.out.println("DEBUG: Calling updateUserStats");
                    updateUserStats(conn, userId);
                    System.out.println("DEBUG: updateUserStats complete");
                    
                    // Update per-game stats
                    System.out.println("DEBUG: Calling updateGameStats");
                    updateGameStats(conn, userId, gameName, score);
                    System.out.println("DEBUG: updateGameStats complete");
                    
                    conn.commit();
                    System.out.println("DEBUG: COMMIT successful - Score and stats recorded for user " + userId + " in game " + gameName + ": " + score);
                    return;
                } catch (SQLException e) {
                    System.err.println("DEBUG: ROLLBACK due to SQLException: " + e.getMessage());
                    e.printStackTrace();
                    conn.rollback();
                    if (isLockingError(e) && i < retries - 1) {
                        try {
                            Thread.sleep(100 * (i + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        System.err.println("ERROR: Failed to record game score after " + (i + 1) + " attempts: " + e.getMessage());
                        return;
                    }
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                System.err.println("ERROR: Connection error: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }
        System.err.println("ERROR: Failed to record game score after all retries");
    }
                
    private void updateUserStats(Connection conn, int userId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("""
            UPDATE user_stats SET
                total_score = (SELECT COALESCE(SUM(score), 0) FROM user_game_scores WHERE user_id = ?),
                total_games_played = (SELECT COUNT(*) FROM user_game_scores WHERE user_id = ?),
                average_score = (SELECT COALESCE(AVG(score), 0) FROM user_game_scores WHERE user_id = ?)
            WHERE user_id = ?
        """);
        stmt.setInt(1, userId);
        stmt.setInt(2, userId);
        stmt.setInt(3, userId);
        stmt.setInt(4, userId);
        stmt.executeUpdate();
    }
    
    private void updateGameStats(Connection conn, int userId, String gameName, int score) throws SQLException {
        try {
            System.out.println("DEBUG updateGameStats: userId=" + userId + ", game=" + gameName + ", score=" + score);
            
            // Step 1: Get current stats if they exist
            String selectSql = "SELECT total_plays, best_score, total_score FROM user_game_stats WHERE user_id = ? AND game_name = ?";
            PreparedStatement selectStmt = conn.prepareStatement(selectSql);
            selectStmt.setInt(1, userId);
            selectStmt.setString(2, gameName);
            ResultSet rs = selectStmt.executeQuery();
            
            int totalPlays;
            int bestScore;
            int totalScore;
            
            if (rs.next()) {
                // Record exists, update it
                System.out.println("DEBUG: Record exists, updating");
                totalPlays = rs.getInt("total_plays") + 1;
                bestScore = Math.max(rs.getInt("best_score"), score);
                totalScore = rs.getInt("total_score") + score;
                rs.close();
                selectStmt.close();
                
                // Update
                String updateSql = "UPDATE user_game_stats SET total_plays = ?, best_score = ?, total_score = ?, average_score = ?, last_played = CURRENT_TIMESTAMP WHERE user_id = ? AND game_name = ?";
                PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setInt(1, totalPlays);
                updateStmt.setInt(2, bestScore);
                updateStmt.setInt(3, totalScore);
                updateStmt.setDouble(4, (double) totalScore / totalPlays);
                updateStmt.setInt(5, userId);
                updateStmt.setString(6, gameName);
                int rows = updateStmt.executeUpdate();
                System.out.println("DEBUG: UPDATE affected " + rows + " rows");
                updateStmt.close();
            } else {
                // New record
                System.out.println("DEBUG: Record does not exist, inserting new");
                rs.close();
                selectStmt.close();
                
                String insertSql = "INSERT INTO user_game_stats (user_id, game_name, total_plays, best_score, total_score, average_score, last_played) VALUES (?, ?, 1, ?, ?, ?, CURRENT_TIMESTAMP)";
                PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                insertStmt.setInt(1, userId);
                insertStmt.setString(2, gameName);
                insertStmt.setInt(3, score);
                insertStmt.setInt(4, score);
                insertStmt.setDouble(5, (double) score);
                int rows = insertStmt.executeUpdate();
                System.out.println("DEBUG: INSERT affected " + rows + " rows");
                insertStmt.close();
            }
            System.out.println("DEBUG updateGameStats: SUCCESS");
        } catch (SQLException e) {
            System.err.println("updateGameStats ERROR: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            e.printStackTrace();
            throw e;
        }
    }
    
    public String getUserStats(int userId) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM user_stats WHERE user_id = ?"
            );
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return String.format("""
                    {"user_id":%d,"total_score":%d,"total_games_played":%d,"average_score":%.2f,"games_completed":%d}""",
                    rs.getInt("user_id"),
                    rs.getInt("total_score"),
                    rs.getInt("total_games_played"),
                    rs.getDouble("average_score"),
                    rs.getInt("games_completed")
                );
            }
            return "{\"error\":\"User not found\"}";
        } catch (SQLException e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
    
    public String getGameStats(int userId, String gameName) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT * FROM user_game_stats WHERE user_id = ? AND game_name = ?
            """);
            stmt.setInt(1, userId);
            stmt.setString(2, gameName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return String.format("""
                    {"game":"%s","plays":%d,"best":%d,"average":%.1f,"total":%d,"wins":%d,"losses":%d,"draws":%d}""",
                    gameName,
                    rs.getInt("total_plays"),
                    rs.getInt("best_score"),
                    rs.getDouble("average_score"),
                    rs.getInt("total_score"),
                    rs.getInt("wins"),
                    rs.getInt("losses"),
                    rs.getInt("draws")
                );
            }
            
            // No stats row; check if we have scores to surface possible data loss
            PreparedStatement countStmt = conn.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM user_game_scores WHERE user_id = ? AND game_name = ?"
            );
            countStmt.setInt(1, userId);
            countStmt.setString(2, gameName);
            ResultSet countRs = countStmt.executeQuery();
            int scoreCount = (countRs.next()) ? countRs.getInt("cnt") : 0;
            if (scoreCount > 0) {
                // Compute stats on the fly to avoid missing rows
                PreparedStatement aggStmt = conn.prepareStatement(
                    "SELECT COUNT(*) AS plays, MAX(score) AS best, COALESCE(AVG(score),0) AS avg, COALESCE(SUM(score),0) AS total " +
                    "FROM user_game_scores WHERE user_id = ? AND game_name = ?"
                );
                aggStmt.setInt(1, userId);
                aggStmt.setString(2, gameName);
                ResultSet aggRs = aggStmt.executeQuery();
                if (aggRs.next()) {
                    int plays = aggRs.getInt("plays");
                    if (plays <= 0) plays = 1; // ensure at least one play when scores exist
                    return String.format("""
                        {"game":"%s","plays":%d,"best":%d,"average":%.1f,"total":%d,"wins":0,"losses":0,"draws":0,"repaired":true}""",
                        gameName,
                        plays,
                        aggRs.getInt("best"),
                        aggRs.getDouble("avg"),
                        aggRs.getInt("total")
                    );
                }
            }
            return String.format("""
                {"game":"%s","plays":0,"best":0,"average":0.0,"total":0,"wins":0,"losses":0,"draws":0,"missing":true,"scoresFound":%d,"dataLost":false}""",
                gameName,
                scoreCount
            );
        } catch (SQLException e) {
            return null;
        }
    }
    
    public String getTopScores(String gameName, int limit) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT u.username, s.score, s.timestamp 
                FROM user_game_scores s
                JOIN users u ON s.user_id = u.user_id
                WHERE s.game_name = ?
                ORDER BY s.score DESC
                LIMIT ?
            """);
            stmt.setString(1, gameName);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append(String.format("""
                    {"username":"%s","score":%d,"timestamp":"%s"}""",
                    rs.getString("username"),
                    rs.getInt("score"),
                    rs.getTimestamp("timestamp")
                ));
                first = false;
            }
            json.append("]");
            return json.toString();
        } catch (SQLException e) {
            return "[{\"error\":\"" + e.getMessage() + "\"}]";
        }
    }
    
    public String getUserGameStats(int userId) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT game_name, total_plays, best_score, average_score, total_score, wins, losses, draws, last_played
                FROM user_game_stats 
                WHERE user_id = ?
                ORDER BY last_played DESC
            """);
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append(String.format("""
                    {"game":"%s","plays":%d,"best":%d,"average":%.2f,"total":%d,"wins":%d,"losses":%d,"draws":%d,"lastPlayed":"%s"}""",
                    rs.getString("game_name"),
                    rs.getInt("total_plays"),
                    rs.getInt("best_score"),
                    rs.getDouble("average_score"),
                    rs.getInt("total_score"),
                    rs.getInt("wins"),
                    rs.getInt("losses"),
                    rs.getInt("draws"),
                    rs.getTimestamp("last_played")
                ));
                first = false;
            }
            json.append("]");
            return json.toString();
        } catch (SQLException e) {
            return "[{\"error\":\"" + e.getMessage() + "\"}]";
        }
    }

    public int deleteGameScores(int userId, String gameName) throws SQLException {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM user_game_scores WHERE user_id = ? AND game_name = ?"
            );
            stmt.setInt(1, userId);
            stmt.setString(2, gameName);
            return stmt.executeUpdate();
        }
    }
    
    public int deleteGameStats(int userId, String gameName) throws SQLException {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM user_game_stats WHERE user_id = ? AND game_name = ?"
            );
            stmt.setInt(1, userId);
            stmt.setString(2, gameName);
            return stmt.executeUpdate();
        }
    }

    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
        }
    }
}
