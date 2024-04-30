package org.kobe.xbot.Client;

import com.google.gson.Gson;
import org.kobe.xbot.Utilites.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;


public class XTablesClient {
    private final SocketClient client;
    private final Gson gson = new Gson();


    private final CountDownLatch latch = new CountDownLatch(1);

    public XTablesClient(String SERVER_ADDRESS, int SERVER_PORT, int MAX_THREADS) {
        this.client = new SocketClient(SERVER_ADDRESS, SERVER_PORT, 1000, MAX_THREADS, this);
        Thread thread = new Thread(() -> {
            client.connect();
            client.setUpdateConsumer(this::on_update);
            latch.countDown();
        });
        thread.setDaemon(true);
        thread.start();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static final HashMap<String, List<UpdateConsumer<?>>> update_consumers = new HashMap<>();

    public <T> RequestAction<ResponseStatus> subscribeUpdateEvent(String key, Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer) {
        Utilities.validateKey(key);
        return subscribeUpdateEventNoCheck(key, type, consumer);
    }

    private <T> RequestAction<ResponseStatus> subscribeUpdateEventNoCheck(String key, Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.SUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class) {
            @Override
            public void onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                    consumers.add(new UpdateConsumer<>(type, consumer));
                }
            }
        };
    }

    public List<RequestAction<ResponseStatus>> resubscribeToAllUpdateEvents() {
        List<RequestAction<ResponseStatus>> all = new ArrayList<>();
        for(String key : update_consumers.keySet()) {
            all.add(new RequestAction<>(client, new ResponseInfo(null, MethodType.SUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class));
        }
        return all;
    }
    public <T>  List<T> completeAll(List<RequestAction<T>> requestActions) {
        List<T> responses = new ArrayList<>();
        for(RequestAction<T> requestAction : requestActions) {
            T response = requestAction.complete();
            responses.add(response);
        }
        return responses;
    }
    public <T> void queueAll(List<RequestAction<T>> requestActions) {
        for(RequestAction<T> requestAction : requestActions) {
            requestAction.queue();
        }
    }
    public <T> RequestAction<ResponseStatus> subscribeUpdateEvent(Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer) {
        String key = " ";
        return subscribeUpdateEventNoCheck(key, type, consumer);
    }

    public <T> RequestAction<ResponseStatus> unsubscribeUpdateEvent(String key, Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.UNSUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class) {
            @Override
            public void onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                    consumers.removeIf(updateConsumer -> updateConsumer.type.equals(type) && updateConsumer.consumer.equals(consumer));
                    if(consumers.isEmpty()) {
                        update_consumers.remove(key);
                    }
                }
            }
            @Override
            public boolean doNotRun(){
                List<UpdateConsumer<?>> consumers = new ArrayList<>(update_consumers.computeIfAbsent(key, k -> new ArrayList<>()));
                consumers.removeIf(updateConsumer -> updateConsumer.type.equals(type) && updateConsumer.consumer.equals(consumer));
                return !consumers.isEmpty();
            }
        };
    }
    public <T> RequestAction<ResponseStatus> unsubscribeUpdateEvent(Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer) {
        String key = " ";
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.UNSUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class) {
            @Override
            public void onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                    consumers.removeIf(updateConsumer -> updateConsumer.type.equals(type) && updateConsumer.consumer.equals(consumer));
                    if(consumers.isEmpty()) {
                        update_consumers.remove(key);
                    }
                }
            }
            @Override
            public boolean doNotRun(){
                List<UpdateConsumer<?>> consumers = new ArrayList<>(update_consumers.computeIfAbsent(key, k -> new ArrayList<>()));
                consumers.removeIf(updateConsumer -> updateConsumer.type.equals(type) && updateConsumer.consumer.equals(consumer));
                return !consumers.isEmpty();
            }
        };
    }
    public record UpdateConsumer<T>(Class<T> type, Consumer<? super SocketClient.KeyValuePair<T>> consumer) {
    }


    private <T> void on_update(SocketClient.KeyValuePair<String> keyValuePair) {
        processUpdate(keyValuePair, keyValuePair.getKey());
        if (update_consumers.containsKey(" ")) {
            processUpdate(keyValuePair, " ");
        }
    }

    private <T> void processUpdate(SocketClient.KeyValuePair<String> keyValuePair, String key) {
        List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
        for (UpdateConsumer<?> updateConsumer : consumers) {
            UpdateConsumer<T> typedUpdateConsumer = (UpdateConsumer<T>) updateConsumer;
            Consumer<? super SocketClient.KeyValuePair<T>> consumer = typedUpdateConsumer.consumer();
            Class<T> type = typedUpdateConsumer.type();
            T parsed = new Gson().fromJson(keyValuePair.getValue(), type);
            consumer.accept(new SocketClient.KeyValuePair<>(keyValuePair.getKey(), parsed));
        }
    }


    public RequestAction<ResponseStatus> putRaw(String key, String value) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + value).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> putString(String key, String value) {
        Utilities.validateKey(key);
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }
    public RequestAction<ResponseStatus> renameKey(String key, String newName) {
        Utilities.validateKey(key);
        Utilities.validateName(newName, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + newName).parsed(), ResponseStatus.class);
    }
    public RequestAction<ResponseStatus> putBoolean(String key, Boolean value) {
        Utilities.validateKey(key);
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }
    public <T> RequestAction<ResponseStatus> putArray(String key, List<T> value) {
        Utilities.validateKey(key);
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> putInteger(String key, Integer value) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + value).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> putObject(String key, Object value) {
        Utilities.validateKey(key);
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> delete(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.DELETE, key).parsed(), ResponseStatus.class);
    }

    public RequestAction<String> getRaw(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), String.class);
    }

    public RequestAction<String> getRawJSON() {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET_RAW_JSON).parsed(), null);
    }

    public RequestAction<String> getString(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), String.class);
    }
    public RequestAction<Boolean> getBoolean(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), Boolean.class);
    }
    public <T> RequestAction<T> getObject(String key, Class<T> type) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), type);
    }

    public RequestAction<Integer> getInteger(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), Integer.class);
    }

    public <T> RequestAction<ArrayList<T>> getArray(String key, Class<T> type) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), type);
    }

    public RequestAction<ArrayList<String>> getTables(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET_TABLES, key).parsed(), ArrayList.class);
    }

    public RequestAction<ArrayList<String>> getTables() {
        return getTables("");
    }

    public RequestAction<ResponseStatus> rebootServer() {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.REBOOT_SERVER).parsed(), ResponseStatus.class);
    }

    public <T> RequestAction<T> sendCustomMessage(MethodType method, String message, Class<T> type) {
        return new RequestAction<>(client, new ResponseInfo(null, method, message).parsed(), type);
    }

    public SocketClient getSocketClient() {
        return client;
    }

    public RequestAction<LatencyInfo> ping_latency() {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PING).parsed()) {
            @Override
            public LatencyInfo parseResponse(long startTime, Object result) {
                RequestInfo info = new RequestInfo(result.toString());
                if (info.getTokens().length == 2 && info.getTokens()[0].equals("OK")) {
                    long serverTime = Long.parseLong(info.getTokens()[1]);
                    long currentTime = System.nanoTime();
                    long networkLatency = Math.abs(currentTime - serverTime);
                    long roundTripLatency = Math.abs(currentTime - startTime);
                    return new LatencyInfo(networkLatency / 1e6, roundTripLatency / 1e6);
                } else {
                    return null;
                }

            }
        };

    }


    public record LatencyInfo(double networkLatencyMS, double roundTripLatencyMS) {
    }
}
