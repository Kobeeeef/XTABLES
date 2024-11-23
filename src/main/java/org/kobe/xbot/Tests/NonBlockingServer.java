package org.kobe.xbot.Tests;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NonBlockingServer {
    private final Selector selector;
    private final ServerSocketChannel serverChannel;

    public NonBlockingServer(int port) throws IOException {
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started on port " + port);
    }

    public void start() throws IOException {
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Messages per second: " + i);
            i = 0; // Reset the counter every second
        }, 1, 1, TimeUnit.SECONDS);
        while (true) {
            selector.select(); // Wait for events
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    acceptConnection();
                } else if (key.isReadable()) {
                    handleRead(key);
                } else if (key.isWritable()) {
                    handleWrite(key);
                }
            }
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(1024));
        System.out.println("Accepted connection from " + clientChannel.getRemoteAddress());
    }
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    int i = 0;
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        int bytesRead;
        try {
            bytesRead = clientChannel.read(buffer);
        } catch (IOException e) {
            clientChannel.close();
            System.out.println("Client disconnected");
            return;
        }
        if (bytesRead == -1) {
            clientChannel.close();
            System.out.println("Client disconnected");
            return;
        }

        buffer.flip();
        String message = new String(buffer.array(), 0, buffer.limit()).trim();
        i++;

        buffer.clear();
        // clientChannel.register(selector, SelectionKey.OP_WRITE, ByteBuffer.wrap(("Echo: " + message).getBytes()));
    }

    private void handleWrite(SelectionKey key) {
        try (SocketChannel clientChannel = (SocketChannel) key.channel()) {
            ByteBuffer buffer = (ByteBuffer) key.attachment();
            clientChannel.write(buffer);
            if (!buffer.hasRemaining()) {
                buffer.clear();
            }
        } catch (IOException e) {
            System.err.println("Error handling write: " + e.getMessage());
            key.cancel();
        }

    }

    public static void main(String[] args) throws IOException {
        new NonBlockingServer(8080).start();
    }
}

