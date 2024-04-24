package org.kobe.xbot.Client;


public class Main {
    private static final String SERVER_ADDRESS = "localhost"; // Server address
    private static final int SERVER_PORT = 1735; // Server port

    public static void main(String[] args) {
        XTablesClient client = new XTablesClient(SERVER_ADDRESS, SERVER_PORT);

        String result = client.put("test.test2", 12).complete();
        System.out.println(result);

        String result2 = client.getRaw("test.test2").complete();
        System.out.println(result2);


    }
}
