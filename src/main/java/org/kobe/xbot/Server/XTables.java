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
import org.kobe.xbot.Utilities.Entities.ScriptParameters;
import org.kobe.xbot.Utilities.Exceptions.ScriptAlreadyExistsException;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final XTablesData table = new XTablesData();
    private ServerSocket serverSocket;
    private final int port;
    private final ExecutorService clientThreadPool;
    private final HashMap<String, Function<ScriptParameters, String>> scripts = new HashMap<>();
    private static Thread mainThread;
    private static final CountDownLatch latch = new CountDownLatch(1);
    private ExecutorService mdnsExecutorService;
    private Server userInterfaceServer;
    private static final AtomicReference<XTableStatus> status = new AtomicReference<>(XTableStatus.OFFLINE);

    private XTables(String SERVICE_NAME, int PORT) {
        this.port = PORT;
        this.SERVICE_NAME = SERVICE_NAME;
        instance.set(this);
        this.clientThreadPool = Executors.newCachedThreadPool();
        startServer();
    }

    public static XTables startInstance(String SERVICE_NAME, int PORT) {
        if (instance.get() == null) {
            if (PORT == 5353)
                throw new IllegalArgumentException("The port 5353 is reserved for mDNS services.");
            if (SERVICE_NAME.equalsIgnoreCase("localhost"))
                throw new IllegalArgumentException("The mDNS service name cannot be localhost!");
            status.set(XTableStatus.STARTING);
            Thread main = new Thread(() -> new XTables(SERVICE_NAME, PORT));
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
            this.mdnsExecutorService = Executors.newCachedThreadPool();
            mdnsExecutorService.execute(() -> initializeMDNSWithRetries(15));

            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                if (e.getMessage().contains("Address already in use")) {
                    logger.fatal("Port " + port + " is already in use.");
                    logger.fatal("Exiting server now...");
                    System.exit(1);
                } else {
                    throw e;
                }
            }
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
                            systemStatistics.setStatus(XTables.getStatus());
                            synchronized (clients) {
                                systemStatistics.setClientDataList(clients.stream().map(m -> new ClientData(m.clientSocket.getInetAddress().getHostAddress(), m.totalMessages, m.identifier)).collect(Collectors.toList()));
                                int i = 0;
                                for (ClientHandler client : clients) {
                                    i += client.totalMessages;
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
                    logger.info("The local XTABLES user interface started at port http://localhost:4880!");
                } catch (Exception e) {
                    logger.warning("The local XTABLES user interface failed to start!");
                }
            }
            latch.countDown();
            status.set(XTableStatus.ONLINE);
            while (!serverSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                logger.info(String.format("Client connected: %1$s:%2$s", clientSocket.getInetAddress(), clientSocket.getPort()));
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                clientThreadPool.execute(clientHandler);
                status.set(XTableStatus.ONLINE);
            }
        } catch (IOException e) {
            logger.severe("Socket error occurred: " + e.getMessage());
            status.set(XTableStatus.OFFLINE);
            if (serverSocket != null && !serverSocket.isClosed()) {
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
            status.set(XTableStatus.OFFLINE);
            for (ClientHandler client : new ArrayList<>(clients)) {
                if (client != null) client.clientSocket.close();
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
                InetAddress addr = Utilities.getLocalInetAddress();
                if (addr == null) {
                    throw new RuntimeException("No local IP address found!");
                }
                logger.info("Initializing mDNS with address: " + addr.getHostAddress());
                jmdns = JmDNS.create(addr);
                // Create the service with additional attributes
                Map<String, String> props = new HashMap<>();
                props.put("port", String.valueOf(port));
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
            status.set(XTableStatus.REBOOTING);
            stopServer(false);
            logger.info("Starting socket server in 1 second...");
            status.set(XTableStatus.STARTING);
            TimeUnit.SECONDS.sleep(1);
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
        synchronized (clients) {
            Iterator<ClientHandler> iterator = clients.iterator();
            if (iterator.hasNext()) {
                do {
                    ClientHandler client = iterator.next();
                    try {
                        Set<String> updateEvents = client.getUpdateEvents();
                        if (updateEvents.contains("") || updateEvents.contains(key)) {
                            client.sendUpdate(key, value);
                        }
                    } catch (Exception | Error e) {
                        logger.warning("Failed to push updates to client: " + e.getMessage());
                    }
                } while (iterator.hasNext());
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
            ScheduledExecutorService messages_log_executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            });
            messages_log_executor.scheduleAtFixedRate(() -> {
                if (totalMessages != 0) {
                    logger.info("Received " + String.format("%,d", totalMessages) + " messages from IP " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " in the last minute.");
                    totalMessages = 0;
                }
            }, 60, 60, TimeUnit.SECONDS);

            // try with resources for no memory leak

            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                String inputLine;
                while ((inputLine = in.readLine()) != null && !this.isInterrupted()) {
                    totalMessages++;
                    String raw = inputLine.replace("\n", "").trim();
                    String[] tokens = tokenize(raw, ' ');
                    String[] requestTokens = tokenize(tokens[0], ':');
                    String id = requestTokens[0];
                    MethodType methodType;
                    try {
                        methodType = MethodType.valueOf(requestTokens[1]);
                    } catch (Exception e) {
                        methodType = MethodType.UNKNOWN;
                    }
                    boolean shouldReply = !id.equals("IGNORED");
                    if (tokens.length == 2 && methodType.equals(MethodType.REGISTER_VIDEO_STREAM)) {
                        String name = tokens[1].trim();
                        if (Utilities.validateName(name, false)) {
                            if (clients.stream().anyMatch(clientHandler -> clientHandler.streams != null && clientHandler.streams.contains(name))) {
                                if (shouldReply) {
                                    ResponseInfo responseInfo = new ResponseInfo(id, MethodType.REGISTER_VIDEO_STREAM, ImageStreamStatus.FAIL_NAME_ALREADY_EXISTS.name());
                                    out.println(responseInfo.parsed());
                                    out.flush();
                                }
                            } else {
                                if (streams == null) {
                                    streams = Collections.synchronizedList(new ArrayList<>());
                                }
                                streams.add(name);
                                if (shouldReply) {
                                    ResponseInfo responseInfo = new ResponseInfo(id, MethodType.REGISTER_VIDEO_STREAM, ImageStreamStatus.OKAY.name());
                                    out.println(responseInfo.parsed());
                                    out.flush();
                                }
                            }
                        } else if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.REGISTER_VIDEO_STREAM, ImageStreamStatus.FAIL_INVALID_NAME.name());
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (shouldReply && tokens.length == 2 && methodType.equals(MethodType.GET_VIDEO_STREAM)) {
                        String name = tokens[1];
                        if (Utilities.validateName(name, false)) {
                            Optional<ClientHandler> optional = clients.stream().filter(clientHandler -> clientHandler.streams != null && clientHandler.streams.contains(name)).findFirst();
                            ResponseInfo responseInfo;
                            responseInfo = optional.map(clientHandler -> {
                                        String clientAddress = clientHandler.clientSocket.getInetAddress().getHostAddress();
                                        return new ResponseInfo(id, MethodType.GET_VIDEO_STREAM, gson.toJson(String.format("http://%1$s:4888/%2$s", clientAddress.equals("127.0.0.1") || clientAddress.equals("::1") ? Utilities.getLocalIPAddress() : clientAddress.replaceFirst("/", ""), name)));
                                    })
                                    .orElseGet(() -> new ResponseInfo(id, MethodType.GET_VIDEO_STREAM, ImageStreamStatus.FAIL_INVALID_NAME.name()));
                            out.println(responseInfo.parsed());
                            out.flush();
                        } else {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.GET_VIDEO_STREAM, ImageStreamStatus.FAIL_INVALID_NAME.name());
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (shouldReply && tokens.length == 2 && methodType.equals(MethodType.GET)) {
                        String key = tokens[1];
                        String result = gson.toJson(table.get(key));
                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.GET, String.format("%1$s " + result, table.isFlaggedKey(key) ? "FLAGGED" : "GOOD"));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (shouldReply && tokens.length == 2 && methodType.equals(MethodType.GET_TABLES)) {
                        String key = tokens[1];
                        String result = gson.toJson(table.getTables(key));
                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.GET_TABLES, result);
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (tokens.length >= 3 && methodType.equals(MethodType.PUT)) {
                        String key = tokens[1];
                        String value = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length));
                        boolean response = table.put(key, value);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.PUT, response ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                        if (response) {
                            notifyUpdateChangeClients(key, value);
                        }
                    } else if (tokens.length >= 2 && methodType.equals(MethodType.RUN_SCRIPT)) {
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
                                    ResponseInfo responseInfo = new ResponseInfo(id, MethodType.RUN_SCRIPT, response);
                                    out.println(responseInfo.parsed());
                                    out.flush();
                                }
                            });
                        } else if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.PUT, "FAIL SCRIPT_NOT_FOUND");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (tokens.length == 3 && methodType.equals(MethodType.UPDATE_KEY)) {
                        String key = tokens[1];
                        String value = tokens[2];
                        if (!Utilities.validateName(value, false)) {
                            if (shouldReply) {
                                ResponseInfo responseInfo = new ResponseInfo(id, MethodType.UPDATE_KEY, "FAIL");
                                out.println(responseInfo.parsed());
                                out.flush();
                            }
                        } else {
                            boolean response = table.renameKey(key, value);
                            if (shouldReply) {
                                ResponseInfo responseInfo = new ResponseInfo(id, MethodType.UPDATE_KEY, response ? "OK" : "FAIL");
                                out.println(responseInfo.parsed());
                                out.flush();
                            }
                        }
                    } else if (tokens.length == 2 && methodType.equals(MethodType.DELETE)) {
                        String key = tokens[1];
                        boolean response = table.delete(key);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.DELETE, response ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                        if (response) {
                            notifyDeleteChangeClients(key);
                        }
                    } else if (tokens.length == 1 && methodType.equals(MethodType.DELETE)) {
                        boolean response = table.delete("");
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.DELETE, response ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (tokens.length == 1 && methodType.equals(MethodType.SUBSCRIBE_DELETE)) {
                        deleteEvent.set(true);
                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.SUBSCRIBE_DELETE, "OK");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (tokens.length == 1 && methodType.equals(MethodType.UNSUBSCRIBE_DELETE)) {
                        deleteEvent.set(false);
                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.UNSUBSCRIBE_DELETE, "OK");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (tokens.length == 2 && methodType.equals(MethodType.SUBSCRIBE_UPDATE)) {
                        String key = tokens[1];
                        updateEvents.add(key);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.SUBSCRIBE_UPDATE, "OK");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (tokens.length == 1 && methodType.equals(MethodType.SUBSCRIBE_UPDATE)) {
                        updateEvents.add("");
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.SUBSCRIBE_UPDATE, "OK");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (tokens.length == 2 && methodType.equals(MethodType.UNSUBSCRIBE_UPDATE)) {
                        String key = tokens[1];
                        boolean success = updateEvents.remove(key);
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.UNSUBSCRIBE_UPDATE, success ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (tokens.length == 1 && methodType.equals(MethodType.UNSUBSCRIBE_UPDATE)) {
                        boolean success = updateEvents.remove("");
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.UNSUBSCRIBE_UPDATE, success ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (shouldReply && tokens.length == 1 && methodType.equals(MethodType.PING)) {
                        SystemStatistics systemStatistics = new SystemStatistics(clients.size());
                        int i = 0;
                        for (ClientHandler client : clients) {
                            i += client.totalMessages;
                        }
                        systemStatistics.setTotalMessages(i);
                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.PING, ResponseStatus.OK.name() + " " + gson.toJson(systemStatistics).replaceAll(" ", ""));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (shouldReply && tokens.length == 1 && methodType.equals(MethodType.GET_TABLES)) {
                        String result = gson.toJson(table.getTables(""));
                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.GET_TABLES, result);
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (shouldReply && tokens.length == 1 && methodType.equals(MethodType.GET_RAW_JSON)) {
                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.GET_RAW_JSON, DataCompression.compressAndConvertBase64(table.toJSON()));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (tokens.length == 1 && methodType.equals(MethodType.REBOOT_SERVER)) {
                        if (shouldReply) {
                            ResponseInfo responseInfo = new ResponseInfo(id, MethodType.REBOOT_SERVER, ResponseStatus.OK.name());
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                        rebootServer();
                    } else if (shouldReply) {
                        // Invalid command
                        logger.warning("Unknown command received from " + clientSocket.getInetAddress().getHostAddress() + ": " + inputLine);

                        ResponseInfo responseInfo = new ResponseInfo(id, MethodType.UNKNOWN, ResponseStatus.FAIL.name());
                        out.println(responseInfo.parsed());
                        out.flush();

                    }
                }
            } catch (IOException e) {
                String message = e.getMessage();
                if (message.contains("Connection reset")) {
                    logger.info(String.format("Client disconnected: %1$s:%2$s", clientSocket.getInetAddress(), clientSocket.getPort()));
                } else {
                    logger.severe("Error occurred: " + e.getMessage());
                }
            } finally {
                logger.info(String.format("Starting cleanup for client: %1$s:%2$s",
                        clientSocket.getInetAddress(),
                        clientSocket.getPort()));
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logger.severe("Error closing client socket: " + e.getMessage());
                }
                clients.remove(this);
                messages_log_executor.shutdownNow();
                logger.info(String.format("Finishing cleanup for client: %1$s:%2$s.",
                        clientSocket.getInetAddress(),
                        clientSocket.getPort()));
                this.interrupt();

            }
        }

        public void disconnect() throws IOException {
            clientSocket.close();
            clients.remove(this);
            this.interrupt();
        }

        public void sendUpdate(String key, String value) {
            out.println("null:UPDATE_EVENT " + (key + " " + value).replaceAll("\n", ""));
            out.flush();
        }

        public void sendDelete(String key) {
            out.println("null:DELETE_EVENT " + (key).replaceAll("\n", ""));
            out.flush();
        }
    }

    private String[] tokenize(String input, char delimiter) {
        int count = 1;
        int length = input.length();
        for (int i = 0; i < length; i++) {
            if (input.charAt(i) == delimiter) {
                count++;
            }
        }

        // Allocate array for results
        String[] result = new String[count];
        int index = 0;
        int tokenStart = 0;

        // Second pass: Extract tokens
        for (int i = 0; i < length; i++) {
            if (input.charAt(i) == delimiter) {
                result[index++] = input.substring(tokenStart, i);
                tokenStart = i + 1;
            }
        }

        // Add last token
        result[index] = input.substring(tokenStart);

        return result;
    }
}
