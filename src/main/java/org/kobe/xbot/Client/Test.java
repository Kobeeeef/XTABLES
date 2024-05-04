package org.kobe.xbot.Client;

public class Test {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) {
        // Initialize a new client with address and port
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT, 5);

        client.subscribeUpdateEvent((stringKeyValuePair -> {
            System.out.println("Key: " + stringKeyValuePair.getKey());
            System.out.println("Value: " + stringKeyValuePair.getValue());

        })).complete();

    }
}
