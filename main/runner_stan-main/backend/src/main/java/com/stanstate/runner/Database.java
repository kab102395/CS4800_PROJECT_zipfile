package com.stanstate.runner;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private final String url;

    public Database(String url) {
        this.url = url;
    }

    public void initialize() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS runs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player TEXT DEFAULT 'Runner',
                        score INTEGER NOT NULL,
                        coins INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public RunRecord insertRun(String player, int score, int coins) {
        String sql = "INSERT INTO runs(player, score, coins, created_at) VALUES(?,?,?,?)";
        String createdAt = Instant.now().toString();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, player == null || player.isBlank() ? "Runner" : player);
            ps.setInt(2, score);
            ps.setInt(3, coins);
            ps.setString(4, createdAt);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                long id = rs.next() ? rs.getLong(1) : -1;
                return new RunRecord(id, player, score, coins, createdAt);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert run", e);
        }
    }

    public List<RunRecord> listTopRuns(int limit) {
        List<RunRecord> runs = new ArrayList<>();
        String sql = "SELECT id, player, score, coins, created_at FROM runs ORDER BY score DESC, id ASC LIMIT ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    runs.add(new RunRecord(
                            rs.getLong("id"),
                            rs.getString("player"),
                            rs.getInt("score"),
                            rs.getInt("coins"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list runs", e);
        }
        return runs;
    }
}

