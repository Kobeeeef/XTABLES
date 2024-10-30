package org.kobe.xbot.Tests;




import org.kobe.xbot.ClientLite.XTablesClient;

import java.io.IOException;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new org.kobe.xbot.ClientLite.XTablesClient(1735, true, 10, false);
        while (true) {
               client.pushZMQStringUpdate("tawd", Math.random() + "");
        }

    }


}
