package org.kobe.xbot.Tests;

import com.google.protobuf.InvalidProtocolBufferException;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class PullPushConsumer {

    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.SUB);
            socket.setHWM(500);
            socket.connect("tcp://localhost:1737");
            System.out.println("Consumer connected. Receiving messages...");
            socket.subscribe(XTableProto.XTableMessage.XTableUpdate.newBuilder().setKey("imageKey").build().toByteArray());
            while (!Thread.currentThread().isInterrupted()) {
                byte[] msg = socket.recv();
                XTableProto.XTableMessage.XTableUpdate update = XTableProto.XTableMessage.XTableUpdate.parseFrom(msg);
                System.out.println(update);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
