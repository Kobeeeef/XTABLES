package org.kobe.xbot.Tests;




import org.kobe.xbot.ClientLite.XTablesClient;

import java.io.IOException;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new org.kobe.xbot.ClientLite.XTablesClient("localhost", 1735);
        int i = 0;
        while(true) {
            client.executePutInteger("test", i);

         i++;


        }
//        Double[] response = client.getObject("test", Double[].class).complete();
//        System.out.println(Arrays.toString(response));
//
//
//        String b = client.getString("test").complete();
//        System.out.println(b);
//        System.out.println(b == null);

    }


}
