package com.stanstate.ttt;

public class Main {
    public static void main(String[] args) throws Exception {
        int wsPort = 8080;
        int httpPort = 8081;
        
        if (args.length > 0) {
            wsPort = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            httpPort = Integer.parseInt(args[1]);
        }
        
        System.out.println("=== Starting Enhanced Multithreaded TTT Server ===");
        
        // Initialize H2 database with enhanced schema
        System.out.println("Initializing enhanced database...");
        DatabaseManager dbManager = DatabaseManager.getInstance();
        dbManager.initializeDatabase();
        
        // Initialize GameStateCache (CRITICAL!)
        System.out.println("Initializing game state cache...");
        GameStateCache gameCache = GameStateCache.getInstance();
        System.out.println("Game state cache initialized successfully");
        
        System.out.println("Starting WebSocket server on port " + wsPort + "...");
        Server wsServer = new Server(wsPort);
        wsServer.start();
        
        System.out.println("Starting HTTP API server on port " + httpPort + "...");
        RestApiServer httpServer = new RestApiServer(httpPort);
        httpServer.start();
        
        System.out.println("\n=== Server Configuration ===");
        System.out.println("TTT WebSocket server: ws://127.0.0.1:" + wsPort);
        System.out.println("TTT HTTP API server: http://127.0.0.1:" + httpPort);
        System.out.println("Enhanced Features Enabled:");
        System.out.println("  - Persistent lobby system");
        System.out.println("  - Message retry with exponential backoff");
        System.out.println("  - Connection health monitoring");
        System.out.println("  - Automatic dead connection cleanup");
        System.out.println("  - State versioning and consistency checks");
        System.out.println("  - Heartbeat system for connection quality");
        System.out.println("Server ready for production multithreaded operations!");
        
        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n=== Shutting down server gracefully ===");
            try {
                if (wsServer != null) {
                    System.out.println("Stopping WebSocket server...");
                    wsServer.stop();
                }
                
                // Shutdown WebSocket notifier
                if (Server.getNotifier() != null) {
                    System.out.println("Shutting down notification system...");
                    Server.getNotifier().shutdown();
                }
                
                // Shutdown database manager
                System.out.println("Shutting down database manager...");
                dbManager.shutdown();
                
                // Shutdown game state cache
                System.out.println("Shutting down game state cache...");
                GameStateCache.getInstance().shutdown();
                
                System.out.println("Server shutdown complete.");
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));
        
        // Keep the server running - block the main thread
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Server interrupted");
        }
    }
}
