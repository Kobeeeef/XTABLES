package org.kobe.xbot.ClientLite;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.kobe.xbot.Utilities.*;
import org.kobe.xbot.Utilities.Entities.KeyValuePair;
import org.kobe.xbot.Utilities.Entities.UpdateConsumer;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Handles client-side interactions with the XTablesServer server.
 * This class provides various constructors to connect to the XTablesServer server using different configurations:
 * direct connection with specified address and port, or service discovery via mDNS with optional settings.
 * <p>
 * The XTablesClient class supports operations like caching, subscribing to updates and delete events,
 * running scripts and managing key-value pairs.
 * It uses Gson for JSON parsing and integrates with a custom SocketClient for network communication.
 * <p>
 *
 * @author Kobe
 */


public class XTablesClient {


    public static final String XTABLES_CLIENT_VERSION =
            "XTABLES Client v4.0.0 | Build Date: 11/14/2024";



    public XTablesClient() {
        this(1735, true, 10, false);
    }
    /**
     * Connects to the XTablesServer instance using the system-level DNS resolver and port with direct connection settings.
     *
     * @param SERVER_PORT the port of the server to connect to.
     * @param MAX_THREADS the maximum number of threads to use for client operations.
     * @throws IllegalArgumentException if the server port is 5353, which is reserved for mDNS services.
     */
    public XTablesClient(int SERVER_PORT, boolean enableZMQ, int MAX_THREADS, boolean async) {
        if (SERVER_PORT == 5353)
            throw new IllegalArgumentException("The port 5353 is reserved for mDNS services.");

        if (async) {
            this.client = new SocketClient(null, SERVER_PORT, enableZMQ, 10, MAX_THREADS, this);
            Thread thread = new Thread(() -> {
                InetAddress address = resolveHostByName();
                if (address == null) {
                    logger.fatal("Failed to find address...");
                    return;
                }
                client.setSERVER_ADDRESS(address.getHostAddress());
                client.connect();
                client.setUpdateConsumer(this::on_update);
                client.setDeleteConsumer(this::on_delete);
            });
            thread.setDaemon(true);
            thread.start();
        } else {
            InetAddress address = resolveHostByName();
            if (address == null) {
                logger.fatal("Failed to find address...");
                return;
            }
            initializeClient(address.getHostAddress(), SERVER_PORT, enableZMQ, MAX_THREADS, false);

        }
    }


    private InetAddress resolveHostByName() {
        InetAddress address = null;
        while (address == null) {
            try {
                logger.info("Attempting to resolve host 'XTABLES.local'...");
                address = Inet4Address.getByName("XTABLES.local");
                logger.info("Host resolution successful: IP address found at " + address.getHostAddress());
                logger.info("Proceeding with socket client initialization.");
            } catch (UnknownHostException e) {
                logger.severe("Failed to resolve 'XTABLES.local'. Host not found. Retrying in 2 seconds...");
                logger.severe("Exception details: " + e);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warning("Retry wait interrupted. Exiting...");
                    return null;
                }
            }
        }
        return address;
    }

    public XTablesClient(String name, int MAX_THREADS, boolean useCache) {
        try {
            InetAddress addr = null;
            while (addr == null) {
                addr = Utilities.getLocalInetAddress();
                if (addr == null) {
                    logger.severe("No non-loopback IPv4 address found. Trying again in 1 second...");
                    Thread.sleep(1000);
                }
            }
            try (JmDNS jmdns = JmDNS.create(addr)) {
                CountDownLatch serviceLatch = new CountDownLatch(1);
                final boolean[] serviceFound = {false};
                final String[] serviceAddressIP = new String[1];
                final Integer[] socketServiceServerPort = new Integer[1];

                jmdns.addServiceListener("_xtables._tcp.local.", new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {
                        logger.info("Service found: " + event.getName());
                        jmdns.requestServiceInfo(event.getType(), event.getName(), true);
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        logger.info("Service removed: " + event.getName());
                    }

                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        synchronized (serviceFound) {
                            if (!serviceFound[0]) {
                                ServiceInfo serviceInfo = event.getInfo();
                                String serviceAddress = serviceInfo.getInet4Addresses()[0].getHostAddress();
                                int socketServerPort = -1;
                                String portStr = serviceInfo.getPropertyString("port");
                                try {
                                    socketServerPort = Integer.parseInt(portStr);
                                } catch (NumberFormatException e) {
                                    logger.warning("Invalid port format from mDNS attribute. Waiting for next resolve...");
                                }

                                String localAddress = null;
                                if (name != null && name.equalsIgnoreCase("localhost")) {
                                    try {
                                        localAddress = Utilities.getLocalIPAddress();
                                        serviceAddress = localAddress;
                                    } catch (Exception e) {
                                        logger.severe("Could not find localhost address: " + e.getMessage());
                                    }
                                }
                                if (socketServerPort != -1 && serviceAddress != null && !serviceAddress.trim().isEmpty() && (localAddress != null || serviceInfo.getName().equals(name) || serviceAddress.equals(name) || name == null)) {
                                    serviceFound[0] = true;
                                    logger.info("Service resolved: " + serviceInfo.getQualifiedName());
                                    logger.info("Address: " + serviceAddress + " Port: " + socketServerPort);
                                    serviceAddressIP[0] = serviceAddress;
                                    socketServiceServerPort[0] = socketServerPort;
                                    serviceLatch.countDown();
                                }
                            }
                        }
                    }
                });
                if (name == null) logger.info("Listening for first instance of XTABLES service on port 5353...");
                else logger.info("Listening for '" + name + "' XTABLES services on port 5353...");
                while (serviceLatch.getCount() > 0 && !serviceFound[0] && !Thread.currentThread().isInterrupted()) {
                    try {
                        jmdns.requestServiceInfo("_xtables._tcp.local.", "XTablesService", true, 1000);
                    } catch (Exception ignored) {
                    }
                }
                serviceLatch.await();
                logger.info("Service latch released, proceeding to close mDNS services...");
                jmdns.close();
                logger.info("mDNS service successfully closed. Service discovery resolver shut down.");

                if (serviceAddressIP[0] == null || socketServiceServerPort[0] == null) {
                    throw new RuntimeException("The service address or port could not be found.");
                } else {
                    initializeClient(serviceAddressIP[0], socketServiceServerPort[0], true, MAX_THREADS, useCache);
                }
            }
        } catch (IOException | InterruptedException e) {
            logger.severe("Service discovery error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    public XTablesClient(String ip, int port) {
        initializeClient(ip, port, true, 10, false);

    }
// ---------------------------------------------------------------
// ---------------- Methods and Fields ---------------------------
// ---------------------------------------------------------------

    private final XTablesLogger logger = XTablesLogger.getLogger();
    private SocketClient client;
    private final Gson gson = new Gson();
    private XTablesData cache;
    private long cacheFetchCooldown = 10000;
    private boolean isCacheReady = false;
    private final CountDownLatch latch = new CountDownLatch(1);
    private Thread cacheThread;
    public final HashMap<String, List<UpdateConsumer<?>>> update_consumers = new HashMap<>();
    public final List<Consumer<String>> delete_consumers = new ArrayList<>();

    private void initializeClient(String SERVER_ADDRESS, int SERVER_PORT, boolean enableZMQ, int MAX_THREADS, boolean useCache) {
        this.client = new SocketClient(SERVER_ADDRESS, SERVER_PORT, enableZMQ, 500, MAX_THREADS, this);
        Thread thread = new Thread(() -> {
            client.connect();
            client.setUpdateConsumer(this::on_update);
            client.setDeleteConsumer(this::on_delete);
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
        cache = new XTablesData();
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

    public XTablesData getCache() {
        return cache;
    }

    public void updateServerAddress(String SERVER_ADDRESS, int SERVER_PORT) {
        client.setSERVER_ADDRESS(SERVER_ADDRESS).setSERVER_PORT(SERVER_PORT).reconnect();
    }


    public <T> RequestAction<ResponseStatus> subscribeUpdateEvent(String key, Class<T> type, Consumer<KeyValuePair<T>> consumer) {
        Utilities.validateKey(key, true);
        return subscribeUpdateEventNoCheck(key, type, consumer);
    }

    private <T> RequestAction<ResponseStatus> subscribeUpdateEventNoCheck(String key, Class<T> type, Consumer<KeyValuePair<T>> consumer) {
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

    public boolean resubscribeToDeleteEvents() {
        return !delete_consumers.isEmpty();
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

    public RequestAction<ResponseStatus> subscribeUpdateEvent(Consumer<KeyValuePair<String>> consumer) {
        String key = " ";
        return subscribeUpdateEventNoCheck(key, null, consumer);
    }

    public <T> RequestAction<ResponseStatus> unsubscribeUpdateEvent(String key, Class<T> type, Consumer<KeyValuePair<T>> consumer) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.UNSUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class) {
            @Override
            public boolean onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                    consumers.removeIf(updateConsumer -> updateConsumer.getType().equals(type) && updateConsumer.getConsumer().equals(consumer));
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
                consumers.removeIf(updateConsumer -> updateConsumer.getType().equals(type) && updateConsumer.getConsumer().equals(consumer));
                return !consumers.isEmpty();
            }
        };
    }

    public RequestAction<ResponseStatus> unsubscribeUpdateEvent(Consumer<KeyValuePair<String>> consumer) {
        String key = " ";
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.UNSUBSCRIBE_UPDATE, key).parsed(), ResponseStatus.class) {
            @Override
            public boolean onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
                    consumers.removeIf(updateConsumer -> updateConsumer.getType().equals(String.class) && updateConsumer.getConsumer().equals(consumer));
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
                consumers.removeIf(updateConsumer -> updateConsumer.getType().equals(String.class) && updateConsumer.getConsumer().equals(consumer));
                return !consumers.isEmpty();
            }
        };
    }

    public RequestAction<ResponseStatus> subscribeDeleteEvent(Consumer<String> consumer) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.SUBSCRIBE_DELETE).parsed(), ResponseStatus.class) {
            @Override
            public boolean onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    delete_consumers.add(consumer);
                }
                return true;
            }

        };
    }

    public RequestAction<ResponseStatus> unsubscribeDeleteEvent(Consumer<String> consumer) {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.UNSUBSCRIBE_DELETE).parsed(), ResponseStatus.class) {
            @Override
            public boolean onResponse(ResponseStatus result) {
                if (result.equals(ResponseStatus.OK)) {
                    delete_consumers.remove(consumer);
                }
                return true;
            }

            @Override
            public ResponseStatus returnValueIfNotRan() {
                return ResponseStatus.OK;
            }

            @Override
            public boolean doNotRun() {
                delete_consumers.remove(consumer);
                return !delete_consumers.isEmpty();
            }
        };
    }

    private <T> void on_update(KeyValuePair<String> keyValuePair) {
        processUpdate(keyValuePair, keyValuePair.getKey());
        if (update_consumers.containsKey(" ")) {
            processUpdate(keyValuePair, " ");
        }
    }

    private void on_delete(String key) {
        synchronized (delete_consumers) {
            for (Consumer<String> consumer : delete_consumers) {
                try {
                    consumer.accept(key);
                } catch (Exception e) {
                    logger.severe("There was a exception while running delete subscriber callback: " + e.getMessage());
                }
            }
        }
    }

    private <T> void processUpdate(KeyValuePair<String> keyValuePair, String key) {
        if (update_consumers.containsKey(key)) {
            List<UpdateConsumer<?>> consumers = update_consumers.computeIfAbsent(key, k -> new ArrayList<>());
            for (UpdateConsumer<?> updateConsumer : consumers) {
                UpdateConsumer<T> typedUpdateConsumer = (UpdateConsumer<T>) updateConsumer;
                Consumer<? super KeyValuePair<T>> consumer = typedUpdateConsumer.getConsumer();
                Class<T> type = typedUpdateConsumer.getType();
                if (type != null) {
                    try {
                        T parsed = gson.fromJson(keyValuePair.getValue(), type);
                        consumer.accept(new KeyValuePair<>(keyValuePair.getKey(), parsed));
                    } catch (JsonSyntaxException ignored) {
                    } catch (Exception e) {
                        logger.severe("There was a exception while running update subscriber callback: " + e.getMessage());

                    }
                } else {
                    UpdateConsumer<String> typedUpdateConsumer2 = (UpdateConsumer<String>) updateConsumer;
                    Consumer<? super KeyValuePair<String>> consumer2 = typedUpdateConsumer2.getConsumer();
                    try {
                        consumer2.accept(keyValuePair);
                    } catch (Exception e) {
                        logger.severe("There was a exception while running subscriber callback: " + e.getMessage());

                    }
                }
            }
        }
    }


    public RequestAction<ResponseStatus> putRawUnsafe(String key, String value) {
        logger.warning("This method is not recommend to be used. Please use XTablesClient#putRaw instead for a more safe put.");
        Utilities.validateKey(key, true);
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

    public RequestAction<ResponseStatus> putString(String key, String value) {
        Utilities.validateKey(key, true);
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> putRaw(String key, String value) {
        Utilities.validateKey(key, true);
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
                return !Utilities.isValidValue(value);
            }
        };
    }

    /**
     * Sends a raw string value to the server with the specified key.
     *
     * @param key   the key associated with the value.
     * @param value the string value to send.
     */
    public void executePutString(String key, String value) {
        client.sendMessageRaw("IGNORED:PUT " + key + " \"" + value + "\"");
    }

    /**
     * Sends an integer value to the server with the specified key.
     *
     * @param key   the key associated with the value.
     * @param value the integer value to send.
     */
    public void executePutInteger(String key, int value) {
        client.sendMessageRaw("IGNORED:PUT " + key + " " + value);
    }

    /**
     * Sends a double value to the server with the specified key.
     *
     * @param key   the key associated with the value.
     * @param value the double value to send.
     */
    public void executePutDouble(String key, double value) {
        client.sendMessageRaw("IGNORED:PUT " + key + " " + value);
    }

    /**
     * Sends a long value to the server with the specified key.
     *
     * @param key   the key associated with the value.
     * @param value the long value to send.
     */
    public void executePutLong(String key, long value) {
        client.sendMessageRaw("IGNORED:PUT " + key + " " + value);
    }

    /**
     * Sends a boolean value to the server with the specified key.
     *
     * @param key   the key associated with the value.
     * @param value the boolean value to send.
     */
    public void executePutBoolean(String key, boolean value) {
        client.sendMessageRaw("IGNORED:PUT " + key + " " + value);
    }

    /**
     * Sends an array to the server with the specified key.
     *
     * @param key   the key associated with the array.
     * @param value the list of values to send, which is converted to JSON format.
     * @param <T>   the type of the array elements.
     */
    public <T> void executePutArray(String key, List<T> value) {
        String parsedValue = gson.toJson(value);
        client.sendMessageRaw("IGNORED:PUT " + key + " " + parsedValue);
    }

    /**
     * Sends an object to the server with the specified key.
     *
     * @param key   the key associated with the object.
     * @param value the object to send, which is converted to JSON format.
     */
    public void executePutObject(String key, Object value) {
        String parsedValue = gson.toJson(value);
        client.sendMessageRaw("IGNORED:PUT " + key + " " + parsedValue);
    }

    public boolean pushZMQStringUpdate(String key, String value) {
        return client.pushZMQ(key + " \"" + value + "\"");
    }
    public boolean pushZMQRawUpdate(String key, String value) {
        return client.pushZMQ(key + " " + value);
    }
    public String[] receiveNextZMQ() {
       return client.receive_nextZMQ();
    }


    public RequestAction<ResponseStatus> renameKey(String key, String newName) {
        Utilities.validateKey(key, true);
        Utilities.validateName(newName, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + newName).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> putBoolean(String key, Boolean value) {
        Utilities.validateKey(key, true);
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }

    public <T> RequestAction<ResponseStatus> putArray(String key, List<T> value) {
        Utilities.validateKey(key, true);
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> putByteFrame(String key, byte[] value) {
        Utilities.validateKey(key, true);
        String parsedValue = gson.toJson(new ByteFrame(value));
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> putInteger(String key, Integer value) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + value).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> putObject(String key, Object value) {
        Utilities.validateKey(key, true);
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PUT, key + " " + parsedValue).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> delete(String key) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.DELETE, key).parsed(), ResponseStatus.class);
    }

    public RequestAction<ResponseStatus> deleteAll() {
        return delete("");
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

    public RequestAction<ByteFrame> getByteFrame(String key) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), ByteFrame.class) {
            @Override
            public String formatResult(String result) {
                String[] parts = result.split(" ");
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }

            @Override
            public ByteFrame parseResponse(long startTime, String result) {
                return null;
            }
        };
    }

    public RequestAction<String> getString(String key) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), String.class);
    }
    public RequestAction<String> getRaw(String key) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), null);
    }
    public RequestAction<Boolean> getBoolean(String key) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), Boolean.class);
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
                if (response == null || response.trim().isEmpty()) response = null;
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
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), type);
    }

    public RequestAction<Integer> getInteger(String key) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET, key).parsed(), Integer.class);
    }


    public RequestAction<ArrayList<String>> getTables(String key) {
        Utilities.validateKey(key, true);
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.GET_TABLES, key).parsed(), ArrayList.class);
    }

    public RequestAction<ArrayList<String>> getTables() {
        return getTables("");
    }

    public RequestAction<ResponseStatus> rebootServer() {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.REBOOT_SERVER).parsed(), ResponseStatus.class);
    }
    public RequestAction<LatencyInfo> ping_latency() {
        return new RequestAction<>(client, new ResponseInfo(null, MethodType.PING).parsed()) {
            @Override
            public LatencyInfo parseResponse(long startTime, String result) {
                RequestInfo info = new RequestInfo(result);
                if (info.getTokens().length == 2 && info.getTokens()[0].equals("OK")) {
                    SystemStatistics stats = gson.fromJson(info.getTokens()[1], SystemStatistics.class);
                    long currentTime = System.nanoTime();
                    long roundTripLatency = Math.abs(currentTime - startTime);
                    return new LatencyInfo(((double) roundTripLatency / 2) / 1e6, roundTripLatency / 1e6, stats);
                } else {
                    return null;
                }

            }
        };

    }
    public <T> RequestAction<T> sendCustomMessage(MethodType method, String message, Class<T> type) {
        return new RequestAction<>(client, new ResponseInfo(null, method, message).parsed(), type);
    }

    public SocketClient getSocketClient() {
        return client;
    }


}
