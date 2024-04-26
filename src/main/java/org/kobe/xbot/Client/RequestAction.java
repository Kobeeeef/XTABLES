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
        CompletableFuture<T> future = client.sendAsync(value, type);
        future.thenAccept(onSuccess)
                .exceptionally(ex -> {
                    onFailure.accept(ex);
                    return null;
                });

    }
    public void queue(Consumer<T> onSuccess) {
        CompletableFuture<T> future = client.sendAsync(value, type);
        future.thenAccept(onSuccess);
    }
    public void queue() {
        client.sendAsync(value, type);
    }

    public T complete() {
        try {
            return client.sendComplete(value, type);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}

