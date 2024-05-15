package org.kobe.xbot.Client;


import java.io.IOException;

public class OverloadTest {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT, 2, false);
        int i = 0;
        while (true) {
            i++;
            System.out.println(i);
            XTablesClient.LatencyInfo info = client.ping_latency().complete();
            System.out.println("Network Latency: " + info.networkLatencyMS() + "ms");
            System.out.println("Round Trip Latency: " + info.roundTripLatencyMS() + "ms");
            System.out.println("CPU Usage: " + info.systemStatistics().getProcessCpuLoadPercentage());
        }

    }
}
