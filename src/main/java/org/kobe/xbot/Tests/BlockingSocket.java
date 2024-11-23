package org.kobe.xbot.Tests;

import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BlockingSocket {
    private final int port;
    private int messagesPerSecond = 0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public BlockingSocket(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Blocking server started on port " + port);

        // Schedule a task to print messages per second
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Messages per second: " + messagesPerSecond);
            messagesPerSecond = 0; // Reset the counter every second
        }, 1, 1, TimeUnit.SECONDS);

        // Accept client connections and handle them in separate threads
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Accepted connection from " + clientSocket.getInetAddress());
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            String message;
            while ((message = in.readLine()) != null) {
                messagesPerSecond++; // Increment the message counter
                // Echo the message back to the client (optional)
//                out.println("Echo: " + message);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        new BlockingSocket(8080).start();
    }
}
