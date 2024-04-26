package org.kobe.xbot.Client;
// EXAMPLE SETUP

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class Main {
    private static final String SERVER_ADDRESS = "10.0.0.92"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT);
        // Thread blocks until connection is successful

        // Get raw JSON from server
        String response = client.getRawJSON().complete();
        System.out.println(response);

        // -------- PUT VALUES --------
        // "OK" - Value updated
        // "FAIL" - Failed to update

        // Put a string value into server
        response = client.putRaw("SmartDashboard", "Some Value").complete();
        System.out.println(response);

        // Put a string value into a sub table
        response = client.putRaw("SmartDashboard.sometable", "Some Value").complete();
        System.out.println(response);

        // Put a string value into server asynchronously
        client.putRaw("SmartDashboard", "Some Value").queue();

        // Put a string value into server asynchronously with response
        client.putRaw("SmartDashboard", "Some Value").queue(System.out::println);

        // Put a string value into server asynchronously with response & fail
        client.putRaw("SmartDashboard", "Some Value").queue(System.out::println, System.err::println);

        // Put an object value into server thread block
        response = client.putObject("SmartDashboard", new String("OK")).complete();
        System.out.println(response);

        // Put a integer value on sub table  asynchronously
        client.putInteger("SmartDashboard.somevalue", 488).queue();

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
    }
}
