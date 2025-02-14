package org.kobe.xbot.JClient;

import com.google.protobuf.ByteString;
import org.kobe.xbot.JClient.Concurrency.ConcurrentPushHandler;
import org.kobe.xbot.Utilities.Entities.QueuedRequests;
import org.kobe.xbot.Utilities.Entities.Subscriptions;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Exceptions.XTablesException;
import org.kobe.xbot.Utilities.Exceptions.XTablesServerNotFound;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * XTablesClient - A client class for interacting with the XTABLES server.
 * <p>
 * This class is responsible for client-side functionality in the XTABLES system,
 * including resolving the server's IP address, handling versioning information,
 * and providing socket initialization for communication with the XTABLES server.
 * It manages connections and provides utility methods for interacting with the server.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: XTABLES
 * <p>
 * This is part of the XTABLES project and provides client-side functionality for
 * socket communication with the server.
 */
public class XTablesClient extends QueuedRequests implements Subscriptions {
    // =============================================================
    // Static Variables
    // These variables belong to the class itself and are shared
    // across all instances of the class.
    // =============================================================
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    //    public static final int TIME_SYNC_PORT = 3123;
    public static final String UUID = java.util.UUID.randomUUID().toString();
    public static final byte[] success = new byte[]{(byte) 0x01};
    public static final byte[] fail = new byte[]{(byte) 0x00};
    public static final ByteString successByte = ByteString.copyFrom(success);
    public static final ByteString failByte = ByteString.copyFrom(fail);
    // =============================================================
    // Instance Variables
    // These variables are unique to each instance of the class.
    // =============================================================
    private String XTABLES_CLIENT_VERSION = "XTABLES Jero Client v5.2.7 | Build Date: 2/6/2025";

    private final String ip;
    private final XTablesSocketMonitor socketMonitor;
    public final Map<String, List<Consumer<XTableProto.XTableMessage.XTableUpdate>>> subscriptionConsumers;
    public final List<Consumer<XTableProto.XTableMessage.XTableLog>> logConsumers;
    private final ZContext context;
    private final ZMQ.Socket subSocket;
    private final ZMQ.Socket clientRegistrySocket;
    private final SubscribeHandler subscribeHandler;
    private final Map<String, XTableContext> contexts;

    private final int pushSocketPort;
    private final int subscribeSocketPort;
    private final int requestSocketPort;
    private final ConcurrentPushHandler pushHandler;
//    private final ConcurrentRequestHandler requestHandler;

//    private final XTablesTimeSyncHandler timeSyncHandler;

    /**
     * Default constructor for XTablesClient.
     * Initializes the client without specifying an IP address.
     */
    public XTablesClient() {
        this(null);
    }

    /**
     * Constructor for XTablesClient with a specified IP address.
     * Initializes the client with the given IP address.
     * <p>
     * If the IP address is not provided (i.e., it's null), the client will attempt to
     * automatically resolve the IP address of the XTABLES server using mDNS (Multicast DNS).
     * <p>
     * The following default ports are used for socket communication:
     * - Push socket port: 48800
     * - Request socket port: 48801
     * - Subscribe socket port: 48802
     *
     * @param ip The IP address of the XTABLES server. If null, it will resolve the IP automatically.
     */
    public XTablesClient(String ip) {
        this(ip, 48800, 48801, 48802);
    }


    /**
     * Constructor for XTablesClient with a specified IP address and ports.
     * Initializes the client with the given parameters.
     *
     * @param ip                  The IP address of the XTABLES server.
     * @param pushSocketPort      The port for the push socket.
     * @param requestSocketPort   The port for the request socket.
     * @param subscribeSocketPort The port for the subscriber socket.
     */
    public XTablesClient(String ip, int pushSocketPort, int requestSocketPort, int subscribeSocketPort) {
        super();
        if (ip == null) {
            this.ip = resolveHostByName();
            if (this.ip == null) {
                throw new XTablesServerNotFound("Could not resolve XTABLES hostname server.");
            }
        } else {
            this.ip = ip;
        }

        this.requestSocketPort = requestSocketPort;
        this.pushSocketPort = pushSocketPort;
        this.subscribeSocketPort = subscribeSocketPort;

        logger.info("Connecting to XTABLES Server:\n" + "------------------------------------------------------------\n" + "Server IP: " + this.ip + "\n" + "Push Socket Port: " + pushSocketPort + "\n" + "Request Socket Port: " + requestSocketPort + "\n" + "Subscribe Socket Port: " + subscribeSocketPort + "\n" + "Web Interface: " + "http://" + this.ip + ":4880/" + "\n" + "------------------------------------------------------------");
        this.contexts = new LinkedHashMap<>();
        this.context = new ZContext(4);
        this.socketMonitor = new XTablesSocketMonitor(context);
        this.socketMonitor.start();
        this.clientRegistrySocket = context.createSocket(SocketType.PUSH);
        this.clientRegistrySocket.setHWM(500);
        this.clientRegistrySocket.setReconnectIVL(2000);
        this.clientRegistrySocket.setReconnectIVLMax(6000);
        this.socketMonitor.addSocket("REGISTRY", this.clientRegistrySocket);
        this.clientRegistrySocket.connect("tcp://" + this.ip + ":" + pushSocketPort);
//        ZMQ.Socket timeSyncSocket = context.createSocket(SocketType.REQ);
//        timeSyncSocket.setHWM(2);
//        timeSyncSocket.setReceiveTimeOut(1000);
//        timeSyncSocket.setReconnectIVL(1000);
//        timeSyncSocket.setReconnectIVLMax(1000);
//        socketMonitor.addSocket("TIMESYNC", timeSyncSocket);
//        timeSyncSocket.connect("tcp://" + this.ip + ":" + TIME_SYNC_PORT);

        this.subSocket = context.createSocket(SocketType.SUB);
        this.subSocket.setHWM(500);
        this.subSocket.setReconnectIVL(1000);
        this.subSocket.setReconnectIVLMax(1000);
        this.socketMonitor.addSocket("SUBSCRIBE", this.subSocket);
        this.subSocket.connect("tcp://" + this.ip + ":" + subscribeSocketPort);
        this.subscribeHandler = new SubscribeHandler(this.subSocket, this);
        this.subscribeHandler.start();
        this.subscribeHandler.requestSubscribe(XTableProto.XTableMessage.XTableUpdate.newBuilder().setCategory(XTableProto.XTableMessage.XTableUpdate.Category.REGISTRY).build().toByteArray());
        this.subscribeHandler.requestSubscribe(XTableProto.XTableMessage.XTableUpdate.newBuilder().setCategory(XTableProto.XTableMessage.XTableUpdate.Category.INFORMATION).build().toByteArray());
        this.subscriptionConsumers = new HashMap<>();
        this.logConsumers = new ArrayList<>();
        ZMQ.Socket pushSocket = context.createSocket(SocketType.PUSH);
        pushSocket.setHWM(500);
        pushSocket.setReconnectIVL(500);
        pushSocket.setReconnectIVLMax(1000);
        this.socketMonitor.addSocket("PUSH", pushSocket);
        pushSocket.connect("tcp://" + this.ip + ":" + pushSocketPort);
        ZMQ.Socket reqSocket = context.createSocket(SocketType.REQ);
        reqSocket.setHWM(500);
        reqSocket.setReconnectIVL(500);
        reqSocket.setReconnectIVLMax(1000);
        reqSocket.setReceiveTimeOut(3000);
        socketMonitor.addSocket("REQUEST", reqSocket);
        reqSocket.connect("tcp://" + this.ip + ":" + requestSocketPort);

        this.pushHandler = new ConcurrentPushHandler(pushSocket);
        this.pushHandler.start();
//        this.requestHandler = new ConcurrentRequestHandler(reqSocket, this);
//        this.requestHandler.start();
//        super.setHandlers(this.pushHandler, this.requestHandler);
        super.setPushHandler(this.pushHandler);
        super.set(reqSocket, pushSocket, this);

//        this.timeSyncHandler = new XTablesTimeSyncHandler(timeSyncSocket, this);
    }

    @Override
    public CachedSubscriber subscribe(String key) {
        return new CachedSubscriber(key, this);
    }
    @Override
    public CachedSubscriber subscribe(String key, int queue) {
        return new CachedSubscriber(key, this, queue);
    }

    /**
     * Retrieves or creates an XTableContext associated with the specified key.
     * Each XTableContext maintains its own sockets for multi-threaded communication.
     *
     * @param key The unique identifier for the context.
     * @return The XTableContext instance associated with the given key.
     */
    public XTableContext registerXTableContext(String key) {
        return contexts.computeIfAbsent(key, (k) -> {
            ZMQ.Socket pushSocket = context.createSocket(SocketType.PUSH);
            pushSocket.setHWM(500);
            pushSocket.setReconnectIVL(500);
            pushSocket.setReconnectIVLMax(1000);
            this.socketMonitor.addSocket("PUSH-" + key, pushSocket);
            pushSocket.connect("tcp://" + this.ip + ":" + pushSocketPort);
            ZMQ.Socket reqSocket = context.createSocket(SocketType.REQ);
            reqSocket.setHWM(500);
            reqSocket.setReconnectIVL(500);
            reqSocket.setReconnectIVLMax(1000);
            reqSocket.setReceiveTimeOut(3000);
            socketMonitor.addSocket("REQUEST-" + key, reqSocket);
            reqSocket.connect("tcp://" + this.ip + ":" + requestSocketPort);
            return new XTableContext(pushSocket, reqSocket, key, this);

        });
    }

    /**
     * Registers a new threaded XTableContext associated with the given key.
     * This is a wrapper around `getXTableContext` for convenience.
     *
     * @param key The unique identifier for the context.
     * @return The newly created or retrieved XTableContext.
     */
    public XTableContext registerNewThreadedContext(String key) {
        return registerXTableContext(key);
    }

    /**
     * Shuts down and removes an XTableContext associated with the given key.
     * If the context exists, it is closed and removed from the context map.
     *
     * @param key The unique identifier for the context to be shut down.
     */
    public void shutdownXTableContext(String key) {
        XTableContext context = contexts.remove(key);
        if (context != null) {
            context.close();
        }
    }


    /**
     * Subscribes a consumer-to-server log update.
     * <p>
     * This method registers a consumer to receive log updates from the server.
     * It ensures the subscription to the appropriate log category via the subscription socket.
     *
     * @param consumer a Consumer function to handle incoming server log messages
     * @return true if the subscription was successful, false otherwise
     */
    @Override
    public boolean subscribeToServerLogs(Consumer<XTableProto.XTableMessage.XTableLog> consumer) {
        boolean success = this.subscribeHandler.requestSubscribe(XTableProto.XTableMessage.XTableUpdate.newBuilder().setCategory(XTableProto.XTableMessage.XTableUpdate.Category.LOG).build().toByteArray());
        if (success) {
            return this.logConsumers.add(consumer);
        }
        return false;
    }

    /**
     * Unsubscribes a consumer from server log updates.
     * <p>
     * This method removes a previously registered consumer from receiving server log updates.
     * If no more consumers are registered, it unsubscribes from the log category on the subscription socket.
     *
     * @param consumer a Consumer function previously registered to handle server log messages
     * @return true if the consumer was removed successfully, false otherwise
     */
    @Override
    public boolean unsubscribeToServerLogs(Consumer<XTableProto.XTableMessage.XTableLog> consumer) {
        boolean success = this.logConsumers.remove(consumer);
        if (this.logConsumers.isEmpty()) {
            return this.subscribeHandler.requestUnsubscription(XTableProto.XTableMessage.XTableUpdate.newBuilder().setCategory(XTableProto.XTableMessage.XTableUpdate.Category.LOG).build().toByteArray());
        }
        return success;
    }

    /**
     * Subscribes to a specific key and associates a consumer to process updates for that key.
     *
     * @param key      The key to subscribe to.
     * @param consumer The consumer function that processes updates for the specified key.
     * @return true if the subscription and consumer addition were successful, false otherwise.
     */
    @Override
    public boolean subscribe(String key, Consumer<XTableProto.XTableMessage.XTableUpdate> consumer) {
        boolean success = this.subscribeHandler.requestSubscribe(XTableProto.XTableMessage.XTableUpdate.newBuilder().setKey(key).build().toByteArray());
        if (success) {
            return this.subscriptionConsumers.computeIfAbsent(key, (k) -> new ArrayList<>()).add(consumer);
        }
        return false;
    }

    /**
     * Subscribes to updates for all keys and associates a consumer to process updates for all keys.
     *
     * @param consumer The consumer function that processes updates for any key.
     * @return true if the subscription and consumer addition were successful, false otherwise.
     */
    @Override
    public boolean subscribe(Consumer<XTableProto.XTableMessage.XTableUpdate> consumer) {
        boolean success = this.subscribeHandler.requestSubscribe("".getBytes(StandardCharsets.UTF_8));
        if (success) {
            return this.subscriptionConsumers.computeIfAbsent("", (k) -> new ArrayList<>()).add(consumer);
        }
        return false;
    }

    /**
     * Unsubscribes a specific consumer from a given key. If no consumers remain for the key,
     * it unsubscribes the key from the subscription socket.
     *
     * @param key      The key to unsubscribe from.
     * @param consumer The consumer function to remove from the key's subscription.
     * @return true if the consumer was successfully removed or the key was unsubscribed, false otherwise.
     */
    @Override
    public boolean unsubscribe(String key, Consumer<XTableProto.XTableMessage.XTableUpdate> consumer) {
        if (this.subscriptionConsumers.containsKey(key)) {
            List<Consumer<XTableProto.XTableMessage.XTableUpdate>> list = this.subscriptionConsumers.get(key);
            boolean success = list.remove(consumer);
            if (list.isEmpty()) {
                this.subscriptionConsumers.remove(key);
                return this.subscribeHandler.requestUnsubscription(XTableProto.XTableMessage.XTableUpdate.newBuilder().setKey(key).build().toByteArray());
            }
            return success;
        } else {
            return this.subscribeHandler.requestUnsubscription(XTableProto.XTableMessage.XTableUpdate.newBuilder().setKey(key).build().toByteArray());
        }
    }

    /**
     * Unsubscribes a specific consumer from all keys. If no consumers remain for all keys,
     * it unsubscribes from the subscription socket for all keys.
     *
     * @param consumer The consumer function to remove from all key subscriptions.
     * @return true if the consumer was successfully removed or unsubscribed from all keys, false otherwise.
     */
    @Override
    public boolean unsubscribe(Consumer<XTableProto.XTableMessage.XTableUpdate> consumer) {
        if (this.subscriptionConsumers.containsKey("")) {
            List<Consumer<XTableProto.XTableMessage.XTableUpdate>> list = this.subscriptionConsumers.get("");
            boolean success = list.remove(consumer);
            if (list.isEmpty()) {
                this.subscriptionConsumers.remove("");
                return this.subscribeHandler.requestUnsubscription("".getBytes(StandardCharsets.UTF_8));
            }
            return success;
        } else {
            return this.subscribeHandler.requestUnsubscription("".getBytes(StandardCharsets.UTF_8));
        }
    }




    /**
     * Gracefully shuts down the XTablesClient.
     * - Destroys the ZMQ context if it exists and is not yet closed.
     * - Interrupts and stops the subscription handler thread if it's active.
     * - Logs the shutdown event for debugging and monitoring.
     */
    public void shutdown() {
        if (this.context != null && !this.context.isClosed()) {
            this.context.destroy();
        }
        if (this.subscribeHandler != null && !this.subscribeHandler.isInterrupted() && this.subscribeHandler.isAlive()) {
            this.subscribeHandler.interrupt();
        }
        if (this.pushHandler != null && !this.pushHandler.isInterrupted() && this.pushHandler.isAlive()) {
            this.pushHandler.interrupt();
        }
        logger.info("XTablesClient has been shutdown gracefully.");
    }

    /**
     * Returns the version information of the XTables client.
     *
     * @return A string containing the version details.
     */
    public String getVersion() {
        return XTABLES_CLIENT_VERSION;
    }

    /**
     * Adds a custom property to the version string.
     *
     * @param prop The property to be added to the version string.
     * @return The updated version string with the new property.
     */
    public String addVersionProperty(String prop) {
        XTABLES_CLIENT_VERSION = XTABLES_CLIENT_VERSION + " | " + prop.trim();
        return XTABLES_CLIENT_VERSION;
    }

    /**
     * Resolves the host by name (XTABLES.local).
     * This method attempts to resolve the IP address of the XTABLES server.
     * If a resolution fails, it retries every second until successful.
     *
     * @return The resolved InetAddress of the XTABLES server, or null if failed to resolve.
     */
    private String resolveHostByName() {
//        String tempIp = TempConnectionManager.get();
//        if (tempIp != null) {
//            logger.info("Retrieved cached server IP: " + tempIp);
//            return tempIp;
//        }
        InetAddress address = null;

        while (address == null) {
            try {
                logger.info("Attempting to resolve host 'XTABLES.local'...");
                address = Inet4Address.getByName("XTABLES.local");
                logger.info("Host resolution successful: IP address found at " + address.getHostAddress());
                logger.info("Proceeding with socket client initialization.");
            } catch (UnknownHostException e) {
                try (JmDNS jmdns = JmDNS.create()) {
                    logger.severe("Failed to resolve 'XTABLES.local'. Host not found. Now attempting jmDNS...");
                    logger.severe("Exception details: " + e);
                    ServiceInfo serviceInfo = jmdns.getServiceInfo("_xtables._tcp.local.", "XTablesService", false, 3000);
                    if (serviceInfo != null) {
                        Optional<Inet4Address> inet4Address = Arrays.stream(serviceInfo.getInet4Addresses()).filter(Objects::nonNull).findFirst();
                        if (inet4Address.isPresent()) {
                            address = inet4Address.get();
                        } else {
                            logger.severe("Failed to retrieve IPv4 address from XTablesService.");
                        }
                    } else {
                        logger.severe("Failed to resolve 'XTablesService' using jmDNS. Retrying in a second...");
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warning("Retry wait interrupted. Exiting...");
                        return null;
                    }
                } catch (IOException ei) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        logger.warning("Retry wait interrupted. Exiting...");
                        Thread.currentThread().interrupt();
                    }
                    logger.severe("Exception on resolving XTABLES server: " + ei.getMessage());
                    return resolveHostByName();
                }
            }
        }
        //        TempConnectionManager.set(foundIp);
        return address.getHostAddress();
    }

    /**
     * Retrieves the REQ (Request) socket port.
     * The REQ socket is typically used for sending requests in a request-reply pattern.
     *
     * @return The ZMQ.Socket instance representing the REQ socket.
     */
    public int getRequestSocketPort() {
        return requestSocketPort;
    }

    /**
     * Retrieves the PUSH socket.
     * The PUSH socket is used for sending messages in a one-way pattern, usually to a PULL socket.
     *
     * @return The ZMQ.Socket instance representing the PUSH socket.
     */
    public int getPushSocketPort() {
        return pushSocketPort;
    }

    /**
     * Retrieves the PUSH socket for registry.
     * The PUSH socket is used for sending messages in a one-way pattern, usually to a PULL socket.
     *
     * @return The ZMQ.Socket instance representing the PUSH socket.
     */
    public ZMQ.Socket getRegsitrySocket() {
        return clientRegistrySocket;
    }

    /**
     * Retrieves the socket monitor.
     * The socket monitor is responsible for monitoring the state of the sockets,
     * handling events such as connections, disconnections, and errors.
     * It provides real-time updates about the status of each monitored socket.
     *
     * @return The XTablesSocketMonitor instance responsible for monitoring socket events.
     */
    public XTablesSocketMonitor getSocketMonitor() {
        return socketMonitor;
    }

    /**
     * Retrieves the SUB (Subscriber) socket.
     * The SUB socket is used to subscribe to messages from a PUB (Publisher) socket.
     *
     * @return The ZMQ.Socket instance representing the SUB socket.
     */
    public ZMQ.Socket getSubSocket() {
        return subSocket;
    }

    /**
     * Retrieves the port number used for the SUB (Subscriber) socket.
     * This socket is used to receive updates from the XTABLES server.
     *
     * @return The port number of the subscription socket.
     */
    public int getSubscribeSocketPort() {
        return subscribeSocketPort;
    }

    /**
     * Retrieves the IP address of the XTABLES server.
     * This is either manually provided during initialization or resolved automatically.
     *
     * @return The IP address of the server as a string.
     */
    public String getIp() {
        return ip;
    }

    /**
     * Retrieves the ZMQ context associated with this client.
     * The context manages sockets and handles networking resources.
     *
     * @return The ZContext instance used by this client.
     */
    public ZContext getContext() {
        return context;
    }

    /**
     * Asynchronously retrieves the default XTablesClientManager instance.
     * This allows for non-blocking initialization of the client manager.
     *
     * @return A new instance of XTablesClientManager.
     */
    public static XTablesClientManager getDefaultClientAsynchronously() {
        return new XTablesClientManager();
    }
}
