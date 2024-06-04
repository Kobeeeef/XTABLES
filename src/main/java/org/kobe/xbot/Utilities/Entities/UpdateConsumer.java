package org.kobe.xbot.Utilities.Entities;

import org.kobe.xbot.Client.SocketClient;

import java.util.function.Consumer;

public class UpdateConsumer<T> {
    private Class<T> type;
    private Consumer<? super SocketClient.KeyValuePair<T>> consumer;

    public UpdateConsumer(Class<T> type, Consumer<? super SocketClient.KeyValuePair<T>> consumer) {
        this.type = type;
        this.consumer = consumer;
    }

    public Class<T> getType() {
        return type;
    }

    public void setType(Class<T> type) {
        this.type = type;
    }

    public Consumer<? super SocketClient.KeyValuePair<T>> getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer<? super SocketClient.KeyValuePair<T>> consumer) {
        this.consumer = consumer;
    }

    @Override
    public String toString() {
        return "UpdateConsumer{" +
                "type=" + type +
                ", consumer=" + consumer +
                '}';
    }
}
