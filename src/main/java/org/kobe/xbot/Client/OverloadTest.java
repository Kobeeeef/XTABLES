package org.kobe.xbot.Client;


import java.io.IOException;
import org.kobe.xbot.Client.XTablesClient;
public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient();

        while(true) {
            client.putString("yep", "" + Math.random()).execute();
        }


    }


}
