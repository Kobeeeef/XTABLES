package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.PingResponse;
import org.kobe.xbot.Utilities.XTablesByteUtils;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {

        XTablesClient client = new XTablesClient("localhost");
        client.subscribe("test", (xTableUpdate -> {
            System.out.println(XTablesByteUtils.toInt(xTableUpdate.getValue()));
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

//        while (true) {
//            try {
//                client.getBoolean("awd");
//
//            } catch (Exception ignored) {
//            }
//
//        }

        Thread.sleep(300000000);
    }
}

