package org.kobe.xbot.JServer;

import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Exceptions.XTablesException;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.Utilities;
import org.kobe.xbot.Utilities.XTableStatus;
import org.kobe.xbot.Utilities.XTablesData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
    public static final XTablesData table = new XTablesData();
    private static final Logger log = LoggerFactory.getLogger(XTablesServer.class);
    private static Thread main;
    // =============================================================
    // Instance Variables
    // These variables are unique to each instance of the class.
    // =============================================================
    public final AtomicInteger messages = new AtomicInteger(0);
    private ZContext context;
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private PushPullRequestHandler pushPullRequestHandler;
    private ReplyRequestHandler replyRequestHandler;
    public ZMQ.Socket pubSocket;
    private final int pullPort;
    private final int repPort;
    private final int pubPort;
    private final AtomicBoolean debug = new AtomicBoolean(false);

    /**
     * Constructor for initializing the server with specified ports.
     *
     * @param pullServerPort Port for the PULL socket
     * @param repServerPort  Port for the REP socket
     * @param pubServerPort  Port for the PUB socket
     */
    private XTablesServer(int pullServerPort, int repServerPort, int pubServerPort) {
        this.pullPort = pullServerPort;
        this.repPort = repServerPort;
        this.pubPort = pubServerPort;
        instance.set(this);
        start();
    }

    /**
     * Starts the server by initializing sockets, threads, and mDNS.
     */
    private void start() {
        try {
            this.cleanup();
        } catch (Exception exception) {
            logger.fatal("There was an error cleaning up the server: " + exception.getMessage());
            System.exit(1);
        }
        Utilities.warmupProtobuf();
        this.context = new ZContext();
        pubSocket = context.createSocket(SocketType.PUB);
        pubSocket.setHWM(500);
        pubSocket.bind("tcp://localhost:" + pubPort);
        ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
        pullSocket.setHWM(500);
        pullSocket.bind("tcp://localhost:" + pullPort);
        ZMQ.Socket repSocket = context.createSocket(SocketType.REP);
        repSocket.setHWM(500);
        repSocket.bind("tcp://localhost:" + repPort);

        this.pushPullRequestHandler = new PushPullRequestHandler(pullSocket, this);
        this.pushPullRequestHandler.start();
        this.replyRequestHandler = new ReplyRequestHandler(repSocket, this);
        this.replyRequestHandler.start();

        initializeMDNSWithRetries(10);
        status.set(XTableStatus.ONLINE);
        latch.countDown();
    }

    /**
     * Cleans up resources like sockets, mDNS, and handler threads.
     *
     * @throws IOException If an error occurs during cleanup
     */
    private void cleanup() throws IOException {
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
        table.delete("");

    }

    /**
     * Restarts the server by performing a clean shutdown followed by a restart.
     * Updates the server status appropriately and ensures proper resource management.
     */
    public void restart() {
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
        status.set(XTableStatus.OFFLINE);
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
     * Initializes the XTablesServer instance and starts the main server thread.
     * Ensures the server is properly cleaned up during JVM shutdown.
     *
     * @param pullSocketPort    Port for the PULL socket
     * @param replySocketPort   Port for the REP socket
     * @param publishSocketPort Port for the PUB socket
     * @return The initialized server instance
     */
    public static XTablesServer initialize(int pullSocketPort, int replySocketPort, int publishSocketPort) {
        if (instance.get() != null) {
            return instance.get();
        }
        status.set(XTableStatus.STARTING);
        main = new Thread(() -> new XTablesServer(pullSocketPort, replySocketPort, publishSocketPort));
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

        return instance.get();
    }

    /**
     * Shuts down the server and releases all associated resources, including
     * JeroMQ context, mDNS service, and handler threads.
     */
    public void shutdown() {
        try {
            if (context != null) {
                logger.info("Destroying ZMQ context and releasing resources...");
                context.destroy();
                logger.info("ZMQ context destroyed successfully.");
            }
            status.set(XTableStatus.OFFLINE);
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
            logger.info("Shutting down the server process...");
            System.exit(0);
        } catch (Exception exception) {
            logger.fatal("An error occurred during server shutdown: " + exception.getMessage());
            logger.fatal("Forcing shutdown due to an error...");
            System.exit(1);
        }
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
     * Notifies connected clients of updates.
     *
     * @param update The update message to send
     */
    public void notifyUpdateClients(XTableProto.XTableMessage.XTableUpdate update) {
        pubSocket.send(update.toByteArray(), ZMQ.DONTWAIT);
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
        debug.set(value);
        XTablesLogger.setHandler((level, s) -> {
            if (debug.get() && pubSocket != null) {
                notifyUpdateClients(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                        .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.LOG)
                        .setValue(XTableProto.XTableMessage.XTableLog.newBuilder()
                                .setLevel(level.equals(Level.INFO) ? XTableProto.XTableMessage.XTableLog.Level.INFO : level.equals(Level.WARNING) ? XTableProto.XTableMessage.XTableLog.Level.WARNING : level.equals(Level.SEVERE) ? XTableProto.XTableMessage.XTableLog.Level.SEVERE : level.equals(XTablesLogger.FATAL) ? XTableProto.XTableMessage.XTableLog.Level.FATAL : XTableProto.XTableMessage.XTableLog.Level.UNKNOWN)
                                .setMessage(s)
                                .build()
                                .toByteString())
                        .build());
            }
        });
    }

}
