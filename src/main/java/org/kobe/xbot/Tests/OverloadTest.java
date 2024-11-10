package org.kobe.xbot.Tests;




import org.kobe.xbot.ClientLite.XTablesClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new org.kobe.xbot.ClientLite.XTablesClient(1735, true, 10, false);
        List<Integer> a = new ArrayList<>();
        a.add(123);
        client.executePutArray("test", a);
        Double[] response = client.getObject("test", Double[].class).complete();
        System.out.println(Arrays.toString(response));


        String b = client.getString("test").complete();
        System.out.println(b);
        System.out.println(b == null);

    }


}
