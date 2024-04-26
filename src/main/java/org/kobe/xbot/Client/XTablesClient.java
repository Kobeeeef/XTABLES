package org.kobe.xbot.Client;

import com.google.gson.Gson;
import org.kobe.xbot.Server.MethodType;
import org.kobe.xbot.Server.ResponseInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;


public class XTablesClient {
    private final SocketClient client;
    private final Gson gson = new Gson();


    private final CountDownLatch latch = new CountDownLatch(1);

    public XTablesClient(String SERVER_ADDRESS, int SERVER_PORT) {
        this.client = new SocketClient(SERVER_ADDRESS, SERVER_PORT, 1000);
        Thread thread = new Thread(() -> {
            client.connect();
            client.setUpdateConsumer(this::on_update);
            latch.countDown();
        });
        thread.start();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final HashMap<String, List<UpdateConsumer<?>>> update_consumers = new HashMap<>();

    public <T> RequestAction<String> subscribeUpdateEvent(String key, Class<T> type, Consumer<T> consumer) {
        List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
        consumers.add(new UpdateConsumer<>(type, consumer));
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.SUBSCRIBE_UPDATE, key).parsed(), String.class);
    }

    private <T> void on_update(SocketClient.KeyValuePair keyValuePair) {
        List<UpdateConsumer<?>> consumers = update_consumers.get(keyValuePair.getKey());
        for (UpdateConsumer<?> updateConsumer : consumers) {
            if (updateConsumer.type().equals(String.class)) {
                // Special handling for String type
                Consumer<String> consumer = (Consumer<String>) updateConsumer.consumer();
                consumer.accept(keyValuePair.getValue());
            } else {
                // General case
                T parsed = new Gson().fromJson(keyValuePair.getValue(), (Class<T>) updateConsumer.type());
                Consumer<T> consumer = (Consumer<T>) updateConsumer.consumer();
                consumer.accept(parsed);
            }
        }
    }

    public RequestAction<String> putRaw(String key, String value) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + value).parsed(), String.class);
    }

    public <T> RequestAction<String> putArray(String key, List<T> value) {
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), String.class);
    }

    public RequestAction<String> putInteger(String key, Integer value) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + value).parsed(), String.class);
    }

    public RequestAction<String> putObject(String key, Object value) {
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), String.class);
    }

    public RequestAction<String> delete(String key) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.DELETE, key).parsed(), String.class);
    }

    public RequestAction<String> getRaw(String key) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), String.class);
    }

    public RequestAction<String> getRawJSON() {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET_RAW_JSON).parsed(), null);
    }

    public RequestAction<String> getString(String key) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), String.class);
    }

    public <T> RequestAction<T> getObject(String key, Class<T> type) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), type);
    }

    public RequestAction<Integer> getInteger(String key) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), Integer.class);
    }

    public <T> RequestAction<ArrayList<T>> getArray(String key, Class<T> type) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), type);
    }

    public RequestAction<ArrayList<String>> getTables(String key) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET_TABLES, key).parsed(), ArrayList.class);
    }


    public SocketClient getSocketClient() {
        return client;
    }

    public record UpdateConsumer<T>(Class<T> type, Consumer<T> consumer) {
    }
}
