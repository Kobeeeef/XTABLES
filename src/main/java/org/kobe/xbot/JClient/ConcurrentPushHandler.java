package org.kobe.xbot.JClient;

import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.CircularBuffer;
import org.kobe.xbot.Utilities.ClientStatistics;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Exceptions.XTablesException;
import org.zeromq.ZMQ;

import java.util.List;
import java.util.function.Consumer;

/**
 * SubscribeHandler - A handler for processing incoming subscription messages using JeroMQ.
 * <p>
 * This class handles the reception of messages on a ZeroMQ socket, processes them, and stores the updates in a circular buffer.
 * It also manages a separate consumer thread that handles the subscription consumers and invokes their functions for each new update.
 * The handler supports both UPDATE and PUBLISH message categories.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: org.kobe.xbot.JClient
 * <p>
 * This is part of the XTABLES project and facilitates subscribing and handling incoming messages from the server.
 */
public class ConcurrentPushHandler extends BaseHandler {
    private final ConcurrentXTablesClient instance;
    /**
     * Constructor that initializes the handler with the provided socket and server instance.
     *
     * @param socket   The ZeroMQ socket to receive messages on
     * @param instance The XTablesClient instance
     */
    public ConcurrentPushHandler(ZMQ.Socket socket, ConcurrentXTablesClient instance) {
        super("XTABLES-PUSH-HANDLER-DAEMON", true, socket);
        this.instance = instance;
    }


    /**
     * The main method for handling incoming messages.
     * <p>
     * It continuously receives messages from the JeroMQ socket,
     * processes them by parsing the message into a protocol buffer,
     * and stores the updates in the buffer.
     * It also increments the count of subscription messages received.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] message = instance.pushBuffer.readAndBlock();
                if (message != null) {
                    socket.send(message, ZMQ.DONTWAIT);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Logs exceptions and handles cleanup when the thread is interrupted.
     * <p>
     * This method interrupts the consumer handling thread and calls the parent class's interrupt method to clean up.
     */
    @Override
    public void interrupt() {
        super.interrupt();
    }
}
