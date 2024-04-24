package org.kobe.xbot.Client;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class XTablesClient {
    private final SocketClient client;
    private final Gson gson = new Gson();

    public XTablesClient(String SERVER_ADDRESS, int SERVER_PORT) {
        this.client = new SocketClient(SERVER_ADDRESS, SERVER_PORT, 1000);
        this.client.connect();
    }

    public RequestAction<String> putRaw(String key, String value) {
        return new RequestAction<>(client, "PUT " + key + " " + value, String.class);
    }
    public <T> RequestAction<String> putArray(String key, List<T> value) {
        String parsedValue = gson.toJson(value);
        return new RequestAction<>(client, "PUT " + key + " " + parsedValue, String.class);
    }
    public RequestAction<Integer> putInteger(String key, Integer value) {
        return new RequestAction<>(client, "PUT " + key + " " + value, Integer.class);
    }

    public RequestAction<String> delete(String key) {
        return new RequestAction<>(client, "DELETE " + key, String.class);
    }

    public RequestAction<String> getRaw(String key) {
        return new RequestAction<>(client, "GET " + key, String.class);
    }
    public RequestAction<String> getRawJSON() {
        return new RequestAction<>(client, "GET_RAW_JSON", null);
    }
    public RequestAction<String> getString(String key) {
        return new RequestAction<>(client, "GET " + key, String.class);
    }
    public <T> RequestAction<ArrayList<T>> getArray(String key, Class<T> type) {
        return new RequestAction<>(client, "GET " + key, type);
    }
    public RequestAction<ArrayList<String>> getTables(String key) {
        return new RequestAction<>(client, "GET_TABLES " + key, ArrayList.class);
    }
}
