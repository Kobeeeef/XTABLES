package org.kobe.xbot.Client;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient();
        int i = 0;
        long time = System.nanoTime();
        List<Double> times = new ArrayList<>();
        while (i < 1000000) {
            i++;
            try {
                long start = System.nanoTime();
                client.executePutInteger("ok", 1);
                times.add((System.nanoTime() - start) / 1e6);
            } catch (Exception ignored) {
            }
        }
        System.out.println("Average Time (1M Updates): " + times.stream().mapToDouble(Double::doubleValue)
                .average().orElse(Double.NaN) + " ms");
        System.out.println("Total Time (1M Updates): " + (System.nanoTime() - time) / 1e6 + " ms");


    }


}
