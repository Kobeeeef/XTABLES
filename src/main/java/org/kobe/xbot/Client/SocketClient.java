package org.kobe.xbot.Client;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class SocketClient {
    private final Logger logger = Logger.getLogger(SocketClient.class.getName());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String SERVER_ADDRESS;
    private final int SERVER_PORT;
    private final long RECONNECT_DELAY_MS;
    public Boolean isConnected = null;
    private PrintWriter out = null;
    private BufferedReader in = null;
    private Socket socket;

    public SocketClient(String SERVER_ADDRESS, int SERVER_PORT, long RECONNECT_DELAY_MS) {
        this.socket = null;
        this.SERVER_ADDRESS = SERVER_ADDRESS;
        this.SERVER_PORT = SERVER_PORT;
        this.RECONNECT_DELAY_MS = RECONNECT_DELAY_MS;
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
                executor.submit(this::auto_reconnect);
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

    private void auto_reconnect() {
        final long INITIAL_DELAY_MS = 1000;
        final long MAX_DELAY_MS = 120000;
        long delay = INITIAL_DELAY_MS;

        while (true) {
            if (!isConnected()) {
                logger.warning("Disconnected from the server. Reconnecting...");
                this.connect();
                delay = Math.min(delay * 2, MAX_DELAY_MS);
            } else {
                delay = INITIAL_DELAY_MS;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(delay);
            } catch (InterruptedException e) {
                logger.severe("Failed to connect to server. Exiting...");
                Thread.currentThread().interrupt();
            }
        }
    }


    public Socket getSocket() {
        return socket;
    }

    private boolean isConnected() {
        boolean serverResponded = false;
        try {
            out.println("PING");
            out.flush();
            String response = in.readLine();
            if (response.equals("ACTIVE")) serverResponded = true;
        } catch (IOException ignored) {}
        boolean connected = socket != null && !socket.isClosed() && socket.isConnected() && serverResponded;
        this.isConnected = connected;
        return connected;
    }


    public CompletableFuture<String> sendAsync(String message) {
        CompletableFuture<String> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                out.println(message);
                out.flush();
                String response = in.readLine();
                future.complete(response);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        }).start();
        return future;
    }

    public <T> CompletableFuture<T> sendAsync(String message, Type type) {
        CompletableFuture<T> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                out.println(message);
                out.flush();
                String response = in.readLine();
                if (type == null) {
                    future.complete((T) response);
                } else {
                    T parsed = new Gson().fromJson(response, type);
                    future.complete(parsed);
                }
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        }).start();
        return future;
    }


    public <T> T sendComplete(String message, Type type) {
        try {
            String response = sendAsync(message).get(5000, TimeUnit.SECONDS);
            if (type == null) {
                return (T) response;
            } else {
                return new Gson().fromJson(response, type);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return null;
        }
    }


}

