package org.kobe.xbot.Client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.kobe.xbot.Utilites.Utilities;
import org.kobe.xbot.Utilites.XTablesLogger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Handles asynchronous and synchronous request actions to a SocketClient.
 * This class encapsulates the logic for sending data over a network, handling responses,
 * and managing errors during communication. It supports both queued (asynchronous)
 * and complete (synchronous) operations.
 * <p>
 * Author: Kobe
 */
public class RequestAction<T> {
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private final SocketClient client;
    private final String value;
    private final Type type;

    /**
     * Constructs a RequestAction with specified type.
     *
     * @param client The client used to send requests.
     * @param value  The data to be sent.
     * @param type   The expected return type of the response.
     */
    public RequestAction(SocketClient client, String value, Type type) {
        this.client = client;
        this.value = value;
        this.type = type;
    }

    /**
     * Constructs a RequestAction without a specified type.
     *
     * @param client The client used to send requests.
     * @param value  The data to be sent.
     */
    public RequestAction(SocketClient client, String value) {
        this.client = client;
        this.value = value;
        this.type = null;
    }

    /**
     * Queues a request to be sent asynchronously with success and failure handlers.
     *
     * @param onSuccess Consumer to handle the successful response.
     * @param onFailure Consumer to handle failures during the request execution.
     */
    public void queue(Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        long startTime = System.nanoTime();
        if (doNotRun()) return;
        beforeRun();
        try {
            CompletableFuture<String> future = client.sendAsync(value);
            future.thenAccept(result -> {
                        T parsed = parseResponse(startTime, result);
                        result = formatResult(result);
                        T res = null;
                        if (parsed == null) {
                            if (type == null) {
                                res = (T) result;
                            } else res = new Gson().fromJson(result, type);
                        }
                        boolean response = onResponse(parsed == null ? res : parsed);
                        if (response) onSuccess.accept(parsed == null ? res : parsed);
                    })
                    .exceptionally(ex -> {
                        onFailure.accept(ex);
                        return null;
                    });
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    /**
     * Queues a request to be sent asynchronously with a success handler.
     *
     * @param onSuccess Consumer to handle the successful response.
     */
    public void queue(Consumer<T> onSuccess) {
        long startTime = System.nanoTime();
        if (doNotRun()) return;
        beforeRun();
        try {
            CompletableFuture<String> future = client.sendAsync(value);
            future.thenAccept(result -> {
                T parsed = parseResponse(startTime, result);
                result = formatResult(result);
                T res = null;
                if (parsed == null) {
                    if (type == null) {
                        res = (T) result;
                    } else res = new Gson().fromJson(result, type);
                }
                boolean response = onResponse(parsed == null ? res : parsed);
                if (response) onSuccess.accept(parsed == null ? res : parsed);
            });
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    /**
     * Queues a request to be sent asynchronously without any additional handlers.
     */
    public void queue() {
        long startTime = System.nanoTime();
        if (doNotRun()) return;
        beforeRun();
        try {
            CompletableFuture<String> future = client.sendAsync(value);
            future.thenAccept(result -> {
                T parsed = parseResponse(startTime, result);
                result = formatResult(result);
                T res = null;
                if (parsed == null) {
                    if (type == null) {
                        res = (T) result;
                    } else res = new Gson().fromJson(result, type);
                }
                onResponse(parsed == null ? res : parsed);
            });
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    /**
     * Sends a request and waits for the completion synchronously.
     *
     * @return The response received, parsed by {@code parseResponse}.
     */
    public T complete() {
        long startTime = System.nanoTime();
        if (doNotRun()) return returnValueIfNotRan();
        beforeRun();
        try {
            String result = client.sendComplete(value);
            T parsed = parseResponse(startTime, result);
            result = formatResult(result);
            T res = null;
            if (parsed == null) {
                if (type == null) {
                    res = (T) result;
                } else{
                     res = new Gson().fromJson(result, type);
                }
            }
            boolean response = onResponse(parsed == null ? res : parsed);
            if (!response) return returnValueIfNotRan();
            return parsed == null ? res : parsed;
        } catch (ExecutionException | TimeoutException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Called when a response is received. Meant to be overridden in subclasses to handle specific actions on response.
     *
     * @param result The result of the response.
     */
    public boolean onResponse(T result) {
        return true;
    }

    /**
     * Determines if the request should not run. Meant to be overridden in subclasses to provide specific conditions.
     *
     * @return true if the request should not be sent, false otherwise.
     */
    public boolean doNotRun() {
        return false;
    }

    /**
     * Returns a value when {@code doNotRun} returns true and the action is not performed. Meant to be overridden in subclasses to provide a default value.
     *
     * @return The default value to return if the request is not run.
     */
    public T returnValueIfNotRan() {
        return null;
    }

    /**
     * Parses the response received from the server. Meant to be overridden in subclasses to parse the response based on specific needs.
     *
     * @param startTime The start time of the request, used for calculating latency.
     * @param result    The raw result from the server.
     * @return The parsed response as type T.
     */
    public T parseResponse(long startTime, String result) {
        return null;
    }

    /**
     * Called before sending the request. Meant to be overridden in subclasses to perform any necessary setup or validation before running the request.
     */
    public void beforeRun() {
    }

    public String formatResult(String result) {
        return result;
    }

}
