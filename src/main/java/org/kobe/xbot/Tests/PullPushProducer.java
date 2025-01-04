package org.kobe.xbot.Tests;

import com.google.protobuf.ByteString;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.SystemStatistics;
import org.kobe.xbot.Utilities.XTablesByteUtils;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PullPushProducer {
    public static void main(String[] args) throws IOException, InterruptedException {
        XTablesClient client = new XTablesClient("localhost");
        System.out.println(Arrays.toString(XTableProto.XTableMessage.XTableUpdate.newBuilder()
                .setKey("test")
                .build()
                .toByteArray()));

            byte[] imageBytes = Files.readAllBytes(Paths.get("D:\\stuff\\IdeaProjects\\XTABLES\\src\\main\\resources\\static\\logo.png"));
            int i = 0;
        i++;
        byte[] valueBytes = ByteBuffer.allocate(4).putInt(i).array();
            while (!Thread.currentThread().isInterrupted()) {

                client.publish("h", new byte[]{});



            }


    }
}
