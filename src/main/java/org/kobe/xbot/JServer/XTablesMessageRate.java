package org.kobe.xbot.JServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class XTablesMessageRate {
    private final AtomicInteger lastPullMessages = new AtomicInteger(0);
    private final AtomicInteger lastReplyMessages = new AtomicInteger(0);
    private final AtomicInteger lastPublishMessages = new AtomicInteger(0);

    private volatile double pullRate = 0.0;
    private volatile double replyRate = 0.0;
    private volatile double publishRate = 0.0;

    private long lastTime = 0;
    private long currentTime = 0;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public XTablesMessageRate(AtomicInteger pullMessages, AtomicInteger replyMessages, AtomicInteger publishMessages) {
        // Set the initial time
        lastTime = System.nanoTime();

        scheduler.scheduleAtFixedRate(() -> {
            // Get current time and calculate the time difference
            currentTime = System.nanoTime();
            long timeElapsed = currentTime - lastTime;

            // Calculate the message rates based on value difference and time elapsed
            int currentPull = pullMessages.get();
            pullRate = (currentPull - lastPullMessages.get()) / (timeElapsed / 1_000_000_000.0);  // messages per second
            lastPullMessages.set(currentPull);

            int currentReply = replyMessages.get();
            replyRate = (currentReply - lastReplyMessages.get()) / (timeElapsed / 1_000_000_000.0);  // messages per second
            lastReplyMessages.set(currentReply);

            int currentPublish = publishMessages.get();
            publishRate = (currentPublish - lastPublishMessages.get()) / (timeElapsed / 1_000_000_000.0);  // messages per second
            lastPublishMessages.set(currentPublish);

            // Update the last time to current time for the next iteration
            lastTime = currentTime;
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    // Getter methods for rates
    public double getPullMessagesPerSecond() {
        return pullRate;
    }

    public double getReplyMessagesPerSecond() {
        return replyRate;
    }

    public double getPublishMessagesPerSecond() {
        return publishRate;
    }

    // Shutdown method to stop the scheduler
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
