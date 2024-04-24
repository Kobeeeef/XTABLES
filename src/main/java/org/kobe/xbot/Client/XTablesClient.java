package org.kobe.xbot.Client;

public class XTablesClient {
    private final SocketClient client;

    public XTablesClient(String SERVER_ADDRESS, int SERVER_PORT) {
        this.client = new SocketClient(SERVER_ADDRESS, SERVER_PORT, 1000);
        this.client.connect();
    }

    public RequestAction put(String key, String value) {
        return new RequestAction(client, "PUT " + key + " " + value);
    }

    public RequestAction put(String key, Integer value) {
        return new RequestAction(client, "PUT " + key + " " + value);
    }

    public RequestAction delete(String key) {
        return new RequestAction(client, "DELETE " + key);
    }

    public RequestAction getRaw(String key) {
        return new RequestAction(client, "GET " + key);
    }

    public RequestAction getTables(String key) {
        return new RequestAction(client, "GET_TABLES " + key);
    }
}
