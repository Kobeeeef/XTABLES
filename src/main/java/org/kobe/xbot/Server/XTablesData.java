package org.kobe.xbot.Server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.kobe.xbot.Utilities.Utilities;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;

import java.util.*;

public class XTablesData<V> {
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private static final Gson gson = new GsonBuilder().create();
    private static final Set<String> flaggedKeys = new HashSet<>();
    private Map<String, XTablesData<V>> data;
    private V value;
    public XTablesData() {
        // Initialize the data map lazily
    }

    // Method to put a value into the nested structure
    public boolean put(String key, V value) {
        Utilities.validateKey(key, true);
        if(!Utilities.isValidValue((String) value) && !flaggedKeys.contains(key)) {
            flaggedKeys.add(key);
            logger.warning("Invalid JSON value for key '" + key + "': " + value);
            logger.warning("The key '" + key + "' is now a flagged value.");
        } else if (Utilities.isValidValue((String) value) && flaggedKeys.contains(key)) {
            flaggedKeys.remove(key);
            logger.warning("The key '" + key + "' is no longer a flagged value.");
        }
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

    public int size() {
        int count = (value != null) ? 1 : 0;
        if (data != null) {
            for (XTablesData<V> child : data.values()) {
                count += child.size();
            }
        }
        return count;
    }

    public boolean isFlaggedKey(String key) {
        Utilities.validateKey(key, true);
        return flaggedKeys.contains(key);
    }

    // Method to get a value from the nested structure
    public V get(String key) {
        Utilities.validateKey(key, true);
        XTablesData<V> current = getLevelxTablesData(key);
        if (current == null) return null; // Key not found

        // Return the value found at the final level
        return current.value;
    }

    public V get(String key, V defaultValue) {
        Utilities.validateKey(key, true);
        V result = get(key);
        return (result != null) ? result : defaultValue;
    }

    private XTablesData<V> getLevelxTablesData(String key) {
        Utilities.validateKey(key, true);
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

    public boolean renameKey(String oldKey, String newKeyName) {
        Utilities.validateKey(oldKey, true);
        Utilities.validateName(newKeyName, true);
        if (oldKey == null || newKeyName == null || oldKey.isEmpty() || newKeyName.isEmpty()) {
            return false; // Invalid parameters
        }

        String[] oldKeys = oldKey.split("\\.");
        if (oldKeys.length == 0) {
            return false; // No key to rename
        }

        // Split the old key and construct the new key
        String parentKey = String.join(".", Arrays.copyOf(oldKeys, oldKeys.length - 1));
        String newKey = parentKey.isEmpty() ? newKeyName : parentKey + "." + newKeyName;

        XTablesData<V> parentNode = getLevelxTablesData(parentKey);
        if (parentNode == null || !parentNode.data.containsKey(oldKeys[oldKeys.length - 1])) {
            return false; // Old key does not exist
        }

        // Get the old node
        XTablesData<V> oldNode = parentNode.data.get(oldKeys[oldKeys.length - 1]);
        if (oldNode == null) {
            return false;
        }

        // Check if new key already exists
        if (parentNode.data.containsKey(newKeyName)) {
            return false; // New key already exists
        }

        // Rename by moving the node
        parentNode.data.put(newKeyName, oldNode);
        parentNode.data.remove(oldKeys[oldKeys.length - 1]);

        // Update flagged keys
        if (flaggedKeys.contains(oldKey)) {
            flaggedKeys.remove(oldKey);
            flaggedKeys.add(newKey);
        }

        return true; // Successfully renamed
    }

    // Method to get all tables at a given level
    public Set<String> getTables(String key) {
        Utilities.validateKey(key, true);
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
        Utilities.validateKey(key, true);
        if (key.isEmpty()) {
            this.data = null; // Remove everything if key is empty
            flaggedKeys.clear(); // Clear flagged keys if everything is removed
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

        boolean result = current.data != null && current.data.remove(keys[keys.length - 1]) != null;

        if (result) {
            flaggedKeys.remove(key);
        }

        return result;
    }

    public String toJSON() {
        return gson.toJson(this.data);
    }


    public void updateFromRawJSON(String json) {
        // Replace the current map directly with the parsed data
        // This avoids unnecessary clearing and re-creating of the map
        Map<String, XTablesData<V>> newData = gson.fromJson(json, new TypeToken<Map<String, XTablesData<V>>>(){}.getType());

        // Check if the new data is null which might be the case if json is empty or invalid
        if (newData == null) {
            newData = new HashMap<>(); // Ensure the data field is never null
        }

        this.data = newData; // Directly assign the new data
    }

}
