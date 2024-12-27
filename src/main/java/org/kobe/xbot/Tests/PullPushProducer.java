package org.kobe.xbot.Tests;

import com.google.protobuf.ByteString;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class PullPushProducer {
    public static void main(String[] args) throws IOException, InterruptedException {
        XTablesClient client = new XTablesClient("localhost");
            byte[] imageBytes = Files.readAllBytes(Paths.get("ping_page.png"));
            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {
                i++;
                  client.putInteger("test", i);
                  System.out.println(i);
                  Thread.sleep(100);


            }


    }
}
