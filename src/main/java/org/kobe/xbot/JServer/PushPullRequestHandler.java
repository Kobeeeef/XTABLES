package org.kobe.xbot.JServer;

import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.Entities.XTableClientStatistics;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.zeromq.ZMQ;

/**
 * PushPullRequestHandler - A handler for processing push-pull messages using JeroMQ.
 * <p>
 * This class handles incoming push-pull requests on a JeroMQ socket. It runs in its own thread
 * and parses messages using Protocol Buffers. If the message is valid, further processing can be
 * implemented inside the `run` method. The thread is set as a daemon, meaning it won't block the
 * JVM from exiting.
 * <p>
 * Author: Kobe Lei
 * Package: XTABLES
 * Version: 1.0
 * <p>
 * This class is part of the XTABLES project and is used for handling server-side push-pull messaging
 * in a multithreaded environment with JeroMQ.
 */
public class PushPullRequestHandler extends BaseHandler {
    private final XTablesServer instance;

    /**
     * Constructor that initializes the handler with the provided socket and server instance.
     *
     * @param socket   The ZeroMQ socket to receive messages on
     * @param instance The XTablesServer instance
     */
    public PushPullRequestHandler(ZMQ.Socket socket, XTablesServer instance) {
        super("XTABLES-PUSH-PULL-HANDLER-DAEMON", true, socket);
        this.instance = instance;
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
                instance.pullMessages.incrementAndGet();
                try {
                    XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(bytes);
                    XTableProto.XTableMessage.Command command = message.getCommand();
                    if (command.equals(XTableProto.XTableMessage.Command.BATCH)) {
                        for (XTableProto.XTableMessage msg : message.getBatchList()) {
                            processMessage(msg, msg.getCommand());
                        }
                    } else processMessage(message, command);

                } catch (Exception e) {
                    handleException(e);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Processes a received message by handling its command.
     *
     * @param message The received XTableMessage
     */
    private void processMessage(XTableProto.XTableMessage message, XTableProto.XTableMessage.Command command) {

        switch (command) {
            case PUT -> {
                if (message.hasKey() && message.hasValue()) {
                    String key = message.getKey();
                    byte[] value = message.getValue().toByteArray();
                    if (XTablesServer.table.put(key, value, message.getType())) {
                        instance.publishQueue.send(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                                .setType(message.getType())
                                .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.UPDATE)
                                .setKey(key)
                                .setValue(ByteString.copyFrom(value))
                                .build().toByteArray()
                        );
                    }
                }
            }
            case PUBLISH -> {
                if (message.hasKey() && message.hasValue()) {
                    instance.publishQueue.send(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                            .setKey(message.getKey())
                            .setType(message.getType())
                            .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.PUBLISH)
                            .setValue(ByteString.copyFrom(message.getValue().toByteArray()))
                            .build().toByteArray()
                    );

                }
            }
            case REGISTRY -> {
                if (message.hasId()) {
                    if (message.getId().equals(instance.getClientRegistrySessionId())) {
                        if (message.hasValue()) {
                            byte[] value = message.getValue().toByteArray();
                            try {
                                XTableClientStatistics.ClientStatistics clientStatistics = XTableClientStatistics.ClientStatistics.parseFrom(value);
                                if (instance.getClientRegistry() != null)
                                    instance.getClientRegistry().getClients()
                                            .add(clientStatistics);
                            } catch (Exception e) {
                                logger.warning("Failed to parse client statistics: " + e.getMessage());
                            }
                        }
                    }
                }
            }
            case INFORMATION -> {
                if (message.hasId() && instance.getClientRegistry() != null) {
                    if (message.getId().equals(instance.getClientRegistry().getSessionId())) {
                        if (message.hasValue()) {
                            byte[] value = message.getValue().toByteArray();
                            try {
                                XTableClientStatistics.ClientStatistics clientStatistics = XTableClientStatistics.ClientStatistics.parseFrom(value);
                                instance.getClientRegistry().getClients().replaceAll(client -> {
                                    if (client.getUuid().equals(clientStatistics.getUuid())) {
                                        return clientStatistics;
                                    }
                                    return client;
                                });
                            } catch (Exception e) {
                                logger.warning("Failed to parse client statistics: " + e.getMessage());
                            }

                        }
                    }
                }
            }
            default -> logger.warning("Unhandled pull command: " + command);
        }
    }
}
