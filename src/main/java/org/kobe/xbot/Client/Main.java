package org.kobe.xbot.Client;
// EXAMPLE SETUP

import org.kobe.xbot.Utilites.ResponseStatus;

import java.util.List;
import java.util.function.Consumer;

public class Main {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT, 5);
        // Thread blocks until connection is successful

        // Get raw JSON from server
        String response = client.getRawJSON().complete();
        System.out.println(response);

        // Variable for connection status, updates on every message
        System.out.println("Connected? " + client.getSocketClient().isConnected);

        // -------- PUT VALUES --------
        // "OK" - Value updated
        // "FAIL" - Failed to update

        // Put a string value into server
        ResponseStatus status = client.putString("SmartDashboard", "Some Value1" + Math.random()).complete();
        System.out.println(status);

        // Put a string value into a sub table
        status = client.putString("SmartDashboard.sometable", "Some Value").complete();
        System.out.println(status);

        // Put a string value into server asynchronously
        client.putString("SmartDashboard", "Some Value").queue();

        // Put a string value into server asynchronously with response
        client.putString("SmartDashboard", "Some Value").queue(System.out::println);

        // Put a string value into server asynchronously with response & fail
        client.putString("SmartDashboard", "Some Value").queue(System.out::println, System.err::println);

        // Put an object value into server thread block
        status = client.putObject("SmartDashboard", new String("OK")).complete();
        System.out.println(status);

        // Put an integer value on sub table asynchronously
        client.putInteger("SmartDashboard.somevalue.awdwadawd", 488).queue();

        // Put an integer array value on sub table asynchronously
        client.putArray("SmartDashboard.somearray", List.of("Test", "TEAM XBOT")).queue();

        // -------- GET VALUES --------
        // value - Value retrieved
        // null - No value

        // Get all tables on server
        List<String> tables = client.getTables("").complete();
        System.out.println(tables);

        // Get sub tables of table on server
        List<String> sub_tables = client.getTables("SmartDashboard").complete();
        System.out.println(sub_tables);

        // Get string from sub-table
        String value = client.getString("SmartDashboard.somevalue").complete();
        System.out.println(value);

        // Get integer from sub-table
        Integer integer = client.getInteger("SmartDashboard.somevalue").complete();
        System.out.println(integer);

        // Subscribe to an update event on key
        client.subscribeUpdateEvent("SmartDashboard", Integer.class, new_value ->
                        System.out.println("New Value: " + new_value)
                )
                .complete();

        // Define a consumer for update events
        Consumer<SocketClient.KeyValuePair<String>> updateConsumer = update -> {
            System.out.println("Update received: " + update);
        };
        // Subscribe to updates for a specific key
        status = client.subscribeUpdateEvent("key", String.class, updateConsumer).complete();
        System.out.println(status);
        // Unsubscribe after a certain condition is met
        status = client.unsubscribeUpdateEvent("key", String.class, updateConsumer).complete();
        System.out.println(status);

        // Get latency from server
        XTablesClient.LatencyInfo info = client.ping_latency().complete();
        System.out.println("Network Latency: " + info.networkLatencyMS() + "ms");
        System.out.println("Round Trip Latency: " + info.roundTripLatencyMS() + "ms");
        System.out.println("CPU Usage: " + info.systemStatistics().getProcessCpuLoadPercentage());
    }
}