package org.kobe.xbot.Tests;


import org.kobe.xbot.JClient.XTableContext;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.BatchedPushRequests;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.concurrent.ExecutionException;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        XTablesClient client = new XTablesClient();
        BatchedPushRequests pushRequests = new BatchedPushRequests();
        pushRequests.putInteger("a", 3);
        pushRequests.putInteger("b", 4);
        pushRequests.putInteger("c", 5);
        pushRequests.putInteger("4", 5);
        pushRequests.putInteger("vg", 5);
        new Thread(() -> {
            while (true) {
               client.putBytes("a", new byte[]{});
            }
        }).start();


       Thread.sleep(1000000);

    }
}

