package org.kobe.xbot.JServer;


import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.*;
import org.kobe.xbot.Utilities.Entities.XTableClientStatistics;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.zeromq.ZMQ;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ReplyRequestHandler - A handler for processing reply requests using JeroMQ.
 * <p>
 * This class processes incoming reply requests on a JeroMQ socket. It runs in its own thread
 * and parses messages using Protocol Buffers. If the message contains a valid command, further
 * processing can be implemented inside the `run` method. The thread is set as a daemon, meaning
 * it won't block the JVM from exiting.
 * <p>
 * Author: Kobe Lei
 * Package: XTABLES
 * Version: 1.0
 * <p>
 * This class is part of the XTABLES project and is used for handling server-side reply requests
 * in a multithreaded environment with JeroMQ.
 */
public class ReplyRequestHandler extends BaseHandler {
    private static final Gson gson = new Gson();
    private final XTablesServer instance;
    private final byte[] success = new byte[]{(byte) 0x01};
    private final byte[] fail = new byte[]{(byte) 0x00};
    private final ByteString successByte = ByteString.copyFrom(success);
    private final ByteString failByte = ByteString.copyFrom(fail);

    /**
     * Constructor that initializes the handler with the provided socket and server instance.
     *
     * @param socket   The ZeroMQ socket to receive reply requests on
     * @param instance The XTablesServer instance
     */
    public ReplyRequestHandler(ZMQ.Socket socket, XTablesServer instance) {
        super("XTABLES-REPLY-REQUEST-HANDLER-DAEMON", true, socket);
        this.instance = instance;
    }

    /**
     * The main method for handling incoming reply requests.
     * It continuously receives messages from the JeroMQ socket and processes them.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] bytes = socket.recv();
                instance.replyMessages.incrementAndGet();
                try {

                    XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(bytes);
                    XTableProto.XTableMessage.Command command = message.getCommand();
                    switch (command) {
                        case GET -> {
                            if (message.hasKey()) {
                                String key = message.getKey();
                                Map.Entry<byte[], XTableProto.XTableMessage.Type> response = XTablesServer.table.getWithType(key);
                                if (response != null)
                                    socket.send(XTableProto.XTableMessage.newBuilder()
                                            .setKey(key)
                                            .setValue(ByteString.copyFrom(response.getKey()))
                                            .setType(response.getValue())
                                            .build()
                                            .toByteArray(), ZMQ.DONTWAIT);
                                else socket.send(XTableProto.XTableMessage.newBuilder()
                                        .setKey(key)
                                        .build()
                                        .toByteArray(), ZMQ.DONTWAIT);
                            }
                        }
                        case GET_RAW_JSON -> socket.send(XTableProto.XTableMessage.newBuilder()
                                .setCommand(command)
                                .setValue(ByteString.copyFrom(DataCompression.compressAndConvertBase64(XTablesServer.table.toJSON()).getBytes(StandardCharsets.UTF_8)))
                                .build().toByteArray(), ZMQ.DONTWAIT);
                        case REBOOT_SERVER -> {
                            socket.send(XTableProto.XTableMessage.newBuilder()
                                    .setValue(successByte)
                                    .build()
                                    .toByteArray(), ZMQ.DONTWAIT);
                            instance.restart();
                        }
                        case GET_TABLES -> {
                            Set<String> tables;
                            XTableProto.XTableMessage.Builder builder = XTableProto.XTableMessage.newBuilder()
                                    .setCommand(command);
                            if (message.hasKey()) {
                                tables = XTablesServer.table.getTables(message.getKey());

                                byte[] resp = Utilities.toByteArray((List<?>) tables);
                                if (tables != null && !tables.isEmpty() && resp != null) {
                                    builder.setValue(ByteString.copyFrom(resp));
                                }
                            } else {
                                tables = XTablesServer.table.getTables("");
                                if (tables != null && !tables.isEmpty()) {
                                    List<String> tablesList = new ArrayList<>(tables);

                                    byte[] resp = Utilities.toByteArray(tablesList);
                                    if (resp != null) {
                                        builder.setValue(ByteString.copyFrom(resp));
                                    }
                                }
                            }
                            socket.send(builder.build().toByteArray(), ZMQ.DONTWAIT);

                        }
                        case DELETE -> {
                            if (message.hasKey()) {
                                String key = message.getKey();
                                boolean response = XTablesServer.table.delete(key);

                                socket.send(XTableProto.XTableMessage.newBuilder()
                                        .setCommand(command)
                                        .setValue(response ? successByte : failByte)
                                        .build()
                                        .toByteArray(), ZMQ.DONTWAIT);

                                if (response) {
                                    instance.notifyUpdateClients(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                                            .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.DELETE)
                                            .setKey(key)
                                            .build()
                                    );
                                }
                            } else {
                                boolean response = XTablesServer.table.delete("");

                                socket.send(XTableProto.XTableMessage.newBuilder()
                                        .setCommand(command)
                                        .setValue(response ? successByte : failByte)
                                        .build()
                                        .toByteArray(), ZMQ.DONTWAIT);

                                if (response) {
                                    instance.notifyUpdateClients(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                                            .setCategory(XTableProto.XTableMessage.XTableUpdate.Category.DELETE)
                                            .build()
                                    );
                                }
                            }
                        }
                        case INFORMATION -> {
                            SystemStatistics systemStatistics = new SystemStatistics(instance);
                            systemStatistics.setClientDataList(((List<XTableClientStatistics.ClientStatistics>) instance.getClientRegistry().getClients().clone()).stream().map(m -> {
                                ClientData data = new ClientData(m.getIp(),
                                        m.getHostname(),
                                        m.getUuid());
                                data.setStats(gson.toJson(ClientStatistics.fromProtobuf(m)));
                                return data;
                            }).collect(Collectors.toList()));
                            try {
                                systemStatistics.setHostname(InetAddress.getLocalHost().getHostName());
                            } catch (Exception ignored) {
                            }
                            byte[] serialized = XTablesByteUtils.fromObject(systemStatistics);
                            socket.send(XTableProto.XTableMessage.newBuilder()
                                    .setCommand(command)
                                    .setValue(ByteString.copyFrom(serialized))
                                    .build().toByteArray(), ZMQ.DONTWAIT);
                        }
                        case DEBUG -> {
                            if (message.hasValue()) {
                                instance.setDebug(message.getValue().equals(successByte));
                                socket.send(XTableProto.XTableMessage.newBuilder()
                                        .setValue(successByte)
                                        .build()
                                        .toByteArray(), ZMQ.DONTWAIT);
                            } else {
                                socket.send(XTableProto.XTableMessage.newBuilder()
                                        .setValue(failByte)
                                        .build()
                                        .toByteArray(), ZMQ.DONTWAIT);
                            }
                        }
                        case PING -> socket.send(XTableProto.XTableMessage.newBuilder()
                                .setValue(successByte)
                                .build()
                                .toByteArray(), ZMQ.DONTWAIT);
                        default -> {
                            logger.warning("Unhandled reply command: " + command);
                            socket.send(XTableProto.XTableMessage.newBuilder()
                                    .setCommand(XTableProto.XTableMessage.Command.UNKNOWN_COMMAND)
                                    .setValue(failByte).build().toByteArray(), ZMQ.DONTWAIT);
                        }
                    }
                } catch (Exception e) {
                    handleException(e);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

}
