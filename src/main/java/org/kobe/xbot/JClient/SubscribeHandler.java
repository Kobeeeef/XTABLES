package org.kobe.xbot.JClient;

import org.kobe.xbot.Utilities.CircularBuffer;
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
public class SubscribeHandler extends BaseHandler {
    private final XTablesClient instance;
    private final CircularBuffer<XTableProto.XTableMessage.XTableUpdate> buffer;
    private final Thread consumerHandlingThread;

    /**
     * Constructor that initializes the handler with the provided socket and server instance.
     *
     * @param socket   The ZeroMQ socket to receive messages on
     * @param instance The XTablesClient instance
     */
    public SubscribeHandler(ZMQ.Socket socket, XTablesClient instance) {
        super("XTABLES-SUBSCRIBE-HANDLER-DAEMON", true, socket);
        this.instance = instance;
        this.buffer = new CircularBuffer<>(250, (latest, current) -> {
            return current.getKey().equals(latest.getKey());
        });
        this.consumerHandlingThread = new ConsumerHandlingThread();
        this.consumerHandlingThread.start();
    }


    /**
     * The main method for handling incoming messages.
     * It continuously receives messages from the JeroMQ socket and processes them.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] bytes = socket.recv();
                instance.subscribeMessagesCount.incrementAndGet();
                try {
                    XTableProto.XTableMessage.XTableUpdate message = XTableProto.XTableMessage.XTableUpdate.parseFrom(bytes);
                    this.buffer.write(message);
                } catch (Exception e) {
                    handleException(e);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    private class ConsumerHandlingThread extends Thread {
        public ConsumerHandlingThread() {
            setName("XTABLES-CONSUMER-HANDLER-DAEMON");
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    XTableProto.XTableMessage.XTableUpdate update = buffer.readLatestAndClearOnFunction();
                    if (update.getCategory().equals(XTableProto.XTableMessage.XTableUpdate.Category.UPDATE) || update.getCategory().equals(XTableProto.XTableMessage.XTableUpdate.Category.PUBLISH)) {
                        if (instance.subscriptionConsumers.containsKey(update.getKey())) {
                            List<Consumer<XTableProto.XTableMessage.XTableUpdate>> consumers = instance.subscriptionConsumers.get(update.getKey());
                            for (Consumer<XTableProto.XTableMessage.XTableUpdate> consumer : consumers) {
                                consumer.accept(update);
                            }
                        }
                        if (instance.subscriptionConsumers.containsKey("")) {
                            List<Consumer<XTableProto.XTableMessage.XTableUpdate>> consumers = instance.subscriptionConsumers.get("");
                            for (Consumer<XTableProto.XTableMessage.XTableUpdate> consumer : consumers) {
                                consumer.accept(update);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                handleException(new XTablesException(e));
            }
        }
    }

    /**
     * Logs exceptions and handles cleanup when the thread is interrupted.
     */
    @Override
    public void interrupt() {
        this.consumerHandlingThread.interrupt();
        super.interrupt();
    }
}
