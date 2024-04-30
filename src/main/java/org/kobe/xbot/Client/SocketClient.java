package org.kobe.xbot.Client;

import com.google.gson.Gson;
import com.sun.jdi.connect.spi.ClosedConnectionException;
import org.kobe.xbot.Utilites.MethodType;
import org.kobe.xbot.Utilites.RequestInfo;
import org.kobe.xbot.Utilites.ResponseInfo;
import org.kobe.xbot.Utilites.ResponseStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class SocketClient {
    private final Logger logger = Logger.getLogger(SocketClient.class.getName());
    private final ExecutorService executor;
    private ThreadPoolExecutor socketExecutor = new ThreadPoolExecutor(0, 3, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
    private final String SERVER_ADDRESS;
    private final int SERVER_PORT;
    private long RECONNECT_DELAY_MS;
    private static final List<RequestInfo> MESSAGES = new ArrayList<>() {
        private final Logger logger = Logger.getLogger(ArrayList.class.getName());

        @Override
        public boolean add(RequestInfo requestInfo) {
            boolean hasLogged = false;
            boolean added = super.add(requestInfo);
            while (added && size() > 100) {
                if (!hasLogged) {
                    logger.info("Dumping all old cached messages...");
                    hasLogged = true;
                }
                super.remove(0);
            }
            return added;
        }
    };

    public Boolean isConnected = null;
    private PrintWriter out = null;
    private BufferedReader in = null;
    private Socket socket;
    private Consumer<KeyValuePair<String>> updateConsumer;
    private final XTablesClient xTablesClient;

    public SocketClient(String SERVER_ADDRESS, int SERVER_PORT, long RECONNECT_DELAY_MS, int MAX_THREADS, XTablesClient xTablesClient) {
        this.socket = null;
        this.SERVER_ADDRESS = SERVER_ADDRESS;
        this.SERVER_PORT = SERVER_PORT;
        this.RECONNECT_DELAY_MS = RECONNECT_DELAY_MS;
        this.executor = Executors.newFixedThreadPool(MAX_THREADS);
        this.xTablesClient = xTablesClient;
    }

    public long getRECONNECT_DELAY_MS() {
        return RECONNECT_DELAY_MS;
    }

    public SocketClient setRECONNECT_DELAY_MS(long RECONNECT_DELAY_MS) {
        this.RECONNECT_DELAY_MS = RECONNECT_DELAY_MS;
        return this;
    }

    public List<RequestInfo> getMessages() {
        return MESSAGES;
    }

    public void setUpdateConsumer(Consumer<KeyValuePair<String>> updateConsumer) {
        this.updateConsumer = updateConsumer;
    }

    public void connect() {
        while (true) {
            try {
                logger.info(String.format("Connecting to server: %1$s:%2$s", SERVER_ADDRESS, SERVER_PORT));
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                if (!socket.isConnected()) throw new IOException();
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                logger.info(String.format("Connected to server: %1$s:%2$s", SERVER_ADDRESS, SERVER_PORT));
                socketExecutor.shutdownNow();
                socketExecutor = new ThreadPoolExecutor(0, 3, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
                socketExecutor.submit(new ClientMessageListener(socket));
                List<RequestAction<ResponseStatus>> requestActions = xTablesClient.resubscribeToAllUpdateEvents();
                if (!requestActions.isEmpty()) {
                    logger.info("Resubscribing to all previously submitted update events.");
                    xTablesClient.queueAll(requestActions);
                    logger.info("Queued " + requestActions.size() + " subscriptions successfully!");
                }
                break;
            } catch (IOException e) {
                logger.warning("Failed to connect to server. Retrying...");
                try {
                    // Wait before attempting reconnection
                    TimeUnit.MILLISECONDS.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public class ClientMessageListener implements Runnable {
        private final Socket socket;

        public ClientMessageListener(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    RequestInfo requestInfo = new RequestInfo(message);
                    MESSAGES.add(requestInfo);
                    if (requestInfo.getTokens().length >= 3 && requestInfo.getMethod().equals(MethodType.UPDATE)) {
                        String key = requestInfo.getTokens()[1];
                        String value = String.join(" ", Arrays.copyOfRange(requestInfo.getTokens(), 2, requestInfo.getTokens().length));
                        if (updateConsumer != null) {
                            KeyValuePair<String> keyValuePair = new KeyValuePair<>(key, value);
                            updateConsumer.accept(keyValuePair);
                            MESSAGES.remove(requestInfo);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading message from server: " + e.getMessage());
                logger.warning("Disconnected from the server. Reconnecting...");
                reconnect();
            }

        }
    }

    public RequestInfo waitForMessage(String ID, long timeout, TimeUnit unit) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = unit.toMillis(timeout);

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            for (RequestInfo message : new ArrayList<>(MESSAGES)) {
                if (message != null && message.getID().equals(ID)) {
                    MESSAGES.remove(message);
                    return message;
                }
            }
        }
        return null;
    }


    public RequestInfo sendMessageAndWaitForReply(ResponseInfo responseInfo, long timeout, TimeUnit unit) throws InterruptedException {
        sendMessage(responseInfo);
        return waitForMessage(responseInfo.getID(), timeout, unit);
    }

    public void sendMessage(ResponseInfo responseInfo) {
        out.println(responseInfo.parsed());
        out.flush();
    }

    private void reconnect() {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
        this.connect();


    }


    public Socket getSocket() {
        return socket;
    }

    private boolean isConnected() {
        boolean serverResponded = false;
        try {
            RequestInfo info = sendMessageAndWaitForReply(new ResponseInfo(null, MethodType.PING), 3, TimeUnit.SECONDS);
            serverResponded = info != null;
        } catch (Exception ignored) {
        }
        boolean connected = socket != null && !socket.isClosed() && socket.isConnected() && serverResponded;
        this.isConnected = connected;
        return connected;
    }


    public CompletableFuture<String> sendAsync(String message) {
        CompletableFuture<String> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                RequestInfo requestInfo = sendMessageAndWaitForReply(ResponseInfo.from(message), 3, TimeUnit.SECONDS);
                if (requestInfo == null) throw new ClosedConnectionException();
                String[] tokens = requestInfo.getTokens();
                future.complete(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)));
            } catch (InterruptedException | ClosedConnectionException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public <T> CompletableFuture<T> sendAsync(String message, Type type) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                RequestInfo requestInfo = sendMessageAndWaitForReply(ResponseInfo.from(message), 3, TimeUnit.SECONDS);
                if (requestInfo == null) throw new ClosedConnectionException();
                String[] tokens = requestInfo.getTokens();
                if (type == null) {
                    future.complete((T) String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)));
                } else {
                    T parsed = new Gson().fromJson(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)), type);
                    future.complete(parsed);
                }
            } catch (InterruptedException | ClosedConnectionException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }


    public <T> T sendComplete(String message, Type type) throws ExecutionException, InterruptedException, TimeoutException {
        String response = sendAsync(message).get(3, TimeUnit.SECONDS);
        if (type == null) {
            return (T) response;
        } else {
            return new Gson().fromJson(response, type);
        }
    }

    public static class KeyValuePair<T> {
        private String key;
        private T value;

        public KeyValuePair(String key, T value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

}

