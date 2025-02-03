package org.kobe.xbot.Tests;


import org.kobe.xbot.JClient.XTableContext;
import org.kobe.xbot.JClient.XTablesClient;

import java.util.concurrent.ExecutionException;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        XTablesClient client = new XTablesClient();

        XTableContext context = client.registerNewThreadedContext("thread-1");
        XTableContext context2 = client.registerNewThreadedContext("thread-2");

        new Thread(() -> {
            while (true) {
                context.publish("test1", new byte[]{});
            }
        }).start();
        new Thread(() -> {
            while (true) {
                context2.publish("test2", new byte[]{});
            }
        }).start();

//       Thread.sleep(1000000);

    }
}

