package org.kobe.xbot.Tests;

import com.google.protobuf.ByteString;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class PullPushProducer {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(ZMQ.PUSH);
            socket.setHWM(500);
            socket.connect("tcp://*:1735");
            System.out.println("Producer started. Sending image...");

            byte[] imageBytes = Files.readAllBytes(Paths.get("ping_page.png"));

            while (!Thread.currentThread().isInterrupted()) {

                    // Read the image file into a byte array

                    long id = ThreadLocalRandom.current().nextLong();
                    XTableProto.XTableMessage message = XTableProto.XTableMessage.newBuilder()
                            .setCommand(XTableProto.XTableMessage.Command.PUT)
                            .setKey("imageKey")
                            .setValue(ByteString.copyFrom(imageBytes)) // Send the image as bytes
                            .build();
                    // Send the message to the socket
                    socket.send(message.toByteArray(), ZMQ.DONTWAIT);
                    System.out.println("sent");

                Thread.sleep(1000);
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
