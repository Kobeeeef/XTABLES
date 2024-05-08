package org.kobe.xbot.Client;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class RequestAction<T> {
    private final SocketClient client;
    private final String value;
    private final Type type;

    public RequestAction(SocketClient client, String value, Type type) {
        this.client = client;
        this.value = value;
        this.type = type;
    }

    public RequestAction(SocketClient client, String value) {
        this.client = client;
        this.value = value;
        this.type = null;
    }

    public void queue(Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        if (doNotRun()) return;
        long startTime = System.nanoTime();
        CompletableFuture<T> future = client.sendAsync(value, type);
        future.thenAccept(t -> {
                    T parsed = parseResponse(startTime, t);
                    onResponse(parsed == null ? t : parsed);
                    onSuccess.accept(parsed == null ? t : parsed);
                })
                .exceptionally(ex -> {
                    onFailure.accept(ex);
                    return null;
                });

    }

    public void queue(Consumer<T> onSuccess) {
        if (doNotRun()) return;
        long startTime = System.nanoTime();
        CompletableFuture<T> future = client.sendAsync(value, type);
        future.thenAccept(t -> {
            T parsed = parseResponse(startTime, t);
            onResponse(parsed == null ? t : parsed);
            onSuccess.accept(parsed == null ? t : parsed);
        });
    }

    public void queue() {
        if (doNotRun()) return;
        long startTime = System.nanoTime();
        CompletableFuture<T> future = client.sendAsync(value, type);
        future.thenAccept(t -> {
            T parsed = parseResponse(startTime, t);
            onResponse(parsed == null ? t : parsed);
        });
    }

    public T complete() {
        if (doNotRun()) return returnValueIfNotRan();
        long startTime = System.nanoTime();
        try {
            T result = client.sendComplete(value, type);
            T parsed = parseResponse(startTime, result);
            onResponse(parsed == null ? result : parsed);
            return parsed == null ? result : parsed;
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void onResponse(T result) {
    }
    public boolean doNotRun(){
        return false;
    }
    public T returnValueIfNotRan() {
        return null;
    }

    public T parseResponse(long startTime, Object result) {
        return null;
    }
}

