package org.kobe.xbot.ClientLite;

import org.kobe.xbot.Utilities.Entities.KeyValuePair;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.*;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
    private final int MAX_THREADS;
    public boolean isConnected = false;
    private PrintWriter out = null;
    private BufferedReader in = null;
    private Socket socket;
    private Consumer<KeyValuePair<String>> updateConsumer;
    private Consumer<String> deleteConsumer;
    private final XTablesClient xTablesClient;
    private ZContext ctx;
    private ZMQ.Socket ZMQ_PUSH_SOCKET;
    private ZMQ.Socket ZMQ_SUB_SOCKET;
    public SocketClient(String SERVER_ADDRESS, int SERVER_PORT, boolean ENABLE_ZMQ, long RECONNECT_DELAY_MS, int MAX_THREADS_ARG, XTablesClient xTablesClient) {
        this.socket = null;
        this.MAX_THREADS = Math.max(MAX_THREADS_ARG, 1);
        this.SERVER_ADDRESS = SERVER_ADDRESS;
        this.SERVER_PORT = SERVER_PORT;
        this.RECONNECT_DELAY_MS = RECONNECT_DELAY_MS;
        this.executor = getWorkerExecutor(MAX_THREADS);
        this.xTablesClient = xTablesClient;
        this.ctx = new ZContext();
        if(ENABLE_ZMQ) {
            this.ZMQ_PUSH_SOCKET = ctx.createSocket(SocketType.PUSH);
            this.ZMQ_PUSH_SOCKET.setImmediate(true);
            this.ZMQ_PUSH_SOCKET.setTCPKeepAlive(1);
            this.ZMQ_PUSH_SOCKET.setHWM(100);
            this.ZMQ_SUB_SOCKET = ctx.createSocket(SocketType.SUB);
            this.ZMQ_SUB_SOCKET.setImmediate(true);
            this.ZMQ_SUB_SOCKET.setTCPKeepAlive(1);
            this.ZMQ_SUB_SOCKET.setHWM(100);
        }
    }

    public String getSERVER_ADDRESS() {
        return SERVER_ADDRESS;
    }

    public ExecutorService getExecutor() {
        return executor;
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

    public SocketClient setDeleteConsumer(Consumer<String> deleteConsumer) {
        this.deleteConsumer = deleteConsumer;
        return this;
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
                socketExecutor.execute(new ClientMessageListener(socket));
                List<RequestAction<ResponseStatus>> requestActions = xTablesClient.resubscribeToAllUpdateEvents();
                if (!requestActions.isEmpty()) {
                    logger.info("Resubscribing to all previously submitted update events.");
                    xTablesClient.queueAll(requestActions);
                    logger.info("Queued " + requestActions.size() + " subscriptions successfully!");
                }
                if (xTablesClient.resubscribeToDeleteEvents()) {
                    logger.info("Subscribing to previously submitted delete event.");
                    new RequestAction<>(this, new ResponseInfo(null, MethodType.SUBSCRIBE_DELETE).parsed(), ResponseStatus.class).queue();
                    logger.info("Queued delete event subscription successfully!");
                }
                if(this.ZMQ_PUSH_SOCKET != null) {
                    try {
                        this.ZMQ_PUSH_SOCKET.connect("tcp://" + SERVER_ADDRESS + ":" + 1736);
                    } catch (Exception ignored) {}
                }
                if(this.ZMQ_SUB_SOCKET != null) {
                    try {
                        this.ZMQ_SUB_SOCKET.connect("tcp://" + SERVER_ADDRESS + ":" + 1737);
                    } catch (Exception ignored) {}
                }
                isConnected = true;
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
                1, TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(1000)
        );
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
        executor.setRejectedExecutionHandler((r, executor1) -> {
            logger.warning(String.format("Too many tasks in thread queue (%1$s). Clearing queue now...", executor1.getQueue().size()));
            executor1.getQueue().clear();
            if (!executor1.isShutdown()) {
                executor1.execute(r);
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
                    if (requestInfo.getTokens().length >= 3 && requestInfo.getMethod().equals(MethodType.UPDATE_EVENT)) {
                        String key = requestInfo.getTokens()[1];
                        String value = String.join(" ", Arrays.copyOfRange(requestInfo.getTokens(), 2, requestInfo.getTokens().length));
                        if (updateConsumer != null) {
                            KeyValuePair<String> keyValuePair = new KeyValuePair<>(key, value);
                            updateConsumer.accept(keyValuePair); // maybe implement a cachedThread instead of using the same executor as socket client
                            if (CLEAR_UPDATE_MESSAGES) MESSAGES.remove(requestInfo);
                        }
                    } else if (requestInfo.getTokens().length >= 2 && requestInfo.getMethod().equals(MethodType.DELETE_EVENT)) {
                        String key = requestInfo.getTokens()[1];
                        if (Utilities.validateKey(key, true)) {
                            if (deleteConsumer != null) {
                                executor.execute(() -> deleteConsumer.accept(key));
                            }
                        }
                    } else {
                        MESSAGES.add(requestInfo);
                    }
                }
                isConnected = false;
                if (!socket.isClosed()) {
                    logger.warning("Disconnected from the server. Reconnecting...");
                    try {
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
                try {
                    for (RequestInfo message : new ArrayList<>(MESSAGES)) {
                        if (message != null && message.getID().equals(ID)) {
                            MESSAGES.remove(message);
                            return message;
                        }
                    }
                } catch (Exception ignored) {}
            }
            return null;
        }


    public RequestInfo sendMessageAndWaitForReply(ResponseInfo responseInfo, long timeout, TimeUnit unit) throws InterruptedException {
        sendMessage(responseInfo);
        return waitForMessage(responseInfo.getID(), timeout, unit);
    }

    public void sendMessage(ResponseInfo responseInfo) {
        if(out != null) {
            out.println(responseInfo.parsed());
            out.flush();
        }
    }

    public void sendMessageRaw(String raw) {
        if(out != null) {
            out.println(raw);
            out.flush();
        }
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
        if(this.ctx != null) {
            this.ctx.destroy();
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

    public boolean isReady() {
        return isConnected && !socket.isClosed() && out != null && in != null;
    }



    public Socket getSocket() {
        return socket;
    }

    public ZMQ.Socket getZMQ_PUSH_SOCKET() {
        return ZMQ_PUSH_SOCKET;
    }
    public ZMQ.Socket getZMQ_SUB_SOCKET() {
        return ZMQ_SUB_SOCKET;
    }

    public boolean pushZMQ(String message) {
       return this.ZMQ_PUSH_SOCKET.send(message, ZMQ.DONTWAIT);
    }
    public String[] receive_nextZMQ() {
        return Utilities.tokenize(this.ZMQ_SUB_SOCKET.recvStr(), ' ', 2);
    }
    public CompletableFuture<String> sendAsync(String message, long timeoutMS) throws IOException {
        if (executor == null || executor.isShutdown())
            throw new IOException("The worker thread executor is shutdown and no new requests can be made.");
        CompletableFuture<String> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                RequestInfo requestInfo = sendMessageAndWaitForReply(ResponseInfo.from(message), timeoutMS, TimeUnit.MILLISECONDS);
                if (requestInfo == null) throw new RuntimeException("Nothing received back from server when sending: " + message);
                String[] tokens = requestInfo.getTokens();
                future.complete(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)));
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void sendExecute(String message) throws IOException {
        if (executor == null || executor.isShutdown())
            throw new IOException("The worker thread executor is shutdown and no new requests can be made.");

        sendMessage(ResponseInfo.from(message).setID("IGNORED"));

    }

    public String sendComplete(String message, long msTimeout) throws ExecutionException, InterruptedException, TimeoutException, IOException {
        return sendAsync(message, msTimeout).get(msTimeout, TimeUnit.MILLISECONDS);
    }



}

