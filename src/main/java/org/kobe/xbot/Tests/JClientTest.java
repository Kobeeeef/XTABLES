package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.PingResponse;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {

        XTablesClient client = new XTablesClient("localhost");
//        client.subscribe("imageKey", (xTableUpdate -> {
//            System.out.println(xTableUpdate.getValue().toStringUtf8());
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }));

        while (true) {
            try {
                client.putBytes("awd", new byte[] {});

            } catch (Exception ignored) {}
        }
     //  Thread.sleep(300000000);

    }
}
