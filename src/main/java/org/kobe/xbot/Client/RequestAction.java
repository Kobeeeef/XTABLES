package org.kobe.xbot.Client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RequestAction {
    private final SocketClient client;
    private final String value;

    public RequestAction(SocketClient client, String value) {
        this.client = client;
        this.value = value;
    }

    public void async(Consumer<String> onSuccess, Consumer<Throwable> onFailure) {
        CompletableFuture<String> future = client.sendAsync(value);
        future.thenAccept(onSuccess)
                .exceptionally(ex -> {
                    onFailure.accept(ex);
                    return null;
                });
    }

    public String complete() {
        return client.sendComplete(value);
    }
}

