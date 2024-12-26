package org.kobe.xbot.JServer;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.kobe.xbot.Utilities.*;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Exceptions.XTablesException;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
    private static final Logger log = LoggerFactory.getLogger(XTablesServer.class);
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
    public ZMQ.Socket pubSocket;
    private ZContext context;
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private PushPullRequestHandler pushPullRequestHandler;
    private ReplyRequestHandler replyRequestHandler;
    private ClientRegistry clientRegistry;
    private int iterationSpeed;

    /**
     * Constructor for initializing the server with specified ports.
     *
     * @param pullServerPort Port for the PULL socket
     * @param repServerPort  Port for the REP socket
     * @param pubServerPort  Port for the PUB socket
     */
    private XTablesServer(String version, int pullServerPort, int repServerPort, int pubServerPort) {
        this.pullPort = pullServerPort;
        this.repPort = repServerPort;
        this.pubPort = pubServerPort;
        this.version = version;
        instance.set(this);
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
    public static XTablesServer initialize(String version, int pullSocketPort, int replySocketPort, int publishSocketPort) {
        if (instance.get() != null) {
            return instance.get();
        }
        status.set(XTableStatus.STARTING);
        main = new Thread(() -> new XTablesServer(version, pullSocketPort, replySocketPort, publishSocketPort));
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
        instance.get().iterationSpeed = Utilities.measureWhileLoopIterationsPerSecond();
        String formattedSpeed = NumberFormat.getInstance().format(instance.get().iterationSpeed);
        logger.info("Server can process approximately " + formattedSpeed + " iterations per second.");
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
//        this.xTablesSocketMonitor = new XTablesSocketMonitor(context);
//        this.xTablesSocketMonitor.addSocket("PULL", pullSocket)
//                .addSocket("REPLY", repSocket)
//                .addSocket("PUBLISH", pubSocket);
//        this.xTablesSocketMonitor.start();
        this.clientRegistry = new ClientRegistry(this);
        this.clientRegistry.start();
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
        if (clientRegistry != null) {
            clientRegistry.interrupt();
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
            if (clientRegistry != null) {
                clientRegistry.interrupt();
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

    /**
     * Notifies connected clients of updates.
     *
     * @param update The update message to send
     */
    public void notifyUpdateClients(XTableProto.XTableMessage.XTableUpdate update) {
        pubSocket.send(update.toByteArray(), ZMQ.DONTWAIT);
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

    private void addServlets(ServletContextHandler servletContextHandler) {
        // Add a servlet to handle GET requests

        XTablesServer server = this;
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                SystemStatistics systemStatistics = new SystemStatistics(server);
                systemStatistics.setClientDataList(clientRegistry.getClients().stream().map(m -> {
                    ClientData data = new ClientData(m.getIp(),
                            m.getHostname(),
                            m.getUUID());
                    data.setStats(gson.toJson(m));
                    return data;
                }).collect(Collectors.toList()));
                try {
                    systemStatistics.setHostname(InetAddress.getLocalHost().getHostName());
                } catch (Exception ignored) {
                }

                resp.getWriter().println(gson.toJson(systemStatistics));
            }
        }), "/api/get");
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String uuidParam = req.getParameter("uuid");

                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                if (uuidParam == null || uuidParam.isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"No UUID found in parameter!\"}");
                    return;
                }
                UUID uuid;

                try {
                    uuid = UUID.fromString(uuidParam);
                } catch (IllegalArgumentException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"Invalid UUID format!\"}");
                    return;
                }


                Optional<ClientStatistics> clientHandler = instance.get().getClientRegistry().getClients().stream().filter(f -> f.getUUID().equals(uuid.toString())).findFirst();
                if (clientHandler.isPresent()) {
//                        ClientStatistics statistics = clientHandler.get().pingServerForInformationAndWait(3000);
//                        if (statistics != null) {
//                            resp.setStatus(HttpServletResponse.SC_OK);
//                            resp.getWriter().println(String.format("{ \"status\": \"success\", \"message\": %1$s}", gson.toJson(statistics)));
//                        } else {
//                            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
//                            resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"The client did not respond!\"}");
//                        }
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"This client does not exist!\"}");
                }


            }
        }), "/api/ping");
        // Add a servlet to handle server reboot POST requests
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                try {
                    restart();
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().println("{ \"status\": \"success\", \"message\": \"Server has been rebooted!\"}");
                } catch (Exception e) {
                    resp.setStatus(HttpServletResponse.SC_CONFLICT);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"Server cannot reboot while: " + status.get().name() + "\"}");
                }

            }
        }), "/api/reboot");
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                String data = table.toJSON();
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().println(data);

            }
        }), "/api/data");
    }
}
