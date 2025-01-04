package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PullPushProducer {
    public static void main(String[] args) throws IOException, InterruptedException {
        XTablesClient client = new XTablesClient();
        Thread.sleep(1000);

        byte[] imageBytes = Files.readAllBytes(Paths.get("D:\\stuff\\IdeaProjects\\XTABLES\\src\\main\\resources\\static\\logo.png"));
        int i = 0;

        while (!Thread.currentThread().isInterrupted()) {

            client.putBytes("h", ByteBuffer.allocate(4).putInt(i).array());

        }


    }
}
