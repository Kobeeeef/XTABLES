package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.XTablesByteUtils;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {

        XTablesClient client = new XTablesClient();
        client.subscribe("test", (xTableUpdate -> {
            System.out.println(XTablesByteUtils.toInt(xTableUpdate.getValue()));
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
        client.subscribeToServerLogs((v) -> {
            System.out.println(v.getMessage());
            System.out.println(v.getLevel());
        });
        System.out.println(client.ping());
        client.setServerDebug(true);
        Thread.sleep(3000);
        System.out.println(client.ping());
        System.out.println(client.ping());
        System.out.println(client.ping());
        System.out.println(client.ping());

        while (true) {
            try {
                client.getBoolean("awd");
                Thread.sleep(300);
            } catch (Exception ignored) {
            }

        }

//        Thread.sleep(300000000);
    }
}

