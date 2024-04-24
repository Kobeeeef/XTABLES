package org.kobe.xbot.Client;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SocketClient {
    private final String SERVER_ADDRESS;
    private final int SERVER_PORT;
    private final long RECONNECT_DELAY_MS;
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
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                if (!socket.isConnected()) throw new IOException();
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                System.out.println("Connected to server");
                break;
            } catch (IOException e) {
                System.err.println("Failed to connect to server. Retrying...");
                try {
                    // Wait before attempting reconnection
                    TimeUnit.MILLISECONDS.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isConnected() {
        return this.socket.isConnected();
    }


    public CompletableFuture<String> sendAsync(String message) {
        CompletableFuture<String> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                out.println(message);
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

    public void disconnect() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                System.out.println("Disconnected from server");
            } catch (IOException e) {
                System.err.println("Error occurred: " + e.getMessage());
            }
        }
    }

}

