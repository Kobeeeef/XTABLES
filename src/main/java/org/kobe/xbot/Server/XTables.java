/**
 * XTables class manages a server that allows clients to interact with a key-value data structure.
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
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class XTables {
    private static final Logger log = LoggerFactory.getLogger(XTables.class);
    private final String SERVICE_NAME;
    private static final int SERVICE_PORT = 5353;
    private static final AtomicReference<XTables> instance = new AtomicReference<>();
    private static final Gson gson = new Gson();
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private final Set<ClientHandler> clients = new HashSet<>();
    private final XTablesData<String> table = new XTablesData<>();
    private ServerSocket serverSocket;
    private final int port;
    private final ExecutorService clientThreadPool;
    private final HashMap<String, Function<ScriptParameters, String>> scripts = new HashMap<>();
    private static Thread mainThread;
    private static final CountDownLatch latch = new CountDownLatch(1);
    private ExecutorService mdnsExecutorService;
    private Server userInterfaceServer;

    public static XTables startInstance(String SERVICE_NAME, int PORT) {
        if (instance.get() == null) {
            if (PORT == 5353)
                throw new IllegalArgumentException("The port 5353 is reserved for mDNS services.");
            if (SERVICE_NAME.equalsIgnoreCase("localhost"))
                throw new IllegalArgumentException("The mDNS service name cannot be localhost!");
            Thread main = new Thread(() -> {
                new XTables(SERVICE_NAME, PORT);
            });
            main.setName("XTABLES-MAIN-SERVER");
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
                instance.get().stopServer(true);
                logger.info("Server stopped gracefully.");
            }));

        }
        return instance.get();
    }

    public static void stopInstance() {
        XTables xTables = instance.get();
        xTables.stopServer(false);
        mainThread.interrupt();
        instance.set(null);
    }

    public static XTables getInstance() {
        return instance.get();
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

    private XTables(String SERVICE_NAME, int PORT) {
        this.port = PORT;
        this.SERVICE_NAME = SERVICE_NAME;
        instance.set(this);
        this.clientThreadPool = Executors.newCachedThreadPool();
        startServer();
    }

    private void startServer() {
        try {
            if (mdnsExecutorService != null) {
                mdnsExecutorService.shutdownNow();
            }
            this.mdnsExecutorService = Executors.newCachedThreadPool();
            mdnsExecutorService.execute(() -> initializeMDNSWithRetries(15));


            serverSocket = new ServerSocket(port);
            logger.info("Server started. Listening on " + serverSocket.getLocalSocketAddress() + "...");
            if (userInterfaceServer == null || !userInterfaceServer.isRunning()) {
                try {
                    userInterfaceServer = new Server(4880);
                    // Static resource handler
                    ResourceHandler resourceHandler = new ResourceHandler();
                    resourceHandler.setDirectoriesListed(true);
                    URL resourceURL = XTables.class.getResource("/static");
                    assert resourceURL != null;
                    String resourceBase = resourceURL.toExternalForm();
                    resourceHandler.setResourceBase(resourceBase);

                    ContextHandler staticContext = new ContextHandler("/");
                    staticContext.setHandler(resourceHandler);

                    ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
                    servletContextHandler.setContextPath("/");

                    // Add a servlet to handle GET requests
                    servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
                        @Override
                        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                            resp.setContentType("application/json");
                            resp.setCharacterEncoding("UTF-8");
                            SystemStatistics systemStatistics = new SystemStatistics(clients.size());
                            systemStatistics.setOnline(!serverSocket.isClosed());
                            synchronized (clients) {
                                systemStatistics.setClientDataList(clients.stream().map(m -> new ClientData(m.clientSocket.getInetAddress().getHostAddress(), m.totalMessages, m.identifier)).toList());
                                int i = 0;
                                for (ClientHandler clientHandler : clients) {
                                    i += clientHandler.totalMessages;
                                }
                                systemStatistics.setTotalMessages(i);
                            }
                            resp.getWriter().println(gson.toJson(systemStatistics));
                        }
                    }), "/api/get");
                    servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
                        @Override
                        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                            resp.setContentType("application/json");
                            resp.setCharacterEncoding("UTF-8");
                            rebootServer();
                            resp.setStatus(HttpServletResponse.SC_OK);
                            resp.getWriter().println("{ \"status\": \"success\", \"message\": \"Server has been rebooted!\"}");
                        }
                    }), "/api/reboot");
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
                            UUID uuid = null;

                            try {
                                uuid = UUID.fromString(uuidParam);
                            } catch (IllegalArgumentException e) {
                                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"Invalid UUID format!\"}");
                                return;
                            }

                            synchronized (clients) {
                                UUID finalUuid = uuid;
                                Optional<ClientHandler> clientHandler = clients.stream().filter(f -> f.identifier.equals(finalUuid.toString())).findFirst();
                                if (clientHandler.isPresent()) {
                                    clientHandler.get().disconnect();
                                    resp.setStatus(HttpServletResponse.SC_OK);
                                    resp.getWriter().println("{ \"status\": \"success\", \"message\": \"The client has been disconnected!\"}");
                                } else {
                                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                    resp.getWriter().println("{ \"status\": \"failed\", \"message\": \"This client does not exist!\"}");
                                }
                            }


                        }
                    }), "/api/disconnect");

                    FilterHolder cors = servletContextHandler.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
                    cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
                    cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
                    cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
                    cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");

                    // Combine the handlers
                    HandlerList handlers = new HandlerList();
                    handlers.addHandler(staticContext);
                    handlers.addHandler(servletContextHandler);

                    userInterfaceServer.setHandler(handlers);
                    userInterfaceServer.start();

                    logger.info("The local XTABLES user interface started at port 4880!");
                } catch (Exception e) {
                    logger.warning("The local XTABLES user interface failed to start!");
                }
            }
            latch.countDown();
            while (!serverSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                logger.info(String.format("Client connected: %1$s:%2$s", clientSocket.getInetAddress(), clientSocket.getPort()));
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                clientThreadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            logger.severe("Socket error occurred: " + e.getMessage());
            if (!serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException ex) {
                    logger.severe("Error closing server socket: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stopServer(boolean fullShutdown) {
        try {
            logger.info("Closing connections to all clients...");
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    client.clientSocket.close();
                }
            }
            clients.clear();
            logger.info("Closing socket server...");
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
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
                InetAddress addr = InetAddress.getLocalHost();
                logger.info("Initializing mDNS with address: " + addr.getHostAddress());
                jmdns = JmDNS.create(addr);
                // Create the service with additional attributes
                Map<String, String> props = new HashMap<>();
                props.put("port", String.valueOf(port));
                serviceInfo = ServiceInfo.create("_xtables._tcp.local.", SERVICE_NAME, SERVICE_PORT, 0, 0, props);
                jmdns.registerService(serviceInfo);

                logger.info("mDNS service registered: " + serviceInfo.getQualifiedName() + " on port " + SERVICE_PORT);
                return;
            } catch (IOException e) {
                logger.severe("Error initializing mDNS (attempt " + attempt + "): " + e.getMessage());
                if (attempt >= maxRetries) {
                    logger.severe("Max retries reached. Giving up on mDNS initialization.");
                    return;
                }

                try {
                    logger.info("Retying mDNS initialization in " + delay + " ms.");
                    TimeUnit.MILLISECONDS.sleep(delay);
                    delay *= 1.5;
                } catch (InterruptedException ie) {
                    logger.severe("Retry sleep interrupted: " + ie.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void rebootServer() {
        try {
            stopServer(false);
            logger.info("Starting socket server in 1 second...");
            TimeUnit.SECONDS.sleep(1);
            logger.info("Starting server...");


            Thread thread = new Thread(this::startServer);
            thread.setDaemon(false);
            thread.start();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void notifyUpdateChangeClients(String key, String value) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getUpdateEvents().contains("") || client.getUpdateEvents().contains(key)) {
                    client.sendUpdate(key, value);
                }
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
    private class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final PrintWriter out;
        private final Set<String> updateEvents = new HashSet<>();
        private final AtomicBoolean deleteEvent = new AtomicBoolean(false);
        private int totalMessages = 0;
        private final String identifier;
        private List<String> streams;

        public ClientHandler(Socket socket) throws IOException {
            this.clientSocket = socket;
            this.identifier = UUID.randomUUID().toString();
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            super.setDaemon(true);
        }

        public Set<String> getUpdateEvents() {
            return updateEvents;
        }

        public Boolean getDeleteEvent() {
            return deleteEvent.get();
        }

        @Override
        public void run() {
            ScheduledFuture<?> messages_log = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }).scheduleAtFixedRate(() -> {
                if (totalMessages != 0) {
                    logger.info("Received " + String.format("%,d", totalMessages) + " messages from IP " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " in the last minute.");
                    totalMessages = 0;
                }
            }, 5, 60, TimeUnit.SECONDS);

            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null && !this.isInterrupted()) {
                    RequestInfo requestInfo = new RequestInfo(inputLine);
                    boolean shouldReply = !requestInfo.getID().equals("IGNORED");
                    totalMessages++;
                    if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.REGISTER_VIDEO_STREAM)) {
                        String name = requestInfo.getTokens()[1].trim();
                        if (Utilities.validateName(name, false)) {
                            if (clients.stream().anyMatch(clientHandler -> clientHandler.streams != null && clientHandler.streams.contains(name))) {
                                if (shouldReply) {
                                    ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.REGISTER_VIDEO_STREAM, ImageStreamStatus.FAIL_NAME_ALREADY_EXISTS.name());
                                    out.println(responseInfo.parsed());
                                    out.flush();
                                }
                            } else {
                                if (streams == null) {
                                    streams = Collections.synchronizedList(new ArrayList<>());
                                }
                                streams.add(name);
                                if (shouldReply) {
                                    ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.REGISTER_VIDEO_STREAM, ImageStreamStatus.OKAY.name());
                                    out.println(responseInfo.parsed());
                                    out.flush();
                                }
                            }
                        } else if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.REGISTER_VIDEO_STREAM, ImageStreamStatus.FAIL_INVALID_NAME.name());
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (shouldReply && requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.GET_VIDEO_STREAM)) {
                        String name = requestInfo.getTokens()[1];
                        if (Utilities.validateName(name, false)) {
                            Optional<ClientHandler> optional = clients.stream().filter(clientHandler -> clientHandler.streams != null && clientHandler.streams.contains(name)).findFirst();
                            ResponseInfo responseInfo;
                            responseInfo = optional.map(clientHandler -> {
                                        String clientAddress = clientHandler.clientSocket.getInetAddress().getHostAddress();
                                        try {
                                            return new ResponseInfo(requestInfo.getID(), MethodType.GET_VIDEO_STREAM, gson.toJson(String.format("http://%1$s:4888/%2$s", clientAddress.equals("127.0.0.1") || clientAddress.equals("::1") ? Utilities.getLocalIpAddress() : clientAddress.replaceFirst("/", ""), name)));
                                        } catch (SocketException e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                                    .orElseGet(() -> new ResponseInfo(requestInfo.getID(), MethodType.GET_VIDEO_STREAM, ImageStreamStatus.FAIL_INVALID_NAME.name()));
                            out.println(responseInfo.parsed());
                            out.flush();
                        } else {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET_VIDEO_STREAM, ImageStreamStatus.FAIL_INVALID_NAME.name());
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (shouldReply && requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.GET)) {
                        String key = requestInfo.getTokens()[1];
                        String result = gson.toJson(table.get(key));
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET, String.format("%1$s " + result, table.isFlaggedKey(key) ? "FLAGGED" : "GOOD"));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (shouldReply && requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.GET_TABLES)) {
                        String key = requestInfo.getTokens()[1];
                        String result = gson.toJson(table.getTables(key));
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET_TABLES, result);
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length >= 3 && requestInfo.getMethod().equals(MethodType.PUT)) {
                        String key = requestInfo.getTokens()[1];
                        String value = String.join(" ", Arrays.copyOfRange(requestInfo.getTokens(), 2, requestInfo.getTokens().length));
                        if (value.equals(table.get(key))) {
                            if (shouldReply) {
                                ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.PUT, "OK");
                                out.println(responseInfo.parsed());
                                out.flush();
                            }
                        } else {
                            boolean response = table.put(key, value);
                            if (shouldReply) {
                                ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.PUT, response ? "OK" : "FAIL");
                                out.println(responseInfo.parsed());
                                out.flush();
                            }
                            if (response) {
                                notifyUpdateChangeClients(key, value);
                            }
                        }
                    } else if (requestInfo.getTokens().length >= 2 && requestInfo.getMethod().equals(MethodType.RUN_SCRIPT)) {
                        String name = requestInfo.getTokens()[1];
                        String customData = String.join(" ", Arrays.copyOfRange(requestInfo.getTokens(), 2, requestInfo.getTokens().length));
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
                                    ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.RUN_SCRIPT, response);
                                    out.println(responseInfo.parsed());
                                    out.flush();
                                }
                            });
                        } else if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.PUT, "FAIL SCRIPT_NOT_FOUND");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (requestInfo.getTokens().length == 3 && requestInfo.getMethod().equals(MethodType.UPDATE_KEY)) {
                        String key = requestInfo.getTokens()[1];
                        String value = requestInfo.getTokens()[2];
                        if (!Utilities.validateName(value, false)) {
                            if (shouldReply) {
                                ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UPDATE_KEY, "FAIL");
                                out.println(responseInfo.parsed());
                                out.flush();
                            }
                        } else {
                            boolean response = table.renameKey(key, value);
                            if (shouldReply) {
                                ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UPDATE_KEY, response ? "OK" : "FAIL");
                                out.println(responseInfo.parsed());
                                out.flush();
                            }
                        }
                    } else if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.DELETE)) {
                        String key = requestInfo.getTokens()[1];
                        boolean response = table.delete(key);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.DELETE, response ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                        if (response) {
                            notifyDeleteChangeClients(key);
                        }
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.DELETE)) {
                        boolean response = table.delete("");
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.DELETE, response ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.SUBSCRIBE_DELETE)) {
                        deleteEvent.set(true);
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.SUBSCRIBE_DELETE, "OK");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.UNSUBSCRIBE_DELETE)) {
                        deleteEvent.set(false);
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UNSUBSCRIBE_DELETE, "OK");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.SUBSCRIBE_UPDATE)) {
                        String key = requestInfo.getTokens()[1];
                        updateEvents.add(key);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.SUBSCRIBE_UPDATE, "OK");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.SUBSCRIBE_UPDATE)) {
                        updateEvents.add("");
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.SUBSCRIBE_UPDATE, "OK");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.UNSUBSCRIBE_UPDATE)) {
                        String key = requestInfo.getTokens()[1];
                        boolean success = updateEvents.remove(key);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UNSUBSCRIBE_UPDATE, success ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.UNSUBSCRIBE_UPDATE)) {
                        boolean success = updateEvents.remove("");
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UNSUBSCRIBE_UPDATE, success ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (shouldReply && requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.PING)) {
                        SystemStatistics systemStatistics = new SystemStatistics(clients.size());
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.PING, ResponseStatus.OK.name() + " " + gson.toJson(systemStatistics).replaceAll(" ", ""));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (shouldReply && requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.GET_TABLES)) {
                        String result = gson.toJson(table.getTables(""));
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET_TABLES, result);
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (shouldReply && requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.GET_RAW_JSON)) {
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET_RAW_JSON, DataCompression.compressAndConvertBase64(table.toJSON()));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.REBOOT_SERVER)) {
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.REBOOT_SERVER, ResponseStatus.OK.name());
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                        rebootServer();
                    } else {
                        // Invalid command
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UNKNOWN, ResponseStatus.FAIL.name());
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    }
                }
                // Close the streams and socket when done
                System.out.println("ADUAUDHAHWDIAWHDIUA");
                out.close();
                in.close();
                clientSocket.close();
                clients.remove(this);
                messages_log.cancel(true);
                this.interrupt();
            } catch (IOException e) {
                String message = e.getMessage();
                if (message.contains("Connection reset")) {
                    logger.info(String.format("Client disconnected: %1$s:%2$s", clientSocket.getInetAddress(), clientSocket.getPort()));
                } else {
                    logger.severe("Error occurred: " + e.getMessage());
                }
                messages_log.cancel(true);
                clients.remove(this);
            }
        }

        public void disconnect() throws IOException {
            this.clientSocket.close();
        }

        public void sendUpdate(String key, String value) {
            out.println(new ResponseInfo(null, MethodType.UPDATE_EVENT, key + " " + value).parsed());
            out.flush();
        }

        public void sendDelete(String key) {
            out.println(new ResponseInfo(null, MethodType.DELETE_EVENT, key).parsed());
            out.flush();
        }
    }

    public record ScriptParameters(XTablesData<String> data, String customData) {
    }


}
