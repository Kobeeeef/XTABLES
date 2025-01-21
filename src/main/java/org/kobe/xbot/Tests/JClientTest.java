package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.XTablesByteUtils;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("OK2");
        XTablesClient client = new XTablesClient();


        client.subscribe((a) -> {
            System.out.println(a);
        });

Thread.sleep(300000000);
    }
}

