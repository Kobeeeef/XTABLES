package org.kobe.xbot.JServer;

import org.zeromq.ZMQ;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class XTablesMessageQueue extends BaseHandler {
    private final BlockingQueue<byte[]> messageQueue;

    /**
     * Constructor that initializes the handler with the provided socket and server instance.
     *
     * @param socket   The ZeroMQ socket to receive messages on
     */
    public XTablesMessageQueue(ZMQ.Socket socket) {
        super("XTABLES-PUBLISH-HANDLER-DAEMON", true, socket);
        this.messageQueue = new LinkedBlockingQueue<>(1000);
    }

    public void send(byte[] message) {
         messageQueue.offer(message);
    }

    /**
     * The main method for handling incoming messages.
     * It continuously receives messages from the JeroMQ socket and processes them.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] message = messageQueue.take();
                socket.send(message, ZMQ.DONTWAIT);
            }
        } catch (Exception e) {
            handleException(e);
        }
    }


}
