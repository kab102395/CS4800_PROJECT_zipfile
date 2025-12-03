package com.stanstate.ttt;

public class TestRunner {
    public static void main(String[] args) {
        System.out.println("=== Database Testing Tool ===");
        
        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.initializeDatabase();
            
            DatabaseInspector inspector = new DatabaseInspector();
            inspector.inspectDatabase();
            
        } catch (Exception e) {
            System.err.println("Error during database testing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}