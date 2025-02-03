package org.kobe.xbot.Tests;


import org.kobe.xbot.JClient.XTableContext;
import org.kobe.xbot.JClient.XTablesClient;
import org.kobe.xbot.Utilities.XTablesByteUtils;

import java.util.concurrent.ExecutionException;

public class JClientTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        XTablesClient client = new XTablesClient();

        XTableContext context = client.registerNewThreadedContext("thread-1");
        XTableContext context2 = client.registerNewThreadedContext("thread-2");
        XTableContext context3 = client.registerNewThreadedContext("thread-3");

        XTableContext context4 = client.registerNewThreadedContext("thread-4");
        XTableContext context5 = client.registerNewThreadedContext("thread-5");
        XTableContext context6 = client.registerNewThreadedContext("thread-6");
        new Thread(() -> {
            while (true) {
                context.putDouble("test1", Math.random());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
        new Thread(() -> {
            while (true) {
                context2.putDouble("test1", Math.random());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
        new Thread(() -> {
            while (true) {
                context3.putDouble("test1", Math.random());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
        new Thread(() -> {
            while (true) {
                context4.putDouble("test1", Math.random());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
        new Thread(() -> {
            while (true) {
                context5.putDouble("test1", Math.random());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
        new Thread(() -> {
            while (true) {
                context6.putDouble("test1", Math.random());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

//       Thread.sleep(1000000);

    }
}

