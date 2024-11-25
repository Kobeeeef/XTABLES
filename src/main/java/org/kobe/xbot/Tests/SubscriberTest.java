package org.kobe.xbot.Tests;

import org.kobe.xbot.ClientLite.XTablesClient;

import java.io.IOException;

public class SubscriberTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new org.kobe.xbot.ClientLite.XTablesClient(1735, true, 10, false);

        client.subscribeUpdateEvent((stringKeyValuePair -> {
           System.out.println(stringKeyValuePair.getValue());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        })).complete();


    }

}
