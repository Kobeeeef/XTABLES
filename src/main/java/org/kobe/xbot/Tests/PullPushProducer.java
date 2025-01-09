package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class PullPushProducer {
    public static void main(String[] args) throws IOException, InterruptedException {
        XTablesClient client = new XTablesClient("localhost");
        Thread.sleep(1000);

        // byte[] imageBytes = Files.readAllBytes(Paths.get("D:\\stuff\\IdeaProjects\\XTABLES\\src\\main\\resources\\static\\logo.png"));
        int i = 0;
        List<Integer> l = new ArrayList<>();
        l.add(1);
        l.add(2);

        System.out.println(Arrays.toString(client.getList("test.ok", String.class)));

    }
}
