package org.kobe.xbot.Utilities.Entities;

import com.google.protobuf.ByteString;
import org.kobe.xbot.JClient.Concurrency.ConcurrentPushHandler;
import org.kobe.xbot.JClient.Concurrency.ConcurrentRequestHandler;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.concurrent.TimeUnit;

public abstract class QueuedRequests extends Requests {
    private ConcurrentPushHandler pushHandler;
    private ConcurrentRequestHandler requestHandler;

    protected void setHandlers(ConcurrentPushHandler pushHandler, ConcurrentRequestHandler requestHandler) {
        this.pushHandler = pushHandler;
        this.requestHandler = requestHandler;
    }
    protected void setPushHandler(ConcurrentPushHandler pushHandler) {
        this.pushHandler = pushHandler;
    }

//    @Override
//    protected byte[] getRawBytes(byte[] data) {
//        try {
//            return  this.requestHandler.sendRequest(data)
//                    .get(3, TimeUnit.SECONDS);
//        } catch (Exception e) {
//            return null;
//        }
//    }


    @Override
    protected boolean sendPutMessage(String key, byte[] value, XTableProto.XTableMessage.Type type) {
        pushHandler.pushBuffer.write(XTableProto.XTableMessage.newBuilder()
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
        pushHandler.pushBuffer.write(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(XTableProto.XTableMessage.Command.PUBLISH)
                .setValue(ByteString.copyFrom(value))
                .build()
                .toByteArray());
        return true;
    }

    @Override
    public boolean sendBatchedPushRequests(BatchedPushRequests batchedPushRequests) {
        pushHandler.pushBuffer.write(XTableProto.XTableMessage.newBuilder()
                .setCommand(XTableProto.XTableMessage.Command.BATCH)
                .addAllBatch(batchedPushRequests.getData())
                .build()
                .toByteArray());
        return true;
    }
}
