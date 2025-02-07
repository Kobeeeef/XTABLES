package org.kobe.xbot.Tests;

import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.XTablesByteUtils;

public class PullPushConsumer {

    public static void main(String[] args) throws InterruptedException {
        XTablesClient client = new XTablesClient();
        client.putString("test","TEST");
    }
}
