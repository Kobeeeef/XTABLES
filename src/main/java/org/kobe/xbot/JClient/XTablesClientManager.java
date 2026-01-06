package org.kobe.xbot.JClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * XTablesClientManager - Manages the asynchronous startup and retrieval of an XTablesClient instance.
 * <p>
 * This class ensures that the client starts in a separate thread and provides methods to check its readiness.
 * It supports non-blocking retrieval, blocking retrieval, and retrieval with a timeout.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: org.kobe.xbot.JClient
 * <p>
 * This is part of the XTABLES project and provides controlled access to the XTablesClient.
 */
public class XTablesClientManager {
    private CompletableFuture<XTablesClient> clientFuture;


    public XTablesClientManager() {
        startXTablesClientAsync();
    }

    public XTablesClientManager(String ip) {
        startXTablesClientAsync(ip);
    }

    /**
     * Starts the XTablesClient asynchronously.
     * <p>
     * The client is initialized in a background thread, and the future can be used to check its status.
     *
     * @return a CompletableFuture representing the asynchronous initialization of the client.
     */
    private CompletableFuture<XTablesClient> startXTablesClientAsync() {
        if (clientFuture == null || clientFuture.isCompletedExceptionally()) {
            clientFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return new XTablesClient();
                } catch (Exception e) {
                    System.err.println("Error initializing XTablesClient: " + e.getMessage());
                    throw new RuntimeException("XTablesClient failed to start", e);
                }
            });
        }
        return clientFuture;
    }

    private CompletableFuture<XTablesClient> startXTablesClientAsync(String ip) {
        if (clientFuture == null || clientFuture.isCompletedExceptionally()) {
            clientFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return new XTablesClient(ip);
                } catch (Exception e) {
                    System.err.println("Error initializing XTablesClient: " + e.getMessage());
                    throw new RuntimeException("XTablesClient failed to start", e);
                }
            });
        }
        return clientFuture;
    }

    public CompletableFuture<XTablesClient> getClientFuture() {
        return clientFuture;
    }

    /**
     * Checks whether the client has been successfully initialized.
     *
     * @return true if the client is ready, false otherwise.
     */
    public boolean isClientReady() {
        return clientFuture != null && clientFuture.isDone() && !clientFuture.isCompletedExceptionally();
    }

    /**
     * Retrieves the client if it is ready, otherwise returns null.
     *
     * @return the initialized XTablesClient or null if not yet ready.
     */
    public XTablesClient getOrNull() {
        if (isClientReady()) {
            try {
                return clientFuture.getNow(null);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Blocks until the client is initialized and returns it.
     *
     * @return the initialized XTablesClient.
     * @throws InterruptedException if the waiting thread is interrupted.
     * @throws ExecutionException   if the initialization fails.
     */
    public XTablesClient getAndBlock() throws InterruptedException, ExecutionException {
        if (clientFuture == null) {
            throw new IllegalStateException("Client has not been started.");
        }
        return clientFuture.get();
    }

    /**
     * Blocks for a specified duration until the client is initialized and returns it.
     *
     * @param timeout the maximum time to wait.
     * @param unit    the time unit of the timeout argument.
     * @return the initialized XTablesClient.
     * @throws InterruptedException if the waiting thread is interrupted.
     * @throws ExecutionException   if the initialization fails.
     * @throws TimeoutException     if the timeout expires before the client is ready.
     */
    public XTablesClient getAndBlock(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (clientFuture == null) {
            throw new IllegalStateException("Client has not been started.");
        }
        return clientFuture.get(timeout, unit);
    }
}

