package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.Entities.BatchedPushRequests;
import org.kobe.xbot.Utilities.XTablesByteUtils;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("OK2");
        XTablesClient client = new XTablesClient();

        BatchedPushRequests batchedPushRequests = new BatchedPushRequests();

        batchedPushRequests.putBoolean("test", false);
        batchedPushRequests.putBoolean("test2", true);
        batchedPushRequests.putBoolean("test3", true);
        batchedPushRequests.putBoolean("test4", true);
        batchedPushRequests.putBoolean("test5", true);
        batchedPushRequests.putBoolean("test6", false);
        batchedPushRequests.putString("test7", "AHHA");
        batchedPushRequests.putString("test8", "AHHA2");
        batchedPushRequests.putString("test9", "AHHA3");

        client.sendBatchedPushRequests(batchedPushRequests);

    }
}

