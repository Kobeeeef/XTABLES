package org.kobe.xbot.Server;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class XTablesData<V> {
    private Map<String, XTablesData<V>> data;
    private V value;

    public XTablesData() {
        // Initialize the data map lazily
    }

    // Method to put a value into the nested structure
    public boolean put(String key, V value) {
        String[] keys = key.split("\\."); // Split the key by '.'
        XTablesData<V> current = this;

        // Traverse through the nested structure until reaching the final level
        for (String k : keys) {
            if (current.data == null) {
                current.data = new HashMap<>();
            }
            if (!current.data.containsKey(k)) {
                current.data.put(k, new XTablesData<>());
            }
            current = current.data.get(k);
        }

        // Put the value into the final level
        current.value = value;
        return true; // Operation successful
    }

    // Method to get a value from the nested structure
    public V get(String key) {
        XTablesData<V> current = getLevelxTablesData(key);
        if (current == null) return null; // Key not found

        // Return the value found at the final level
        return current.value;
    }

    public V get(String key, V defaultValue) {
        V result = get(key);
        return (result != null) ? result : defaultValue;
    }

    private XTablesData<V> getLevelxTablesData(String key) {
        String[] keys = key.split("\\."); // Split the key by '.'
        XTablesData<V> current = this;

        // Traverse through the nested structure
        for (String k : keys) {
            if (current.data == null || !current.data.containsKey(k)) {
                return null; // Key not found
            }
            current = current.data.get(k);
        }
        return current;
    }

    // Method to get all tables at a given level
    public Set<String> getTables(String key) {
        if (key.isEmpty()) {
            return (data != null) ? data.keySet() : null;
        }
        XTablesData<V> current = getLevelxTablesData(key);
        if (current == null || current.data == null) return null;

        // Return the keys at this level
        return current.data.keySet();
    }

    // Method to delete a value at a given level
    public boolean delete(String key) {
        if (key.isEmpty()) {
            this.data = null; // Remove everything if key is empty
            return true;
        }
        String[] keys = key.split("\\."); // Split the key by '.'
        XTablesData<V> current = this;

        // Traverse through the nested structure until reaching the level to delete
        for (int i = 0; i < keys.length - 1; i++) {
            String k = keys[i];
            if (current.data == null || !current.data.containsKey(k)) {
                return false; // Key not found
            }
            current = current.data.get(k);
        }

        // Remove the entry at the final level
        return current.data != null && current.data.remove(keys[keys.length - 1]) != null;
    }

    public String toJSON() {
        return new Gson().toJson(this.data);
    }
}
