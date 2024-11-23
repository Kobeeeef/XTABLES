package org.kobe.xbot.Tests;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AsyncNIOServer {
    private final int port;
    private int messagesPerSecond = 0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AsyncNIOServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        System.out.println("AsyncNIO server started on port " + port);

        // Schedule a task to print messages per second
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Messages per second: " + messagesPerSecond);
            messagesPerSecond = 0; // Reset the counter every second
        }, 1, 1, TimeUnit.SECONDS);

        // Accept connections
        serverChannel.accept(null, new java.nio.channels.CompletionHandler<>() {


            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
                System.out.println("Accepted connection from " + getClientAddress(clientChannel));
                serverChannel.accept(null, this); // Accept the next connection
                handleClient(clientChannel); // Handle the current client
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                System.err.println("Failed to accept connection: " + exc.getMessage());
            }
        });

        // Keep the main thread alive to prevent the server from shutting down
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleClient(AsynchronousSocketChannel clientChannel) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        clientChannel.read(buffer, buffer, new java.nio.channels.CompletionHandler<>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead == -1) {
                    closeClient(clientChannel);
                    return;
                }

                buffer.flip();
                String message = new String(buffer.array(), 0, buffer.limit()).trim();
                messagesPerSecond++; // Increment the message counter
                buffer.clear();
                handleClient(clientChannel);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("Failed to read from client: " + exc.getMessage());
                closeClient(clientChannel);
            }
        });
    }

    private void closeClient(AsynchronousSocketChannel clientChannel) {
        try {
            System.out.println("Client disconnected: " + getClientAddress(clientChannel));
            clientChannel.close();
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }
    }

    private String getClientAddress(AsynchronousSocketChannel clientChannel) {
        try {
            return clientChannel.getRemoteAddress().toString();
        } catch (IOException e) {
            return "Unknown";
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        new AsyncNIOServer(8080).start();
    }
}
