package org.kobe.xbot.Client;

import com.google.gson.Gson;
import com.sun.jdi.connect.spi.ClosedConnectionException;
import org.kobe.xbot.Utilites.*;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class SocketClient {
    private final XTablesLogger logger = XTablesLogger.getLogger();
    private ExecutorService executor;
    private ThreadPoolExecutor socketExecutor;
    private String SERVER_ADDRESS;
    private int SERVER_PORT;
    private long RECONNECT_DELAY_MS;
    private boolean CLEAR_UPDATE_MESSAGES = true;
    private final List<RequestInfo> MESSAGES = new ArrayList<>() {
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
    private int MAX_THREADS = 2;
    public Boolean isConnected = false;
    private PrintWriter out = null;
    private BufferedReader in = null;
    private Socket socket;
    private Consumer<KeyValuePair<String>> updateConsumer;
    private final XTablesClient xTablesClient;

    public SocketClient(String SERVER_ADDRESS, int SERVER_PORT, long RECONNECT_DELAY_MS, int MAX_THREADS, XTablesClient xTablesClient) {
        this.socket = null;
        this.MAX_THREADS = Math.max(MAX_THREADS, 1);
        this.SERVER_ADDRESS = SERVER_ADDRESS;
        this.SERVER_PORT = SERVER_PORT;
        this.RECONNECT_DELAY_MS = RECONNECT_DELAY_MS;
        this.executor = getWorkerExecutor(MAX_THREADS);
        this.xTablesClient = xTablesClient;
    }

    public String getSERVER_ADDRESS() {
        return SERVER_ADDRESS;
    }

    public int getSERVER_PORT() {
        return SERVER_PORT;
    }

    public SocketClient setSERVER_ADDRESS(String SERVER_ADDRESS) {
        this.SERVER_ADDRESS = SERVER_ADDRESS;
        return this;
    }

    public List<RequestInfo> getMESSAGES() {
        return MESSAGES;
    }

    public SocketClient setSERVER_PORT(int SERVER_PORT) {
        this.SERVER_PORT = SERVER_PORT;
        return this;
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

    public boolean isCLEAR_UPDATE_MESSAGES() {
        return CLEAR_UPDATE_MESSAGES;
    }

    public SocketClient setCLEAR_UPDATE_MESSAGES(boolean CLEAR_UPDATE_MESSAGES) {
        this.CLEAR_UPDATE_MESSAGES = CLEAR_UPDATE_MESSAGES;
        return this;
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
                if (executor == null || executor.isShutdown()) {
                    this.executor = getWorkerExecutor(MAX_THREADS);
                }
                if (socketExecutor != null) {
                    socketExecutor.shutdownNow();
                }
                this.socketExecutor = getSocketExecutor();
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

    private ThreadPoolExecutor getSocketExecutor() {
        return new ThreadPoolExecutor(
                0,
                3,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        String messageName = "SocketClient-MessageHandler-" + counter.getAndIncrement();
                        Thread thread = new Thread(r);
                        thread.setDaemon(false);
                        thread.setName(messageName);
                        logger.info("Starting SocketClient main thread: " + thread.getName());
                        return thread;
                    }
                }
        );
    }

    private ExecutorService getWorkerExecutor(int initialMaxThreads) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0,
                initialMaxThreads,
                1000L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );
        // Set a custom ThreadFactory to give meaningful names to threads
        executor.setThreadFactory(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                String workerName = "SocketClient-WorkerThread-" + counter.getAndIncrement();
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName(workerName);
                logger.info("Starting worker thread: " + workerName);
                return thread;
            }
        });

        return executor;
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
                while ((message = in.readLine()) != null && !socket.isClosed() && socket.isConnected()) {
                    isConnected = true;
                    RequestInfo requestInfo = new RequestInfo(message);
                    MESSAGES.add(requestInfo);
                    if (requestInfo.getTokens().length >= 3 && requestInfo.getMethod().equals(MethodType.UPDATE)) {
                        String ID = requestInfo.getID();
                        String key = requestInfo.getTokens()[1];
                        String value = String.join(" ", Arrays.copyOfRange(requestInfo.getTokens(), 2, requestInfo.getTokens().length));
                        if (updateConsumer != null) {
                            KeyValuePair<String> keyValuePair = new KeyValuePair<>(key, value);
                            updateConsumer.accept(keyValuePair);
                            if (CLEAR_UPDATE_MESSAGES) MESSAGES.remove(requestInfo);
                        }
                    }
                }
                isConnected = false;
                if (!socket.isClosed()) {
                    logger.warning("Disconnected from the server. Reconnecting...");
                    try {
                        // Wait before attempting reconnection
                        TimeUnit.MILLISECONDS.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    reconnect();
                }
            } catch (IOException e) {
                isConnected = false;
                if (!socket.isClosed()) {
                    System.err.println("Error reading message from server: " + e.getMessage());
                    logger.warning("Disconnected from the server. Reconnecting...");
                    try {
                        // Wait before attempting reconnection
                        TimeUnit.MILLISECONDS.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    reconnect();
                }
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

    public void stopAll() {
        long startTime = System.nanoTime();
        logger.severe("Shutting down all threads and processes.");
        try {
            if (socketExecutor != null) {
                socketExecutor.shutdownNow();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            logger.severe("Failed to shutdown main threads: " + e.getMessage());
        }
        isConnected = false;
        try {
            if (socket != null) {
                socket.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            logger.severe("Failed to close socket or streams: " + e.getMessage());
        }
        double elapsedTimeMS = (System.nanoTime() - startTime) / 1e6;
        logger.severe("SocketClient is now closed. (" + elapsedTimeMS + "ms)");
    }


    public void reconnect() {
        long startTime = System.nanoTime();
        logger.severe("Shutting down all worker threads.");
        try {
            if (executor != null) {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            logger.severe("Failed to shutdown worker threads: " + e.getMessage());
        }
        isConnected = false;
        try {
            if (socket != null) {
                socket.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            logger.severe("Failed to close socket or streams: " + e.getMessage());
        }
        double elapsedTimeMS = (System.nanoTime() - startTime) / 1e6;
        logger.severe("SocketClient is now closed. (" + elapsedTimeMS + "ms)");
        this.connect();
    }


    public Socket getSocket() {
        return socket;
    }


    public CompletableFuture<String> sendAsync(String message) throws IOException {
        if (executor == null || executor.isShutdown())
            throw new IOException("The worker thread executor is shutdown and no new requests can be made.");
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

    public <T> CompletableFuture<T> sendAsync(String message, Type type) throws IOException {
        if (executor == null || executor.isShutdown())
            throw new IOException("The worker thread executor is shutdown and no new requests can be made.");
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


    public <T> T sendComplete(String message, Type type) throws ExecutionException, InterruptedException, TimeoutException, IOException {
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

