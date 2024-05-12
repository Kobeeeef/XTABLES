package org.kobe.xbot.Client;

import org.kobe.xbot.Utilites.XTablesLogger;

import java.io.IOException;
import java.util.logging.Level;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT, 2, false);


    }
}
