package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.PingResponse;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {
        XTablesClient client = new XTablesClient("localhost");

        client.subscribe("imageKey", (t) -> {
            System.out.println(t);

        });

        PingResponse response = client.ping();
        System.out.println(response);


        System.out.println(client.getTables());

        System.out.println(client.delete());
        Thread.sleep(300000000);

    }
}
