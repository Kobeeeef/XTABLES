package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;

public class JClientTest {
    public static void main(String[] args) {
        XTablesClient client = new XTablesClient("localhost");
        client.executePutBoolean("tesat", false);

       System.out.println(client.executeGetBoolean("tesat"));
    }
}
