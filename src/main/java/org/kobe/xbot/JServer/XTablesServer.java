package org.kobe.xbot.JServer;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Exceptions.XTablesException;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.TableFormatter;
import org.kobe.xbot.Utilities.Utilities;
import org.kobe.xbot.Utilities.XTableStatus;
import org.kobe.xbot.Utilities.XTablesData;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * XTablesServer - A server class for managing JeroMQ-based messaging and mDNS service registration.
 * <p>
 * This class initializes and manages the server components, including JeroMQ sockets for handling
 * PUB, PULL, and REQ/REP messaging patterns. It also supports mDNS registration for service discovery.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: XTABLES
 * <p>
 * This is part of the XTABLES project and provides core server functionality
 * in a multithreaded environment.
 */
public class XTablesServer {
    public static final XTablesData table = new XTablesData();
    // =============================================================
    // Static Variables
    // These variables belong to the class itself and are shared
    // across all instances of the class.
    // =============================================================
    private static final int SERVICE_PORT = 5353;
    private static final String SERVICE_NAME = "XTablesService";
    private static final AtomicReference<XTablesServer> instance = new AtomicReference<>();
    private static final AtomicReference<XTableStatus> status = new AtomicReference<>(XTableStatus.OFFLINE);
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private static final CountDownLatch latch = new CountDownLatch(1);
    private static final Gson gson = new Gson();
    private static Thread main;
    // =============================================================
    // Instance Variables
    // These variables are unique to each instance of the class.
    // =============================================================
    public final AtomicInteger pullMessages = new AtomicInteger(0);
    public final AtomicInteger replyMessages = new AtomicInteger(0);
    public final AtomicInteger publishMessages = new AtomicInteger(0);
    private final int pullPort;
    private final int repPort;
    private final int pubPort;
    private final String version;
    private final AtomicBoolean debug = new AtomicBoolean(false);
    private final boolean additionalFeatures;
    private ZMQ.Socket pubSocket;
    public ZContext context;
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private PushPullRequestHandler pushPullRequestHandler;
    private ReplyRequestHandler replyRequestHandler;
//    private TimeSyncHandler timeSyncHandler;
    private ClientRegistry clientRegistry;
    private WebInterface webInterface;
    private XTablesSocketMonitor socketMonitor;
    private XTablesMessageRate rate;
    private int iterationSpeed;
    private final AtomicReference<ByteString> clientRegistrySessionId = new AtomicReference<>();
    private final ScheduledExecutorService scheduler;
    public XTablesMessageQueue publishQueue;
    /**
     * Constructor for initializing the server with specified ports.
     *
     * @param pullServerPort Port for the PULL socket
     * @param repServerPort  Port for the REP socket
     * @param pubServerPort  Port for the PUB socket
     */
    private XTablesServer(String version, int pullServerPort, int repServerPort, int pubServerPort, boolean additionalFeatures) {
        this.pullPort = pullServerPort;
        this.repPort = repServerPort;
        this.pubPort = pubServerPort;
        this.version = version;
        this.additionalFeatures = additionalFeatures;
        instance.set(this);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(() -> {
            String[] headers = {"Type", "Amount"};
            String[][] data = {
                    {"PULL", NumberFormat.getInstance().format(pullMessages.get())},
                    {"PUBLISH", NumberFormat.getInstance().format(publishMessages.get())},
                    {"REPLY", NumberFormat.getInstance().format(replyMessages.get())}
            };
            logger.info("Total message count in the last minute:\n" + TableFormatter.makeTable(headers, data));
            pullMessages.set(0);
            replyMessages.set(0);
            publishMessages.set(0);
        }, 60, 60, TimeUnit.SECONDS);
        start();
    }

    /**
     * Initializes the XTablesServer instance and starts the main server thread.
     * Ensures the server is properly cleaned up during JVM shutdown.
     *
     * @param pullSocketPort    Port for the PULL socket
     * @param replySocketPort   Port for the REP socket
     * @param publishSocketPort Port for the PUB socket
     * @return The initialized server instance
     */
    public static XTablesServer initialize(String version, int pullSocketPort, int replySocketPort, int publishSocketPort, boolean additionalFeatures) {
        if (instance.get() != null) {
            return instance.get();
        }
        status.set(XTableStatus.STARTING);
        main = new Thread(() -> new XTablesServer(version, pullSocketPort, replySocketPort, publishSocketPort, additionalFeatures));
        main.setName("XTABLES-SERVER");
        main.setDaemon(false);
        main.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown initiated. Cleaning up resources...");
            if (instance.get() != null) {
                logger.info("Stopping server instance...");
                try {
                    instance.get().cleanup();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                logger.info("Server shutdown completed successfully.");
            } else {
                logger.warning("No active server instance found during shutdown.");
            }
        }));


        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.severe("Main latch interrupted: " + e.getMessage());
            System.exit(1);
        }
        if (additionalFeatures) {
            instance.get().iterationSpeed = Utilities.measureWhileLoopIterationsPerSecond();
            String formattedSpeed = NumberFormat.getInstance().format(instance.get().iterationSpeed);
            logger.info("Server can process approximately " + formattedSpeed + " iterations per second.");
        }
        return instance.get();
    }

    /**
     * Retrieves the current instance of the XTablesServer.
     *
     * @return The current server instance, or null if not initialized
     */
    public static XTablesServer getInstance() {
        return instance.get();
    }

    /**
     * Starts the server by initializing sockets, threads, and mDNS.
     */
    private void start() {
        try {
            try {
                this.cleanup();
            } catch (Exception exception) {
                logger.fatal("There was an error cleaning up the server: " + exception.getMessage());
                System.exit(1);
            }
            Utilities.warmupProtobuf();
            this.context = new ZContext(3);
            this.pubSocket = context.createSocket(SocketType.PUB);
            this.pubSocket.setHWM(500);
            this.pubSocket.bind("tcp://*:" + pubPort);
            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.setHWM(500);
            pullSocket.bind("tcp://*:" + pullPort);
            ZMQ.Socket repSocket = context.createSocket(SocketType.REP);
            repSocket.setHWM(500);
            repSocket.bind("tcp://*:" + repPort);

//            ZMQ.Socket syncSocket = context.createSocket(SocketType.REP);
//            syncSocket.setHWM(50);
//            syncSocket.setLinger(-1);
//            syncSocket.setHeartbeatTimeout(5000);
//            syncSocket.setHeartbeatIvl(1000);
//            syncSocket.bind("tcp://*:3123");

            this.publishQueue = new XTablesMessageQueue(this.pubSocket, this);
            this.publishQueue.start();
            this.pushPullRequestHandler = new PushPullRequestHandler(pullSocket, this);
            this.pushPullRequestHandler.start();
            this.replyRequestHandler = new ReplyRequestHandler(repSocket, this);
            this.replyRequestHandler.start();
//            this.timeSyncHandler = new TimeSyncHandler(syncSocket, this);
//            this.timeSyncHandler.start();
            initializeMDNSWithRetries(10);
            if (additionalFeatures) {
                this.rate = new XTablesMessageRate(pullMessages, replyMessages, publishMessages);
                this.clientRegistry = new ClientRegistry(this);
                this.clientRegistry.start();
                this.socketMonitor = new XTablesSocketMonitor(context) {
                    @Override
                    protected void onClientConnected(String socketName, String clientAddress, int clientCount) {
                        if (socketMonitor != null && socketName.equals("PUBLISH")) {
                            logger.info(String.format("New client connection detected on PUBLISH socket: address=%s, connected clients=%d.", clientAddress, clientCount));
                            logger.info("Triggering client registry update due to new PUBLISH client connection.");
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ignored) {

                            }
                            clientRegistrySessionId.set(ByteString.copyFrom(Utilities.generateRandomBytes(10)));
                            clientRegistry.getClients().clear();
                            publishQueue.send(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                                    .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.REGISTRY)
                                    .setValue(clientRegistrySessionId.get())
                                    .build().toByteArray());
                        }
                    }

                    @Override
                    protected void onClientDisconnected(String socketName, String clientAddress, int clientCount) {
                        if (socketMonitor != null && socketName.equals("PUBLISH")) {
                            logger.info(String.format("Client disconnected from PUBLISH socket: address=%s, total connected clients=%d.", clientAddress, clientCount));
                            logger.info("Triggering client registry update due to PUBLISH client disconnection.");
                            clientRegistrySessionId.set(ByteString.copyFrom(Utilities.generateRandomBytes(10)));
                            clientRegistry.getClients().clear();
                            publishQueue.send(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                                    .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.REGISTRY)
                                    .setValue(clientRegistrySessionId.get())
                                    .build().toByteArray()
                            );
                        }
                    }
                };
                this.socketMonitor.addSocket("PULL", pullSocket)
                        .addSocket("REPLY", repSocket)
                        .addSocket("PUBLISH", pubSocket);
                this.socketMonitor.start();
                this.webInterface = WebInterface.initialize(this);
            } else {
                logger.warning("""
                        Additional features are disabled. The XTablesServer will proceed with standard initialization.
                        The following components are excluded:
                                            \s
                        - XTablesMessageRate: Real-time messaging rate monitoring will not be initialized.
                        - ClientRegistry: The registry for tracking connected clients will not be enabled.
                        - XTablesSocketMonitor: Advanced socket monitoring and client management will not be available.
                        - WebInterface: The web-based dashboard for monitoring and managing server activities will not be initialized.
                                            \s
                        To enable these features, use the '--additional_features=true' option when starting the server.
                        \s""");
            }
            logger.info(String.format("""
                    Debug Mode Status: %s
                    - When enabled, the system publishes detailed logs to connected clients to assist in debugging.
                    - This allows real-time insights into server operations and events.
                    """, debug.get() ? "Enabled" : "Disabled"));
            status.set(XTableStatus.ONLINE);
        } catch (Exception e) {
            e.printStackTrace();
            logger.fatal("Fatal exception in starting server. Retrying in 2 seconds!");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException exception) {
                logger.fatal("Exception while waiting on retrying server start! Shutting down.");
                System.exit(1);
            }
            start();
        } finally {
            latch.countDown();
        }

    }

    /**
     * Cleans up resources like sockets, mDNS, and handler threads.
     *
     * @throws IOException If an error occurs during cleanup
     */
    private void cleanup() throws IOException {
        status.set(XTableStatus.CLEANING);
        if (context != null) {
            logger.info("Destroying ZMQ context and releasing resources...");
            context.destroy();
            logger.info("ZMQ context destroyed successfully.");
        }

        if (jmdns != null && serviceInfo != null) {
            logger.info("Attempting to unregister mDNS service: " + serviceInfo.getQualifiedName() + " on port " + SERVICE_PORT + "...");
            jmdns.unregisterService(serviceInfo);
            jmdns.close();
            logger.info("mDNS service unregistered successfully and mDNS closed.");
        }
        if (pushPullRequestHandler != null) {
            pushPullRequestHandler.interrupt();
        }
        if (replyRequestHandler != null) {
            replyRequestHandler.interrupt();
        }
        if (clientRegistry != null) {
            clientRegistry.interrupt();
        }
        if (socketMonitor != null) {
            socketMonitor.interrupt();
        }
        if (publishQueue != null) {
            publishQueue.interrupt();
        }
        if (rate != null) {
            rate.shutdown();
        }
        table.delete("");
        pullMessages.set(0);
        replyMessages.set(0);
        publishMessages.set(0);
        status.set(XTableStatus.OFFLINE);
    }

    /**
     * Restarts the server by performing a clean shutdown followed by a restart.
     * Updates the server status appropriately and ensures proper resource management.
     */
    public void restart() {
        if(status.get().equals(XTableStatus.OFFLINE) || status.get().equals(XTableStatus.CLEANING) || status.get().equals(XTableStatus.REBOOTING))
        {
            logger.warning("Server cannot restart at current state: " + status.get().name());
            return;
        }
        try {
            status.set(XTableStatus.REBOOTING);
            logger.info("Restarting server...");

            if (Thread.currentThread() == main) {
                performRestart();
            } else {
                Thread restartThread = new Thread(() -> {
                    try {
                        performRestart();
                    } catch (Exception e) {
                        logger.fatal("Failed to restart server from main thread: " + e.getMessage());
                        throw new XTablesException("Server restart failed from main thread", e);
                    }
                });
                restartThread.setName("XTABLES-RESTART");
                restartThread.setDaemon(false);
                restartThread.start();
            }
        } catch (Exception e) {
            logger.fatal("Failed to restart server: " + e.getMessage());
            throw new XTablesException("Server restart failed", e);
        }
    }

    /**
     * Performs the actual restart logic, ensuring cleanup and resource management.
     * This method should only be called from the main thread.
     */
    private void performRestart() throws Exception {
        cleanup();
        logger.info("Starting server in 3 seconds...");
        status.set(XTableStatus.STARTING);
        Thread.sleep(3000);
        start();
        status.set(XTableStatus.ONLINE);
        logger.info("Server restarted successfully.");
    }

    /**
     * Initializes mDNS (Multicast DNS) service with a specified number of retry attempts.
     * This is used for service discovery, allowing other devices to locate the server.
     *
     * @param maxRetries Maximum number of retries for mDNS initialization
     */
    private void initializeMDNSWithRetries(int maxRetries) {
        int attempt = 0;
        long delay = 1000;

        while (attempt < maxRetries && !Thread.currentThread().isInterrupted()) {
            try {
                attempt++;
                InetAddress addr = Utilities.getLocalInetAddress();
                if (addr == null) {
                    throw new RuntimeException("No local IP address found!");
                }
                logger.info("Initializing mDNS with address: " + addr.getHostAddress());
                jmdns = JmDNS.create(addr, "XTABLES");
                Map<String, String> props = new HashMap<>();
                props.put("pullSocketPort", String.valueOf(pullPort));
                props.put("pubSocketPort", String.valueOf(pubPort));
                props.put("replySocketPort", String.valueOf(repPort));
                serviceInfo = ServiceInfo.create("_xtables._tcp.local.", SERVICE_NAME, SERVICE_PORT, 0, 0, props);
                jmdns.registerService(serviceInfo);
                logger.info("mDNS service registered: " + serviceInfo.getQualifiedName() + " on port " + SERVICE_PORT);
                return;
            } catch (Exception e) {
                logger.severe("Error initializing mDNS (attempt " + attempt + "): " + e.getMessage());
                if (attempt >= maxRetries) {
                    logger.severe("Max retries reached. Giving up on mDNS initialization.");
                    shutdown();
                    System.exit(1);
                    return;
                }
                try {
                    logger.info("Retying mDNS initialization in " + delay + " ms.");
                    TimeUnit.MILLISECONDS.sleep(delay);
                } catch (InterruptedException ie) {
                    logger.severe("Retry sleep interrupted: " + ie.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Shuts down the server and releases all associated resources, including
     * JeroMQ context, mDNS service, and handler threads.
     */
    public void shutdown() {
        try {
            status.set(XTableStatus.OFFLINE);
            if (context != null) {
                logger.info("Destroying ZMQ context and releasing resources...");
                context.destroy();
                logger.info("ZMQ context destroyed successfully.");
            }
            if (jmdns != null && serviceInfo != null) {
                logger.info("Attempting to unregister mDNS service: " + serviceInfo.getQualifiedName() + " on port " + SERVICE_PORT + "...");
                jmdns.unregisterService(serviceInfo);
                jmdns.close();
                logger.info("mDNS service unregistered successfully and mDNS closed.");
            }
            if (main != null) {
                main.interrupt();
            }
            if (pushPullRequestHandler != null) {
                pushPullRequestHandler.interrupt();
            }
            if (replyRequestHandler != null) {
                replyRequestHandler.interrupt();
            }
            if (clientRegistry != null) {
                clientRegistry.interrupt();
            }
            if (socketMonitor != null) {
                socketMonitor.interrupt();
            }
            if (publishQueue != null) {
                publishQueue.interrupt();
            }
            if (rate != null) {
                rate.shutdown();
            }
            logger.info("Shutting down the server process...");
            System.exit(0);
        } catch (Exception exception) {
            logger.fatal("An error occurred during server shutdown: " + exception.getMessage());
            logger.fatal("Forcing shutdown due to an error...");
            System.exit(1);
        }
    }

    public XTablesMessageRate getRate() {
        return rate;
    }

    /**
     * Returns the current data table associated with this instance.
     *
     * @return an instance of {@link XTablesData} representing the current table data.
     */
    public XTablesData getTable() {
        return table;
    }

    /**
     * Returns the ClientRegistry instance associated with the XTables server.
     * <p>
     * This method provides access to the ClientRegistry, which manages the list of connected clients
     * and periodically updates the server with new session IDs and client information.
     * The ClientRegistry operates as a background thread that handles client management tasks.
     *
     * @return the ClientRegistry instance managing client connections and session updates
     */
    public ClientRegistry getClientRegistry() {
        return clientRegistry;
    }

    public ByteString getClientRegistrySessionId() {
        return clientRegistrySessionId.get();
    }

    public AtomicReference<ByteString> getClientRegistrySession() {
        return clientRegistrySessionId;
    }



    /**
     * Retrieves the iteration speed.
     * <p>
     * This method returns the number of iterations per second that the server can process,
     * representing its current processing capability.
     *
     * @return the iteration speed as an integer
     */
    public int getIterationSpeed() {
        return this.iterationSpeed;
    }

    /**
     * Retrieves the version of the server.
     * <p>
     * This method provides the version string for the current instance, which can
     * be useful for diagnostics or display purposes.
     *
     * @return the version as a string
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * Retrieves the current status of the server.
     * <p>
     * This method accesses the current status stored in an atomic reference,
     * providing a thread-safe way to get the server's state.
     *
     * @return the current status as an {@link XTableStatus} object
     */
    public XTableStatus getStatus() {
        return status.get();
    }

    /**
     * Checks if debug mode is enabled.
     *
     * @return boolean - true if debug mode is enabled, false otherwise.
     * This method retrieves the current value of the debug mode flag.
     */
    public boolean isDebug() {
        return debug.get();
    }

    /**
     * Sets the debug mode state.
     *
     * @param value boolean - the value to set for debug mode.
     *              This method sets the debug mode flag to the provided value.
     *              When enabled, the system starts publishing logs to the clients for debugging purposes.
     */
    public void setDebug(boolean value) {
        if (debug.get() != value) {
            debug.set(value);
            XTablesLogger.setHandler((level, s) -> {
                if (debug.get() && pubSocket != null && status.get().equals(XTableStatus.ONLINE)) {
                    publishQueue.send(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                            .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.LOG)
                            .setValue(XTableProto.XTableMessage.XTableLog.newBuilder()
                                    .setLevel(level.equals(Level.INFO) ? XTableProto.XTableMessage.XTableLog.Level.INFO : level.equals(Level.WARNING) ? XTableProto.XTableMessage.XTableLog.Level.WARNING : level.equals(Level.SEVERE) ? XTableProto.XTableMessage.XTableLog.Level.SEVERE : level.equals(XTablesLogger.FATAL) ? XTableProto.XTableMessage.XTableLog.Level.FATAL : XTableProto.XTableMessage.XTableLog.Level.UNKNOWN)
                                    .setMessage(s)
                                    .build()
                                    .toByteString())
                            .build().toByteArray());
                }
            });
            logger.info("Debug mode is now " + (debug.get() ? "enabled. Logs will be published to connected clients."
                    : "disabled. Logs will not be sent to clients."));
        }
    }
}
