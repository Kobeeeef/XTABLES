package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {
        XTablesClient xTablesClient = new XTablesClient();
        while (true) {
            xTablesClient.putDouble("test", 1d);
        }

//        Thread.sleep(100000000);


    }
}

