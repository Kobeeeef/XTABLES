/*
 * XTables class manages a server that allows clients to interact with a key-value data structure.
 * It provides methods to start the server, handle client connections, and process client requests.
 * The class also supports server reboot functionality and notification of clients upon data updates.
 *
 * Author: Kobe
 *
 */

package org.kobe.xbot.Server;

import com.google.gson.Gson;
import org.kobe.xbot.Utilites.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class XTables {
    private static AtomicReference<XTables> instance = new AtomicReference<>();
    private final Gson gson = new Gson();
    private final XTablesLogger logger = XTablesLogger.getLogger();
    private final Set<ClientHandler> clients = new HashSet<>();
    private final XTablesData<String> table = new XTablesData<>();
    private ServerSocket serverSocket;
    private final int port;
    private final ExecutorService clientThreadPool;

    public static XTables startInstance(int PORT) {
        if (instance.get() == null) {
           Thread main = new Thread(() -> {
              XTables xTables = new XTables(PORT);
               instance.set(xTables);
            });
           main.setName("XTABLES-MAIN-SERVER");
           main.setDaemon(false);
           main.start();
        }
        return instance.get();
    }

    private XTables(int PORT) {
        this.port = PORT;
        this.clientThreadPool = Executors.newCachedThreadPool();
        startServer();
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            logger.info("Server started. Listening on " + serverSocket.getLocalSocketAddress() + "...");

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                logger.info(String.format("Client connected: %1$s:%2$s", clientSocket.getInetAddress(), clientSocket.getPort()));
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                clientThreadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            logger.severe("Error occurred: " + e.getMessage());
            if (!serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException ex) {
                    logger.severe("Error closing server socket: " + ex.getMessage());
                }
            }
        }
    }

    public void rebootServer() {
        try {
            logger.info("Closing connections to all clients...");
            for (ClientHandler client : clients) {
                client.clientSocket.close();
            }
            clients.clear();
            logger.info("Closing socket server...");
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            table.delete("");
            logger.info("Starting socket server in 1 second...");
            TimeUnit.SECONDS.sleep(1);
            logger.info("Starting server...");
            startServer();

        } catch (IOException | InterruptedException e) {
            System.err.println(e);
            logger.severe("Error occurred during server reboot: " + e.getMessage());
        }
    }

    private void notifyClients(String key, String value) {
        for (ClientHandler client : clients) {
            if (client.getUpdateEvents().contains("") || client.getUpdateEvents().contains(key)) {
                client.sendUpdate(key, value);
            }
        }
    }


    // Thread to handle each client connection
    private class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final PrintWriter out;
        private final Set<String> updateEvents = new HashSet<>();
        private int totalMessages = 0;

        public ClientHandler(Socket socket) throws IOException {
            this.clientSocket = socket;
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            super.setDaemon(true);
        }

        public Set<String> getUpdateEvents() {
            return updateEvents;
        }

        @Override
        public void run() {
            ScheduledFuture<?> messages_log = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }).scheduleAtFixedRate(() -> {
                if (totalMessages != 0) {
                    logger.info("Received " + totalMessages + " messages from IP " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " in the last minute.");
                    totalMessages = 0;
                }
            }, 5, 60, TimeUnit.SECONDS);

            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null && !this.isInterrupted()) {
                    RequestInfo requestInfo = new RequestInfo(inputLine);
                    totalMessages++;
                    if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.GET)) {
                        String key = requestInfo.getTokens()[1];
                        String result = gson.toJson(table.get(key));
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET, String.format("%1$s " + result, table.isFlaggedKey(key) ? "FLAGGED" : "GOOD"));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.GET_TABLES)) {
                        String key = requestInfo.getTokens()[1];
                        String result = gson.toJson(table.getTables(key));
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET_TABLES, result);
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length >= 3 && requestInfo.getMethod().equals(MethodType.PUT)) {
                        String key = requestInfo.getTokens()[1];
                        String value = String.join(" ", Arrays.copyOfRange(requestInfo.getTokens(), 2, requestInfo.getTokens().length));
                        if (value.equals(table.get(key))) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.PUT, "OK");
                            out.println(responseInfo.parsed());
                            out.flush();
                        } else {
                            boolean response = table.put(key, value);
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.PUT, response ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                            if (response) {
                                notifyClients(key, value);
                            }
                        }
                    } else if (requestInfo.getTokens().length == 3 && requestInfo.getMethod().equals(MethodType.UPDATE_KEY)) {
                        String key = requestInfo.getTokens()[1];
                        String value = requestInfo.getTokens()[2];
                        if (!Utilities.validateName(value, false)) {
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UPDATE_KEY, "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        } else {
                            boolean response = table.renameKey(key, value);
                            ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UPDATE_KEY, response ? "OK" : "FAIL");
                            out.println(responseInfo.parsed());
                            out.flush();
                        }
                    } else if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.DELETE)) {
                        String key = requestInfo.getTokens()[1];
                        boolean response = table.delete(key);
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.DELETE, response ? "OK" : "FAIL");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.DELETE)) {
                        boolean response = table.delete("");
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.DELETE, response ? "OK" : "FAIL");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.SUBSCRIBE_UPDATE)) {
                        String key = requestInfo.getTokens()[1];
                        updateEvents.add(key);
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.SUBSCRIBE_UPDATE, "OK");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.SUBSCRIBE_UPDATE)) {
                        updateEvents.add("");
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.SUBSCRIBE_UPDATE, "OK");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 2 && requestInfo.getMethod().equals(MethodType.UNSUBSCRIBE_UPDATE)) {
                        String key = requestInfo.getTokens()[1];
                        boolean success = updateEvents.remove(key);
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UNSUBSCRIBE_UPDATE, success ? "OK" : "FAIL");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.UNSUBSCRIBE_UPDATE)) {
                        boolean success = updateEvents.remove("");
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UNSUBSCRIBE_UPDATE, success ? "OK" : "FAIL");
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.PING)) {
                        SystemStatistics systemStatistics = new SystemStatistics(clients.size());
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.PING, ResponseStatus.OK.name() + " " + gson.toJson(systemStatistics).replaceAll(" ", ""));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.GET_TABLES)) {
                        String result = gson.toJson(table.getTables(""));
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET_TABLES, result);
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.GET_RAW_JSON)) {
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.GET_RAW_JSON, DataCompression.compressAndConvertBase64(table.toJSON()));
                        out.println(responseInfo.parsed());
                        out.flush();
                    } else if (requestInfo.getTokens().length == 1 && requestInfo.getMethod().equals(MethodType.REBOOT_SERVER)) {
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.REBOOT_SERVER, ResponseStatus.OK.name());
                        out.println(responseInfo.parsed());
                        out.flush();
                        rebootServer();
                    } else {
                        // Invalid command
                        ResponseInfo responseInfo = new ResponseInfo(requestInfo.getID(), MethodType.UNKNOWN);
                        out.println(responseInfo.parsed());
                        out.flush();
                    }
                }
                // Close the streams and socket when done
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

        public void sendUpdate(String key, String value) {
            out.println(new ResponseInfo(null, MethodType.UPDATE, key + " " + value).parsed());
            out.flush();
        }
    }
}
