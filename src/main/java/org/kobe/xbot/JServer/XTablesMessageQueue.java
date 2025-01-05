package org.kobe.xbot.JServer;

import org.kobe.xbot.Utilities.CircularBuffer;
import org.zeromq.ZMQ;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * XTablesMessageQueue - A handler class for managing JeroMQ-based messaging queues.
 * <p>
 * This class initializes and manages the components required for handling message queues
 * using JeroMQ.
 * It leverages a blocking queue for message buffering and operates in a
 * multithreaded environment.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: org.kobe.xbot.JServer
 * <p>
 * This is part of the XTABLES project and facilitates efficient message processing
 * and publication in real-time applications.
 */
public class XTablesMessageQueue extends BaseHandler {
    private final CircularBuffer<byte[]> messageQueue;
    private final XTablesServer instance;

    /**
     * Constructor for initializing the XTablesMessageQueue.
     * <p>
     * This constructor sets up the handler thread with a specified name, daemon mode, and the
     * provided JeroMQ socket. It also initializes a blocking queue to buffer messages
     * for asynchronous processing.
     *
     * @param socket The ZeroMQ socket to receive and send messages
     */
    public XTablesMessageQueue(ZMQ.Socket socket, XTablesServer instance) {
        super("XTABLES-PUBLISH-HANDLER-DAEMON", true, socket);
        this.messageQueue = new CircularBuffer<>(1000);
        this.instance = instance;
    }

    /**
     * Adds a message to the queue for asynchronous processing.
     * <p>
     * This method places the provided message into the blocking queue.
     * If the queue is full, the message will be rejected silently.
     *
     * @param message The byte array message to enqueue for processing
     */
    public void send(byte[] message) {
       messageQueue.write(message);
    }

    /**
     * The main processing loop for handling incoming messages.
     * <p>
     * This method continuously retrieves messages from the blocking queue and sends them
     * through the JeroMQ socket.
     * The loop runs until the thread is interrupted.
     * Any exception encountered is passed to the handler's exception processor.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] message = messageQueue.readAndBlock();
                instance.publishMessages.incrementAndGet();
                socket.send(message, ZMQ.DONTWAIT);
            }
        } catch (Exception e) {
            handleException(e);
        }
    }


}
