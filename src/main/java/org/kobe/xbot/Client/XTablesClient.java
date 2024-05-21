package org.kobe.xbot.Client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.kobe.xbot.Server.XTablesData;
import org.kobe.xbot.Utilities.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;


public class XTablesClient {
    private final XTablesLogger logger = XTablesLogger.getLogger();

    private final SocketClient client;
    private final Gson gson = new Gson();
    private XTablesData<String> cache;
    private long cacheFetchCooldown = 10000;
    private boolean isCacheReady = false;
    private final CountDownLatch latch = new CountDownLatch(1);
    private Thread cacheThread;
    public final HashMap<String, List<UpdateConsumer<?>>> update_consumers = new HashMap<>();

    public XTablesClient(String SERVER_ADDRESS, int SERVER_PORT, int MAX_THREADS, boolean useCache) {
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
        if (useCache) {
            cacheThread = new Thread(this::enableCache);
            cacheThread.setDaemon(true);
            cacheThread.start();
        }
    }

    public void stopAll() {
        client.stopAll();
        if (cacheThread != null) cacheThread.interrupt();
    }

    public long getCacheFetchCooldown() {
        return cacheFetchCooldown;
    }

    public XTablesClient setCacheFetchCooldown(long cacheFetchCooldown) {
        this.cacheFetchCooldown = cacheFetchCooldown;
        return this;
    }

    private void enableCache() {
        try {
            initializeCache();
            if (subscribeToCacheUpdates()) {
                logger.info("Cache is now setup and ready to use.");
                periodicallyUpdateCache();
            } else {
                logger.severe("Failed to subscribe to ANY update, NON OK status returned from server.");
            }
        } catch (Exception e) {
            logger.severe("Failed to initialize cache or subscribe to updates. Error:\n" + e.getMessage());
        }
    }

    private void initializeCache() {
        logger.info("Initializing cache...");
        String rawJSON = getRawJSON().complete();
        cache = new XTablesData<>();
        cache.updateFromRawJSON(rawJSON);
        logger.info("Cache initialized and populated.");
    }

    private boolean subscribeToCacheUpdates() {
        ResponseStatus responseStatus = subscribeUpdateEvent((updateEvent) -> {
            cache.put(updateEvent.getKey(), updateEvent.getValue());
        }).complete();
        if (responseStatus.equals(ResponseStatus.OK)) logger.info("Cache is now subscribed for updates.");
        isCacheReady = responseStatus.equals(ResponseStatus.OK);
        return isCacheReady;
    }

    private void periodicallyUpdateCache() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String newRawJSON = getRawJSON().complete();
                cache.updateFromRawJSON(newRawJSON);
                logger.info("Cache has been auto re-populated.");
                Thread.sleep(cacheFetchCooldown);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Cache update thread was interrupted.");
                break;
            }
        }
    }


    public boolean isCacheReady() {
        return isCacheReady;
    }

    public XTablesData<String> getCache() {
        return cache;
    }

    public void updateServerAddress(String SERVER_ADDRESS, int SERVER_PORT) {
        client.setSERVER_ADDRESS(SERVER_ADDRESS).setSERVER_PORT(SERVER_PORT).reconnect();
    }


    public <T> RequestAction<ResponseStatus> subscribeUpdateEvent(String key, Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer) {
        Utilities.validateKey(key);
        return subscribeUpdateEventNoCheck(key, type, consumer);
    }

    private <T> RequestAction<ResponseStatus> subscribeUpdateEventNoCheck(String key, Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.SUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class) {
            @Override
            public boolean onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                    consumers.add(new UpdateConsumer<>(type, consumer));
                }
                return true;
            }
        };
    }

    public List<RequestAction<ResponseStatus>> resubscribeToAllUpdateEvents() {
        List<RequestAction<ResponseStatus>> all = new ArrayList<>();
        for (String key : update_consumers.keySet()) {
            all.add(new RequestAction<>(client, new ResponseInfo(null, MethodType.SUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class));
        }
        return all;
    }

    public <T> List<T> completeAll(List<RequestAction<T>> requestActions) {
        List<T> responses = new ArrayList<>();
        for (RequestAction<T> requestAction : requestActions) {
            T response = requestAction.complete();
            responses.add(response);
        }
        return responses;
    }

    public <T> void queueAll(List<RequestAction<T>> requestActions) {
        for (RequestAction<T> requestAction : requestActions) {
            requestAction.queue();
        }
    }

    public RequestAction<ResponseStatus> subscribeUpdateEvent(Consumer<SocketClient.KeyValuePair<String>> consumer) {
        String key = " ";
        return subscribeUpdateEventNoCheck(key, null, consumer);
    }

    public <T> RequestAction<ResponseStatus> unsubscribeUpdateEvent(String key, Class<T> type, Consumer<SocketClient.KeyValuePair<T>> consumer) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.UNSUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class) {
            @Override
            public boolean onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                    consumers.removeIf(updateConsumer -> updateConsumer.type.equals(type) && updateConsumer.consumer.equals(consumer));
                    if (consumers.isEmpty()) {
                        update_consumers.remove(key);
                    }
                }
                return true;
            }

            @Override
            public ResponseStatus returnValueIfNotRan() {
                return ResponseStatus.OK;
            }

            @Override
            public boolean doNotRun() {
                List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                consumers.removeIf(updateConsumer -> updateConsumer.type.equals(type) && updateConsumer.consumer.equals(consumer));
                return !consumers.isEmpty();
            }
        };
    }

    public RequestAction<ResponseStatus> unsubscribeUpdateEvent(Consumer<SocketClient.KeyValuePair<String>> consumer) {
        String key = " ";
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.UNSUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class) {
            @Override
            public boolean onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                    consumers.removeIf(updateConsumer -> updateConsumer.type.equals(String.class) && updateConsumer.consumer.equals(consumer));
                    if (consumers.isEmpty()) {
                        update_consumers.remove(key);
                    }
                }
                return true;
            }

            @Override
            public ResponseStatus returnValueIfNotRan() {
                return ResponseStatus.OK;
            }

            @Override
            public boolean doNotRun() {
                List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                consumers.removeIf(updateConsumer -> updateConsumer.type.equals(String.class) && updateConsumer.consumer.equals(consumer));
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
            if (type != null) {
                try {
                    T parsed = gson.fromJson(keyValuePair.getValue(), type);
                    consumer.accept(new SocketClient.KeyValuePair<>(keyValuePair.getKey(), parsed));
                } catch (JsonSyntaxException ignored) {
                } catch (Exception e) {
                    logger.severe("There was a exception while running subscriber callback: " + e.getMessage());

                }
            } else {
                UpdateConsumer<String> typedUpdateConsumer2 = (UpdateConsumer<String>) updateConsumer;
                Consumer<? super SocketClient.KeyValuePair<String>> consumer2 = typedUpdateConsumer2.consumer();
                try {
                    consumer2.accept(keyValuePair);
                } catch (Exception e) {
                    logger.severe("There was a exception while running subscriber callback: " + e.getMessage());

                }
            }
        }
    }


    public RequestAction<ResponseStatus> putRawUnsafe(String key, String value) {
        logger.warning("This method is not recommend to be used. Please use XTablesClient#putRaw instead for a more safe put.");
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + value).parsed(), ResponseStatus.class) {
            /**
             * Called before sending the request. Meant to be overridden in subclasses to perform any necessary setup or validation before running the request.
             */
            @Override
            public void beforeRun() {
                if (!Utilities.isValidValue(value)) {
                    logger.warning("Invalid JSON value for key '" + key + "': " + value);
                    logger.warning("The key '" + key + "' may be flagged by the server.");
                }
            }
        };
    }

    public RequestAction<ResponseStatus> putRaw(String key, String value) {
        Utilities.validateKey(key);
        if (!Utilities.isValidValue(value)) throw new JsonSyntaxException("The value is not a valid JSON.");
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + value).parsed(), ResponseStatus.class) {
            /**
             * Returns a value when {@code doNotRun} returns true and the action is not performed. Meant to be overridden in subclasses to provide a default value.
             *
             * @return The default value to return if the request is not run.
             */
            @Override
            public ResponseStatus returnValueIfNotRan() {
                return ResponseStatus.FAIL;
            }

            /**
             * Determines if the request should not run. Meant to be overridden in subclasses to provide specific conditions.
             *
             * @return true if the request should not be sent, false otherwise.
             */
            @Override
            public boolean doNotRun() {
                return Utilities.isValidValue(value);
            }
        };
    }
    public RequestAction<ByteFrame> getByteFrame(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), ByteFrame.class) {
            @Override
            public String formatResult(String result) {
                String[] parts = result.split(" ");
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }

            @Override
            public ByteFrame parseResponse(long startTime, String result) {
                checkFlaggedValue(result);
                return null;
            }
        };
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
    public RequestAction<ResponseStatus> putByteFrame(String key, byte[] value) {
        Utilities.validateKey(key);
        String parsedValue = gson.toJson(new ByteFrame(value));
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

    public RequestAction<ResponseStatus> deleteAll() {
        return delete("");
    }

    public RequestAction<String> getRaw(String key) {
        return getString(key);
    }

    public RequestAction<String> getRawJSON() {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET_RAW_JSON).parsed(), null) {
            /**
             * Parses the response received from the server. Meant to be overridden in subclasses to parse the response based on specific needs.
             *
             * @param startTime The start time of the request, used for calculating latency.
             * @param result    The raw result from the server.
             * @return The parsed response as type T.
             */
            @Override
            public String parseResponse(long startTime, String result) {
                return DataCompression.decompressAndConvertBase64(result);
            }
        };
    }

    public RequestAction<String> getString(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), String.class) {
            @Override
            public String formatResult(String result) {
                String[] parts = result.split(" ");
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }
        };
    }

    public RequestAction<Boolean> getBoolean(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), Boolean.class) {
            /**
             * Parses the response received from the server. Meant to be overridden in subclasses to parse the response based on specific needs.
             *
             * @param startTime The start time of the request, used for calculating latency.
             * @param result    The raw result from the server.
             * @return The parsed response as type T.
             */
            @Override
            public Boolean parseResponse(long startTime, String result) {
                checkFlaggedValue(result);
                return null;
            }

            @Override
            public String formatResult(String result) {
                String[] parts = result.split(" ");
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }
        };
    }

    public RequestAction<ScriptResponse> runScript(String name, String customData) {
        Utilities.validateName(name, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.RUN_SCRIPT, customData == null || customData.trim().isEmpty() ? name : name + " " + customData).parsed(), ScriptResponse.class) {
            /**
             * Parses the response received from the server. Meant to be overridden in subclasses to parse the response based on specific needs.
             *
             * @param startTime The start time of the request, used for calculating latency.
             * @param result    The raw result from the server.
             * @return The parsed response as type T.
             */
            @Override
            public ScriptResponse parseResponse(long startTime, String result) {
                String[] parts = result.split(" ");
                ResponseStatus status = ResponseStatus.valueOf(parts[0]);
                String response = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                if(response == null || response.trim().isEmpty()) response = null;
                if (status.equals(ResponseStatus.OK)) {
                    return new ScriptResponse(response, status);
                } else {
                    return new ScriptResponse(response, ResponseStatus.FAIL);
                }
            }

            @Override
            public String formatResult(String result) {
                String[] parts = result.split(" ");
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }
        };
    }

    public <T> RequestAction<T> getObject(String key, Class<T> type) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), type) {
            @Override
            public String formatResult(String result) {
                String[] parts = result.split(" ");
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }

            @Override
            public T parseResponse(long startTime, String result) {
                checkFlaggedValue(result);
                return null;
            }
        };
    }

    public RequestAction<Integer> getInteger(String key) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), Integer.class) {
            @Override
            public String formatResult(String result) {
                String[] parts = result.split(" ");
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }

            @Override
            public Integer parseResponse(long startTime, String result) {

                checkFlaggedValue(result);
                return null;
            }
        };
    }

    public <T> RequestAction<ArrayList<T>> getArray(String key, Class<T> type) {
        Utilities.validateKey(key);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), type) {
            @Override
            public String formatResult(String result) {
                String[] parts = result.split(" ");
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }

            @Override
            public ArrayList<T> parseResponse(long startTime, String result) {
                checkFlaggedValue(result);
                return null;
            }
        };
    }

    private static void checkFlaggedValue(String result) {
        if (result.split(" ")[0].equals("FLAGGED")) {
            throw new ServerFlaggedValueException("This key is flagged as invalid JSON therefore cannot be parsed. Use XTablesClient#getRaw instead.");
        }
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
            public LatencyInfo parseResponse(long startTime, String result) {
                RequestInfo info = new RequestInfo(result);
                if (info.getTokens().length == 2 && info.getTokens()[0].equals("OK")) {
                    SystemStatistics stats = gson.fromJson(info.getTokens()[1], SystemStatistics.class);
                    long serverTime = stats.getNanoTime();
                    long currentTime = System.nanoTime();
                    long networkLatency = Math.abs(currentTime - serverTime);
                    long roundTripLatency = Math.abs(currentTime - startTime);
                    return new LatencyInfo(networkLatency / 1e6, roundTripLatency / 1e6, stats);
                } else {
                    return null;
                }

            }
        };

    }


    public record LatencyInfo(double networkLatencyMS, double roundTripLatencyMS, SystemStatistics systemStatistics) {
    }
}
