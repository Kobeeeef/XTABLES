package org.kobe.xbot.Utilities.Entities;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;

public class BatchedPushRequests extends Requests {
    private final List<XTableProto.XTableMessage> data;

    public BatchedPushRequests() {
        this.data = new ArrayList<>();
    }

    public List<XTableProto.XTableMessage> getData() {
        return data;
    }

    /**
     * Publishes a message with a specified key and value to the push socket.
     * The message is sent with the "PUBLISH" command.
     *
     * @param key   The key associated with the message being published.
     * @param value The value (byte array) to be published with the key.
     * @return true if the message was successfully sent, false otherwise.
     */
    @Override
    public boolean publish(String key, byte[] value) {
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUBLISH)
                .setValue(ByteString.copyFrom(value))
                .build()
        );
    }

    /**
     * this method is blocked in this class
     */
    @Override
    public boolean sendBatchedPushRequests(BatchedPushRequests batchedPushRequests) {
        return false;
    }

    /**
     * Sends a message via the PUSH socket to the server.
     * <p>
     * This helper method builds an XTableMessage and sends it using the PUSH socket. The message contains the specified key, command, value, and type.
     *
     * @param key   The key associated with the value.
     * @param value The byte array representing the value to be sent.
     * @param type  The type of the value being sent (e.g., STRING, INT64, etc.).
     * @return True if the message was sent successfully; otherwise, false.
     */
    @Override
    protected boolean sendPutMessage(String key, byte[] value, XTableProto.XTableMessage.Type type) {
        return data.add(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(value))
                .setType(type)
                .build()
        );
    }
}
