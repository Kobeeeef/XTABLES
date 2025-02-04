package org.kobe.xbot.JClient.Concurrency;

import org.kobe.xbot.JClient.BaseHandler;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.CircularBuffer;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ConcurrentRequestHandler - A handler for processing outgoing requests using JeroMQ.
 * <p>
 * This class manages the sending of requests through a ZeroMQ REQ socket, ensuring thread-safe queuing
 * and sequential processing. It utilizes a circular buffer for message storage and handles responses
 * efficiently using futures.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: org.kobe.xbot.JClient
 * <p>
 * This is part of the XTABLES project and facilitates reliable request handling with ZeroMQ.
 */
public class ConcurrentRequestHandler extends BaseHandler {
    private final CircularBuffer<RequestTask> requestBuffer;
    private final AtomicLong requestCounter;
    private final XTablesClient client;
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private int attempt = 1;
    /**
     * Represents a request task containing the request bytes and its corresponding future.
     */
    private static class RequestTask {
        final byte[] requestData;
        final CompletableFuture<byte[]> future;
        final long requestId;

        RequestTask(byte[] requestData, CompletableFuture<byte[]> future, long requestId) {
            this.requestData = requestData;
            this.future = future;
            this.requestId = requestId;
        }
    }

    /**
     * Constructor that initializes the handler with the provided socket.
     *
     * @param socket The ZeroMQ REQ socket for sending requests
     */
    public ConcurrentRequestHandler(ZMQ.Socket socket, XTablesClient client) {
        super("XTABLES-REQUEST-HANDLER-DAEMON", true, socket);
        this.requestBuffer = new CircularBuffer<>(250);
        this.requestCounter = new AtomicLong(0);
        this.client = client;
    }

    /**
     * The main method for handling outgoing requests.
     * <p>
     * It continuously reads messages from the circular buffer, sends them to the server via the ZeroMQ REQ socket,
     * and waits for a corresponding response. The response is directly mapped to the `CompletableFuture` stored
     * inside `RequestTask`.
     */
    @Override
    public void run() {
        try {
            ZMQ.Poller poller = client.getContext().createPoller(1);
            poller.register(socket, ZMQ.Poller.POLLIN);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    RequestTask task = this.requestBuffer.readAndBlock();
                    if (task != null) {
                        byte[] requestIdBytes = longToBytes(task.requestId);

                        // Send request to the server
                        socket.sendMore(requestIdBytes); // First frame: Request ID
                        socket.send(task.requestData, 0); // Second frame: Actual request data

                        // Wait for response with timeout
                        if (poller.poll(3000) > 0) {
                            if (poller.pollin(0)) {
                                byte[] replyIdBytes = socket.recv(0); // First frame: Response ID
                                byte[] response = socket.recv(0); // Second frame: Response data

                                long replyId = bytesToLong(replyIdBytes);

                                // Ensure the received response corresponds to the sent request
                                if (replyId == task.requestId) {
                                    task.future.complete(response);
                                } else {
                                    task.future.completeExceptionally(new IllegalStateException("Mismatched request ID"));
                                }
                            }
                        } else {
                            // No response received within timeout, trigger reconnect
                            throw new RuntimeException("Request timed out, forcing socket reset");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warning("Reconnecting... attempt " + attempt);
                    Thread.sleep(Math.min(200 * attempt, 5000)); // Exponential backoff (max 5s)
                    attempt++;
                    reconnect();
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
    }


    private void reconnect() {
        this.socket.close();
        this.client.getSocketMonitor().removeSocket("REQUEST");
        this.socket = client.getContext().createSocket(SocketType.REQ);
        this.socket.setHWM(500);
        this.socket.setReconnectIVL(500);
        this.socket.setReconnectIVLMax(1000);
        this.socket.setReceiveTimeOut(3000);
        client.getSocketMonitor().addSocket("REQUEST", this.socket);
        this.socket.connect("tcp://" + this.client.getIp() + ":" + this.client.getRequestSocketPort());
    }
    /**
     * Sends a request asynchronously and returns a CompletableFuture for the response.
     *
     * @param request The request message to send
     * @return A CompletableFuture that resolves when the response is received
     */
    public CompletableFuture<byte[]> sendRequest(byte[] request) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        long requestId = requestCounter.incrementAndGet();
        RequestTask task = new RequestTask(request, future, requestId);
        this.requestBuffer.write(task);
        return future;
    }

    /**
     * Handles cleanup when the thread is interrupted.
     * <p>
     * This method interrupts the worker thread and ensures that any pending futures are completed exceptionally.
     */
    @Override
    public void interrupt() {
        super.interrupt();
        while (!requestBuffer.isEmpty()) {
            RequestTask task = requestBuffer.read();
            if (task != null) {
                task.future.completeExceptionally(new InterruptedException("Request handler interrupted"));
            }
        }
    }

    /**
     * Converts a long value to a byte array.
     *
     * @param value The long value to convert
     * @return A byte array representing the long value
     */
    private byte[] longToBytes(long value) {
        byte[] buffer = new byte[8];
        for (int i = 7; i >= 0; i--) {
            buffer[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return buffer;
    }

    /**
     * Converts a byte array to a long value.
     *
     * @param bytes The byte array to convert
     * @return The long value
     */
    private long bytesToLong(byte[] bytes) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[i] & 0xFF);
        }
        return value;
    }
}
