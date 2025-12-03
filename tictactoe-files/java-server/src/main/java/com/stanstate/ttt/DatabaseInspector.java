package com.stanstate.ttt;

import java.sql.*;

public class DatabaseInspector {
    public static void main(String[] args) {
        DatabaseInspector inspector = new DatabaseInspector();
        inspector.inspectDatabase();
    }

    public void inspectDatabase() {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        dbManager.initializeDatabase();

        try (Connection conn = dbManager.getConnection()) {
            System.out.println("=== DATABASE INSPECTION (H2) ===\n");
            System.out.println("URL: " + ConnectionPool.getDatabaseUrl() + "\n");

            // Get all tables
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});

            System.out.println("TABLES IN DATABASE:");
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                System.out.println("  * " + tableName);
            }

            System.out.println("\n" + "=".repeat(50));

            // Check each table for data
            String[] tableNames = {
                "schema_version", "player_sessions", "game_matches", 
                "game_moves", "pending_notifications", "connection_health", "lobby_state",
                "users", "user_stats", "user_game_scores", "user_game_stats", "player_stats"
            };

            for (String table : tableNames) {
                try {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM " + table);

                    if (rs.next()) {
                        int count = rs.getInt("count");
                        System.out.println("\nTABLE: " + table.toUpperCase());
                        System.out.println("   Rows: " + count);

                        if (count > 0 && count < 20) {
                            // Show actual data for small tables
                            ResultSet dataRs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT 10");
                            ResultSetMetaData rsMetaData = dataRs.getMetaData();
                            int columnCount = rsMetaData.getColumnCount();

                            System.out.println("   Data:");
                            while (dataRs.next()) {
                                System.out.print("     ");
                                for (int i = 1; i <= columnCount; i++) {
                                    String columnName = rsMetaData.getColumnName(i);
                                    String value = dataRs.getString(i);
                                    System.out.print(columnName + "=" + value + " ");
                                }
                                System.out.println();
                            }
                        } else if (count > 0) {
                            // Just show column info for large tables
                            ResultSet dataRs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT 1");
                            ResultSetMetaData rsMetaData = dataRs.getMetaData();
                            int columnCount = rsMetaData.getColumnCount();

                            System.out.print("   Columns: ");
                            for (int i = 1; i <= columnCount; i++) {
                                System.out.print(rsMetaData.getColumnName(i) + " ");
                            }
                            System.out.println();
                        }
                    }
                } catch (SQLException e) {
                    System.out.println("\nERROR TABLE: " + table.toUpperCase() + " - " + e.getMessage());
                }
            }

            System.out.println("\n" + "=".repeat(50));
            System.out.println("SUMMARY: Database inspection complete!");

        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
