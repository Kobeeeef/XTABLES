package org.kobe.xbot.Client;


public class Main {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) {
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT);
        String response = client.putInteger("SmartDashboard.test", 25).complete();
        System.out.println(response);
    }
}
