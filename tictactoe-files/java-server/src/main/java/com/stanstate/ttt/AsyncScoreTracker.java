package com.stanstate.ttt;

import java.util.concurrent.*;
import java.util.*;


/**
 * Async score tracking system with SINGLE WRITER thread
 * Handles concurrent score submissions from multiple players without blocking game client responses
 * Uses a single dedicated thread to write to database, preventing SQLite concurrency issues
 * 
 * Architecture:
 * - Multiple game clients submit scores → Non-blocking queue.offer() (< 1ms)
 * - Single writer thread processes queue batches → Serialized database writes
 * - Result: No database locks, no SQLITE_BUSY errors, client response time < 10ms
 */
public class AsyncScoreTracker {
    private static final int QUEUE_CAPACITY = 10000;  // Maximum pending scores
    private static final int BATCH_SIZE = 10;         // Process scores in batches for efficiency
    private static final int BATCH_TIMEOUT_MS = 100;  // Max wait time before processing batch
    
    private final BlockingQueue<ScoreSubmission> scoreQueue;
    private final Thread writerThread;                 // Single writer thread for serialized database writes
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = true;
    private final DatabaseManager dbManager;
    
    // Statistics tracking
    private volatile long totalQueued = 0;
    private volatile long totalProcessed = 0;
    private volatile long totalFailed = 0;
    private volatile long queuePeakSize = 0;
    
    /**
     * Score submission data class - supports both lookup-by-id and lookup-by-username
     */
    public static class ScoreSubmission {
        public final Integer userId;           // If null, use username to lookup/create
        public final String username;          // Username for lookup if userId is null
        public final String gameName;
        public final int score;
        public final int level;
        public final long timestamp;
        
        // Submit by userId
        public ScoreSubmission(int userId, String gameName, int score, int level, String username) {
            this.userId = userId;
            this.username = username;
            this.gameName = gameName;
            this.score = score;
            this.level = level;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Submit by username (will be resolved to userId by writer thread)
        public ScoreSubmission(String username, String gameName, int score, int level) {
            this.userId = null;
            this.username = username;
            this.gameName = gameName;
            this.score = score;
            this.level = level;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public AsyncScoreTracker(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.scoreQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ScoreBatcher");
                t.setDaemon(false);
                return t;
            }
        });
        
        // Create single writer thread for serialized database writes
        this.writerThread = new Thread(this::singleWriterLoop, "ScoreWriter-1");
        this.writerThread.setDaemon(false);
        this.writerThread.setPriority(Thread.NORM_PRIORITY - 1);
        this.writerThread.start();
        
        // Schedule periodic stats reporting
        scheduler.scheduleAtFixedRate(this::logStatistics, 30, 30, TimeUnit.SECONDS);
        
        System.out.println("AsyncScoreTracker initialized with SINGLE WRITER thread (serialized DB writes, no locks)");
    }
    
    /**
     * Queue a score for async processing (non-blocking)
     * Returns immediately without waiting for database write
     */
    public boolean queueScore(int userId, String gameName, int score, int level, String username) {
        ScoreSubmission submission = new ScoreSubmission(userId, gameName, score, level, username);
        return offerToQueue(submission);
    }
    
    /**
     * Queue a score by username (will be resolved to userId by writer thread)
     * Non-blocking submission - returns immediately
     */
    public boolean queueScoreByUsername(String username, String gameName, int score, int level) {
        ScoreSubmission submission = new ScoreSubmission(username, gameName, score, level);
        return offerToQueue(submission);
    }
    
    /**
     * Internal method to add submission to queue
     */
    private boolean offerToQueue(ScoreSubmission submission) {
        // Try to add to queue without blocking
        boolean added = scoreQueue.offer(submission);
        
        if (added) {
            totalQueued++;
            long currentSize = scoreQueue.size();
            if (currentSize > queuePeakSize) {
                queuePeakSize = currentSize;
            }
            
            if (currentSize % 100 == 0) {
                System.out.println("Score queue size: " + currentSize + " (peak: " + queuePeakSize + ")");
            }
        } else {
            System.err.println("Score queue full! Rejecting score for user " + submission.username + " in game " + submission.gameName);
        }
        
        return added;
    }
    
    /**
     * Single-writer loop: processes all database writes sequentially in one thread
     * This prevents SQLite from getting SQLITE_BUSY errors and eliminates concurrency issues
     */
    private void singleWriterLoop() {
        List<ScoreSubmission> batch = new ArrayList<>(BATCH_SIZE);
        
        while (running) {
            try {
                batch.clear();
                
                // Wait for first item with timeout (allows for periodic flushes)
                ScoreSubmission first = scoreQueue.poll(BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                
                if (first != null) {
                    batch.add(first);
                    
                    // Drain up to BATCH_SIZE-1 more items without blocking
                    scoreQueue.drainTo(batch, BATCH_SIZE - 1);
                    
                    // Process the entire batch in this single thread (no concurrency)
                    processBatch(batch);
                    totalProcessed += batch.size();
                }
            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Error in score writer thread: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Process a batch of score submissions
     * This is the ONLY place database writes happen (single-threaded)
     */
    private void processBatch(List<ScoreSubmission> batch) {
        if (batch.isEmpty()) return;
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;
        
        System.out.println("=== PROCESSING BATCH: " + batch.size() + " scores ===");
        
        for (ScoreSubmission submission : batch) {
            try {
                System.out.println("DEBUG: Processing score for user=" + submission.username + ", game=" + submission.gameName + ", score=" + submission.score);
                
                Integer finalUserId = submission.userId;
                String displayName = submission.username;
                
                // If userId is null, resolve username to userId (or create user)
                if (finalUserId == null) {
                    System.out.println("DEBUG: userId is null, calling ensureUserExists for " + submission.username);
                    finalUserId = dbManager.ensureUserExists(submission.username);
                    if (finalUserId == null) {
                        throw new Exception("Failed to create or lookup user: " + submission.username);
                    }
                    System.out.println("DEBUG: User registered/found with ID: " + finalUserId);
                    displayName = submission.username;
                }
                
                // Record score in database (this is the actual database write)
                System.out.println("DEBUG: Calling recordGameScore for userId=" + finalUserId + ", game=" + submission.gameName + ", score=" + submission.score);
                dbManager.recordGameScore(
                    finalUserId,
                    submission.gameName,
                    submission.score,
                    submission.level
                );
                System.out.println("DEBUG: Score recorded successfully");
                successCount++;
            } catch (Exception e) {
                failCount++;
                totalFailed++;
                System.err.println("!!! FAILED to record score for " + submission.username + " in " + submission.gameName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("=== BATCH COMPLETE: " + successCount + " success, " + failCount + " failed, " + 
                         duration + "ms for " + batch.size() + " scores ===");
    }
    
    /**
     * Get current queue statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("queued", totalQueued);
        stats.put("processed", totalProcessed);
        stats.put("failed", totalFailed);
        stats.put("pending", scoreQueue.size());
        stats.put("peak_queue_size", queuePeakSize);
        stats.put("success_rate", totalQueued > 0 ? String.format("%.1f%%", (totalProcessed * 100.0) / totalQueued) : "N/A");
        return stats;
    }
    
    /**
     * Log statistics periodically
     */
    private void logStatistics() {
        long pending = scoreQueue.size();
        double rate = totalQueued > 0 ? (totalProcessed * 100.0) / totalQueued : 0;
        System.out.println(String.format(
            "AsyncScore Stats: Queued=%d, Processed=%d, Failed=%d, Pending=%d, Peak=%d, Success=%.1f%%",
            totalQueued, totalProcessed, totalFailed, pending, queuePeakSize, rate
        ));
    }
    
    /**
     * Graceful shutdown - waits for pending scores to be processed
     */
    public void shutdown() {
        System.out.println("AsyncScoreTracker shutting down... (pending scores: " + scoreQueue.size() + ")");
        running = false;
        
        // Wait for writer thread to finish
        try {
            if (writerThread.isAlive()) {
                writerThread.join(10000);  // Wait up to 10 seconds
                
                if (writerThread.isAlive()) {
                    System.out.println("Writer thread still running, processing remaining scores...");
                    
                    // Process any remaining scores
                    List<ScoreSubmission> remaining = new ArrayList<>();
                    scoreQueue.drainTo(remaining);
                    if (!remaining.isEmpty()) {
                        System.out.println("Processing " + remaining.size() + " remaining scores...");
                        processBatch(remaining);
                    }
                }
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for writer thread shutdown");
            Thread.currentThread().interrupt();
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("AsyncScoreTracker shutdown complete. Final stats:");
        logStatistics();
    }
    
    /**
     * Wait for all pending scores to be processed (useful for tests)
     */
    public void waitForPending(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (scoreQueue.size() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
