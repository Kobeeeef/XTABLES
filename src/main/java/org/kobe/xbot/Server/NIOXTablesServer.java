/**
 * XTablesServer class manages a server that allows clients to interact with a key-value data structure.
 * It provides methods to start the server, handle client connections, and process client requests.
 * The class also supports server reboot functionality and notification of clients upon data updates.
 *
 * <p>
 *
 * @author Kobe
 */

package org.kobe.xbot.Server;

import com.google.gson.Gson;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.kobe.xbot.Utilities.*;
import org.kobe.xbot.Utilities.Entities.ScriptParameters;
import org.kobe.xbot.Utilities.Exceptions.ScriptAlreadyExistsException;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.kobe.xbot.Server.MainNIO.XTABLES_SERVER_VERSION;
import static org.kobe.xbot.Utilities.Utilities.tokenize;

public class NIOXTablesServer {
    private static final Logger log = LoggerFactory.getLogger(NIOXTablesServer.class);
    private final String SERVICE_NAME;
    private static final int SERVICE_PORT = 5353;
    private static final AtomicReference<NIOXTablesServer> instance = new AtomicReference<>();
    private static final Gson gson = new Gson();
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private final Set<ClientHandler> clients = new HashSet<>();
    private final XTablesData table = new XTablesData();
    private AsynchronousServerSocketChannel serverSocketChannel;
    private final int port;
    private final int zeroMQPullPort;
    private final int zeroMQPubPort;
    private final ExecutorService clientThreadPool;
    private final HashMap<String, Function<ScriptParameters, String>> scripts = new HashMap<>();
    private static Thread mainThread;
    private static final CountDownLatch latch = new CountDownLatch(1);
    private ExecutorService mdnsExecutorService;
//    private ExecutorService zeroMQExecutorService;
//    private ZContext context;
    private Server userInterfaceServer;
    private static final AtomicReference<XTableStatus> status = new AtomicReference<>(XTableStatus.OFFLINE);
    private int framesReceived;

    private NIOXTablesServer(String SERVICE_NAME, int PORT, int zeroMQPullPort, int zeroMQPubPort) {
        this.port = PORT;
        this.zeroMQPullPort = zeroMQPullPort;
        this.zeroMQPubPort = zeroMQPubPort;
        this.SERVICE_NAME = SERVICE_NAME;
        instance.set(this);
        this.clientThreadPool = Executors.newCachedThreadPool();
        startServer();
    }

    public static NIOXTablesServer startInstance(String SERVICE_NAME, int PORT, int zeroMQPullPort, int zeroMQPubPort) {
        if (instance.get() == null) {
            if (PORT == 5353)
                throw new IllegalArgumentException("The port 5353 is reserved for mDNS services.");
            if (SERVICE_NAME.equalsIgnoreCase("localhost"))
                throw new IllegalArgumentException("The mDNS service name cannot be localhost!");
            status.set(XTableStatus.STARTING);
            Thread main = new Thread(() -> new NIOXTablesServer(SERVICE_NAME, PORT, zeroMQPullPort, zeroMQPubPort));
            main.setName("XTABLES-SERVER");
            main.setDaemon(false);
            main.start();
            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.severe("Main thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
            mainThread = main;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered. Stopping server...");
                if (instance.get() != null) {
                    instance.get().stopServer(true);
                    logger.info("Server stopped gracefully.");
                }
            }));

        }
        return instance.get();
    }

    public static void stopInstance() {
        NIOXTablesServer xTablesServer = instance.get();
        xTablesServer.stopServer(false);
        mainThread.interrupt();
        instance.set(null);
    }

    public static NIOXTablesServer getInstance() {
        return instance.get();
    }

    public static XTableStatus getStatus() {
        return status.get();
    }

    public void addScript(String name, Function<ScriptParameters, String> script) {
        if (scripts.containsKey(name))
            throw new ScriptAlreadyExistsException("There is already a script with the name: '" + name + "'");
        scripts.put(name, script);
        logger.info("Added script '" + name + "'");
    }

    public void removeScript(String name) {
        scripts.remove(name);
    }

    private void startServer() {
        try {
            status.set(XTableStatus.STARTING);

            if (mdnsExecutorService != null) {
                mdnsExecutorService.shutdownNow();
            }

            this.mdnsExecutorService = Executors.newFixedThreadPool(1);
            mdnsExecutorService.execute(() -> initializeMDNSWithRetries(15));

            serverSocketChannel = AsynchronousServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port));
            logger.info("Server started. Listening on port " + port);

            if (userInterfaceServer == null || !userInterfaceServer.isRunning()) {
                try {
                    userInterfaceServer = new Server(4880);
                    // Static resource handler
                    ResourceHandler resourceHandler = new ResourceHandler();
                    resourceHandler.setDirectoriesListed(true);
                    URL resourceURL = NIOXTablesServer.class.getResource("/static");
                    assert resourceURL != null;
                    String resourceBase = resourceURL.toExternalForm();
                    resourceHandler.setResourceBase(resourceBase);

                    ContextHandler staticContext = new ContextHandler("/");
                    staticContext.setHandler(resourceHandler);

                    ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
                    servletContextHandler.setContextPath("/");

                    // Add servlets (GET, POST, etc.)
                    addServlets(servletContextHandler);

                    FilterHolder cors = servletContextHandler.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
                    cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
                    cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
                    cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
                    cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");

                    // Combine handlers
                    HandlerList handlers = new HandlerList();
                    handlers.addHandler(staticContext);
                    handlers.addHandler(servletContextHandler);

                    userInterfaceServer.setHandler(handlers);
                    userInterfaceServer.start();
                    logger.info("The local XTABLES user interface started at http://localhost:4880!");
                } catch (Exception e) {
                    logger.warning("The local XTABLES user interface failed to start!");
                }
            }

            latch.countDown();
            status.set(XTableStatus.ONLINE);

            // Accept connections asynchronously
            serverSocketChannel.accept(null, new java.nio.channels.CompletionHandler<>() {
                @Override
                public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
                    try {
                        logger.info(String.format("Client connected: %1$s", clientChannel.getRemoteAddress()));
                        ClientHandler clientHandler = new ClientHandler(clientChannel);
                        clients.add(clientHandler);
                        clientHandler.start();
                    } catch (IOException e) {
                        logger.severe("Error handling client connection: " + e.getMessage());
                    }

                    // Accept the next connection
                    serverSocketChannel.accept(null, this);
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    logger.severe("Failed to accept connection: " + exc.getMessage());
                }
            });

        } catch (IOException e) {
            logger.severe("Socket error occurred: " + e.getMessage());
            status.set(XTableStatus.OFFLINE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void stopServer(boolean fullShutdown) {
        try {


            status.set(XTableStatus.OFFLINE);
            logger.info("Closing socket server...");
            if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                serverSocketChannel.close();
            }
            logger.info("Closing connections to all clients...");
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client != null) client.closeClient();
                }
            }
            clients.clear();

            table.delete("");
            mdnsExecutorService.shutdownNow();
            if (jmdns != null && serviceInfo != null) {
                logger.info("Unregistering mDNS service: " + serviceInfo.getQualifiedName() + " on port " + SERVICE_PORT + "...");
                jmdns.unregisterService(serviceInfo);
                jmdns.close();
                logger.info("mDNS service unregistered and mDNS closed");
            }
            if (fullShutdown) {
                mainThread.interrupt();
                if (userInterfaceServer != null) userInterfaceServer.stop();
                clientThreadPool.shutdownNow();
            }
        } catch (IOException e) {
            logger.severe("Error occurred during server stop: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeMDNSWithRetries(int maxRetries) {
        int attempt = 0;
        long delay = 1000; // 1 second mDNS setup retry

        while (attempt < maxRetries && !Thread.currentThread().isInterrupted()) {
            try {
                attempt++;
                InetAddress addr = Utilities.getLocalInetAddress();
                if (addr == null) {
                    throw new RuntimeException("No local IP address found!");
                }
                logger.info("Initializing mDNS with address: " + addr.getHostAddress());
                jmdns = JmDNS.create(addr, "XTABLES");
                // Create the service with additional attributes
                Map<String, String> props = new HashMap<>();
                props.put("port", String.valueOf(port));
                props.put("pull-port", String.valueOf(zeroMQPullPort));
                props.put("pub-port", String.valueOf(zeroMQPubPort));
                serviceInfo = ServiceInfo.create("_xtables._tcp.local.", SERVICE_NAME, SERVICE_PORT, 0, 0, props);
                jmdns.registerService(serviceInfo);
                logger.info("mDNS service registered: " + serviceInfo.getQualifiedName() + " on port " + SERVICE_PORT);
                return;
            } catch (Exception e) {
                logger.severe("Error initializing mDNS (attempt " + attempt + "): " + e.getMessage());
                if (attempt >= maxRetries) {
                    logger.severe("Max retries reached. Giving up on mDNS initialization.");
                    stopInstance();
                    System.exit(0);
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

    public boolean rebootServer() {
        if (!getStatus().equals(XTableStatus.ONLINE)) {
            logger.warning("Cannot reboot server when status is: " + getStatus());
            return false;
        }
        try {
            stopServer(false);
            framesReceived = 0;
            status.set(XTableStatus.REBOOTING);
            logger.info("Starting socket server in 1 second...");
            TimeUnit.MILLISECONDS.sleep(1500);
            logger.info("Starting server...");
            status.set(XTableStatus.STARTING);
            Thread thread = new Thread(this::startServer);
            thread.setDaemon(false);
            thread.start();
            return true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void notifyUpdateChangeClients(String key, String value) {
        List<ClientHandler> snapshot;
        synchronized (clients) {
            snapshot = new ArrayList<>(clients);
        }

        for (ClientHandler client : snapshot) {

                Set<String> updateEvents = client.getUpdateEvents();
                if (updateEvents.contains("") || updateEvents.contains(key)) {
                    client.sendUpdate(key, value);
                }

        }
    }



    private void notifyDeleteChangeClients(String key) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getDeleteEvent()) {
                    client.sendDelete(key);
                }
            }
        }
    }

    // Thread to handle each client connection
    private class ClientHandler {
        private final AsynchronousSocketChannel clientChannel;
        private final Set<String> updateEvents = new HashSet<>();
        private final AtomicBoolean deleteEvent = new AtomicBoolean(false);
        private int totalMessages = 0;
        private final String identifier;
        private List<String> streams;
        private ClientStatistics statistics;

        public ClientHandler(AsynchronousSocketChannel clientChannel) {
            this.clientChannel = clientChannel;
            this.identifier = UUID.randomUUID().toString();

        }
        public String getAddress() {
            try {
                return clientChannel.getRemoteAddress().toString();
            } catch (IOException e) {
                return null;
            }
        }
        public String getHostname() {
            try {
                return  ((InetSocketAddress) clientChannel.getRemoteAddress()).getHostName();
            } catch (IOException e) {
                return null;
            }
        }
        public Set<String> getUpdateEvents() {
            return updateEvents;
        }

        public Boolean getDeleteEvent() {
            return deleteEvent.get();
        }

        public void start() {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            ScheduledExecutorService messagesLogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            });

            messagesLogExecutor.scheduleAtFixedRate(() -> {
                if (totalMessages > 0) {
                    try {
                        logger.info("Received " + String.format("%,d", totalMessages) + " messages from " + clientChannel.getRemoteAddress());
                    } catch (IOException e) {
                        logger.warning("Could not get client address: " + e.getMessage());
                    }
                    totalMessages = 0;
                }
            }, 60, 60, TimeUnit.SECONDS);

            readFromClient(buffer);
            pingServerForInformation();
        }

        private final StringBuilder messageBuffer = new StringBuilder();

        private void readFromClient(ByteBuffer buffer) {
            clientChannel.read(buffer, buffer, new java.nio.channels.CompletionHandler<>() {
                @Override
                public void completed(Integer bytesRead, ByteBuffer attachment) {
                    if (bytesRead == -1) {
                        logger.severe("Empty byte from client, closing: " + getAddress());
                        closeClient();
                        return;
                    }

                    buffer.flip();
                    String chunk = new String(buffer.array(), 0, buffer.limit());
                    buffer.clear();

                    // Append the received chunk to the message buffer
                    messageBuffer.append(chunk);

                    // Process complete messages
                    int delimiterIndex;
                    while ((delimiterIndex = messageBuffer.indexOf("\n")) != -1) {
                        // Extract the complete message up to the delimiter
                        String message = messageBuffer.substring(0, delimiterIndex).trim();
                        messageBuffer.delete(0, delimiterIndex + 1);

                        totalMessages++;
                        processMessage(message); // Process the complete message
                    }

                    // Continue reading from the client
                    readFromClient(buffer);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    logger.severe("Failed to read from client: " + exc.getMessage());
                    closeClient();
                }
            });
        }


        private void processMessage(String raw) {
            String[] tokens = tokenize(raw, ' ', 3);
            String[] requestTokens = tokenize(tokens[0], ':', 2);
            String id = requestTokens[0];
            String methodType = requestTokens[1];
            boolean shouldReply = !id.equals("IGNORED");

            switch (methodType) {
                case "PUT" -> {
                    if (tokens.length >= 3) {
                        String key = tokens[1];
                        StringBuilder valueBuilder = new StringBuilder();
                        for (int i = 2; i < tokens.length; i++) {
                            valueBuilder.append(tokens[i]);
                            if (i < tokens.length - 1) valueBuilder.append(" ");
                        }
                        String value = valueBuilder.toString();

                        boolean response = table.put(key, value);

                        if (shouldReply) {
                            writeToClient(id + ":" + methodType + " " + (response ? "OK" : "FAIL"));
                        }
                        if (response) {
                            notifyUpdateChangeClients(key, value);
                        }
                    }
                }
                case "GET" -> {
                    if (tokens.length == 2 && shouldReply) {
                        String key = tokens[1];
                        String result = table.get(key);
                        writeToClient(id + ":" + methodType + " " + (result != null ? result.replace("\n", "") : "null"));
                    }
                }
                case "SUBSCRIBE_UPDATE" -> {
                    if (tokens.length == 2) {
                        String key = tokens[1];
                        updateEvents.add(key);
                        if (shouldReply) {
                            writeToClient(id + ":" + methodType + " OK");
                        }
                    } else if (tokens.length == 1) {
                        updateEvents.add("");
                        if (shouldReply) {
                            writeToClient(id + ":" + methodType + " OK");
                        }
                    }
                }
                case "GET_TABLES" -> {
                    if (tokens.length == 1 && shouldReply) {
                        String result = gson.toJson(table.getTables(""));
                        ResponseInfo responseInfo = new ResponseInfo(id, methodType, result);
                        writeToClient(responseInfo.parsed());
                    } else if (tokens.length == 2 && shouldReply) {
                        String key = tokens[1];
                        String result = gson.toJson(table.getTables(key));
                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.GET_TABLES, result);
                        writeToClient(responseInfo.parsed());
                    }
                }
                case "RUN_SCRIPT" -> {
                    if (tokens.length >= 2) {
                        String name = tokens[1];
                        String customData = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length));
                        if (scripts.containsKey(name)) {
                            clientThreadPool.execute(() -> {
                                long startTime = System.nanoTime();
                                String returnValue;
                                ResponseStatus status = ResponseStatus.OK;
                                try {
                                    returnValue = scripts.get(name).apply(new ScriptParameters(table, customData == null || customData.trim().isEmpty() ? null : customData.trim()));
                                    long endTime = System.nanoTime();
                                    double durationMillis = (endTime - startTime) / 1e6;
                                    logger.info("The script '" + name + "' ran successfully.");
                                    logger.info("Start time: " + startTime + " ns");
                                    logger.info("End time: " + endTime + " ns");
                                    logger.info("Duration: " + durationMillis + " ms");
                                } catch (Exception e) {
                                    long endTime = System.nanoTime();
                                    double durationMillis = (endTime - startTime) / 1e6;
                                    returnValue = e.getMessage();
                                    status = ResponseStatus.FAIL;
                                    logger.severe("The script '" + name + "' encountered an error.");
                                    logger.severe("Error message: " + e.getMessage());
                                    logger.severe("Start time: " + startTime + " ns");
                                    logger.severe("End time: " + endTime + " ns");
                                    logger.severe("Duration: " + durationMillis + " ms");
                                }
                                String response = returnValue == null || returnValue.trim().isEmpty() ? status.name() : status.name() + " " + returnValue.trim();
                                if (shouldReply) {
                                    ResponseInfo responseInfo = new ResponseInfo(id, methodType, response);
                                    writeToClient(responseInfo.parsed());
                                }
                            });
                        } else if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, methodType, "FAIL SCRIPT_NOT_FOUND");
                            writeToClient(responseInfo.parsed());
                        }
                    }
                }
                case "UPDATE_KEY" -> {
                    if (tokens.length == 3) {
                        String key = tokens[1];
                        String value = tokens[2];
                        if (!Utilities.validateName(value, false)) {
                            if (shouldReply) {
                                ResponseInfo responseInfo = new ResponseInfo(id, methodType, "FAIL");
                                writeToClient(responseInfo.parsed());
                            }
                        } else {
                            boolean response = table.renameKey(key, value);
                            if (shouldReply) {
                                ResponseInfo responseInfo = new ResponseInfo(id, methodType, response ? "OK" : "FAIL");
                                writeToClient(responseInfo.parsed());
                            }
                        }
                    }
                }
                case "DELETE" -> {
                    if (tokens.length == 2) {
                        String key = tokens[1];
                        boolean response = table.delete(key);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, methodType, response ? "OK" : "FAIL");
                            writeToClient(responseInfo.parsed());
                        }
                        if (response) {
                            notifyDeleteChangeClients(key);
                        }
                    } else if (tokens.length == 1) {
                        boolean response = table.delete("");
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, methodType, response ? "OK" : "FAIL");
                            writeToClient(responseInfo.parsed());
                        }
                    }
                }
                case "SUBSCRIBE_DELETE" -> {
                    if (tokens.length == 1) {
                        deleteEvent.set(true);
                        ResponseInfo responseInfo = new ResponseInfo(id, methodType, "OK");
                        writeToClient(responseInfo.parsed());
                    }
                }
                case "UNSUBSCRIBE_DELETE" -> {
                    if (tokens.length == 1) {
                        deleteEvent.set(false);
                        ResponseInfo responseInfo = new ResponseInfo(id, methodType, "OK");
                        writeToClient(responseInfo.parsed());
                    }
                }
                case "UNSUBSCRIBE_UPDATE" -> {
                    if (tokens.length == 2) {
                        String key = tokens[1];
                        boolean success = updateEvents.remove(key);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, methodType, success ? "OK" : "FAIL");
                            writeToClient(responseInfo.parsed());
                        }
                    } else if (tokens.length == 1) {
                        boolean success = updateEvents.remove("");
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, methodType, success ? "OK" : "FAIL");
                            writeToClient(responseInfo.parsed());
                        }
                    }
                }
                case "PING" -> {
                    if (tokens.length == 1 && shouldReply) {
                        SystemStatistics systemStatistics = new SystemStatistics(clients.size());
                        int i = 0;
                        for (ClientHandler client : clients) {
                            i += client.totalMessages;
                        }
                        systemStatistics.setTotalMessages(i);
                        systemStatistics.setVersion(XTABLES_SERVER_VERSION);
                        ResponseInfo responseInfo = new ResponseInfo(id, methodType, ResponseStatus.OK.name() + " " + gson.toJson(systemStatistics).replaceAll(" ", ""));
                        writeToClient(responseInfo.parsed());
                    }
                }
                case "GET_RAW_JSON" -> {
                    if (tokens.length == 1 && shouldReply) {
                        ResponseInfo responseInfo = new ResponseInfo(id, methodType, DataCompression.compressAndConvertBase64(table.toJSON()));
                        writeToClient(responseInfo.parsed());
                    }
                }
                case "REBOOT_SERVER" -> {
                    if (tokens.length == 1) {
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, methodType, ResponseStatus.OK.name());
                            writeToClient(responseInfo.parsed());
                        }
                        rebootServer();
                    }
                }
                case "INFORMATION" -> {
                    if (tokens.length >= 2) {
                        try {
                            this.statistics = gson.fromJson(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)), ClientStatistics.class);
                        } catch (Exception e) {
                            logger.info("Could not parse client information: " + e.getMessage());
                        }
                    }
                }
                default -> {
                    if (shouldReply) {
                        writeToClient(id + ":UNKNOWN FAIL");
                    }
                }
            }
        }

        private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean isWriting = new AtomicBoolean(false);

        private void writeToClient(String message) {
            messageQueue.offer(message);
            processQueue();
        }

        private void processQueue() {
            if (isWriting.compareAndSet(false, true)) {
                String message = messageQueue.poll();
                if (message != null) {
                    ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes());
                    clientChannel.write(buffer, null, new java.nio.channels.CompletionHandler<>() {
                        @Override
                        public void completed(Integer result, Object attachment) {
                            isWriting.set(false);
                            processQueue(); // Process the next message in the queue
                        }

                        @Override
                        public void failed(Throwable exc, Object attachment) {
                            isWriting.set(false);
                            logger.warning("Failed to write to client: " + exc.getMessage());
                            closeClient();
                        }
                    });
                } else {
                    isWriting.set(false); // No messages to write
                }
            }
        }


        public void closeClient() {
            try {
                logger.info("Closing connection with client: " + clientChannel.getRemoteAddress());
                clientChannel.close();
            } catch (IOException e) {
                logger.severe("Error closing client connection: " + e.getMessage());
            } finally {
                clients.remove(this);
            }
        }
        public void pingServerForInformation() {
            writeToClient("null:INFORMATION");
        }

        public ClientStatistics pingServerForInformationAndWait(long timeout) {
            long startTime = System.currentTimeMillis();
            pingServerForInformation();
            if (this.statistics == null) {
                while (System.currentTimeMillis() - startTime < timeout) {
                    if (this.statistics != null) {
                        return this.statistics;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (this.statistics == null) {
                    logger.info("Timeout reached. Server did not respond with information.");
                    return null;
                }
            }
            return this.statistics;
        }
        public void sendUpdate(String key, String value) {
            writeToClient("null:UPDATE_EVENT " + key + " " + value);
        }

        public void sendDelete(String key) {
            writeToClient("null:DELETE_EVENT " + key);
        }
    }









    private void addServlets(ServletContextHandler servletContextHandler) {
        // Add a servlet to handle GET requests
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                SystemStatistics systemStatistics = new SystemStatistics(clients.size());
                systemStatistics.setStatus(NIOXTablesServer.getStatus());
                synchronized (clients) {
                    systemStatistics.setClientDataList(clients.stream().map(m -> {
                        ClientData data = new ClientData(m.getAddress(),
                                m.getHostname(),
                                m.totalMessages,
                                m.identifier);
                        if (m.statistics != null) data.setStats(gson.toJson(m.statistics));
                        return data;
                    }).collect(Collectors.toList()));
                    int i = 0;
                    for (ClientHandler client : clients) {
                        i += client.totalMessages;
                    }
                    systemStatistics.setTotalMessages(i);
                    systemStatistics.setVersion(XTABLES_SERVER_VERSION);
                    systemStatistics.setFramesForwarded(framesReceived);
                    try {
                        systemStatistics.setHostname(InetAddress.getLocalHost().getHostName());
                    } catch (Exception ignored) {
                    }
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

                synchronized (clients) {
                    Optional<ClientHandler> clientHandler = clients.stream().filter(f -> f.identifier.equals(uuid.toString())).findFirst();
                    if (clientHandler.isPresent()) {
                        ClientStatistics statistics = clientHandler.get().pingServerForInformationAndWait(3000);
                        if (statistics != null) {
                            resp.setStatus(HttpServletResponse.SC_OK);
                            resp.getWriter().println(String.format("{ \"status\": \"success\", \"message\": %1$s}", gson.toJson(statistics)));
                        } else {
                            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"The client did not respond!\"}");
                        }
                    } else {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"This client does not exist!\"}");
                    }
                }


            }
        }), "/api/ping");
        // Add a servlet to handle server reboot POST requests
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                boolean response = rebootServer();
                if (response) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().println("{ \"status\": \"success\", \"message\": \"Server has been rebooted!\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_CONFLICT);
                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"Server cannot reboot while: " + getStatus() + "\"}");
                }
            }
        }), "/api/reboot");

        // Add more servlets as needed, e.g., ping or disconnect
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

                synchronized (clients) {
                    Optional<ClientHandler> clientHandler = clients.stream().filter(f -> f.identifier.equals(uuid.toString())).findFirst();
                    if (clientHandler.isPresent()) {
                        clientHandler.get().closeClient();
                        resp.setStatus(HttpServletResponse.SC_OK);
                        resp.getWriter().println("{ \"status\": \"success\", \"message\": \"The client has been disconnected!\"}");
                    } else {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"This client does not exist!\"}");
                    }
                }
            }
        }), "/api/disconnect");
    }

}
