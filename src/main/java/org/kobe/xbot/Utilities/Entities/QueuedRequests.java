package org.kobe.xbot.Utilities.Entities;

import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.CircularBuffer;

public abstract class QueuedRequests extends Requests {
    public CircularBuffer<byte[]> pushBuffer;

    protected void setBuffer(CircularBuffer<byte[]> pushBuffer) {
        this.pushBuffer = pushBuffer;
    }

    @Override
    protected boolean sendPutMessage(String key, byte[] value, XTableProto.XTableMessage.Type type) {
        pushBuffer.write(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUT)
                .setValue(ByteString.copyFrom(value))
                .setType(type)
                .build()
                .toByteArray());
        return true;
    }

    @Override
    public boolean publish(String key, byte[] value) {
        pushBuffer.write(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUBLISH)
                .setValue(ByteString.copyFrom(value))
                .build()
                .toByteArray());
        return true;
    }

    @Override
    public boolean sendBatchedPushRequests(BatchedPushRequests batchedPushRequests) {
        pushBuffer.write(XTableProto.XTableMessage.newBuilder()
                .setCommand(XTableProto.XTableMessage.Command.BATCH)
                .addAllBatch(batchedPushRequests.getData())
                .build()
                .toByteArray());
        return true;
    }
}
