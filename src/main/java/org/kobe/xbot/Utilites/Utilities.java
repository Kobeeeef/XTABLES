package org.kobe.xbot.Utilites;

public class Utilities {
    public static boolean validateKey(String key) {
        // Check if key is null or empty
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null.");
        }

        // Check if key contains spaces
        if (key.contains(" ")) {
            throw new IllegalArgumentException("Key cannot contain spaces.");
        }

        // Check if key starts or ends with '.'
        if (key.startsWith(".") || key.endsWith(".")) {
            throw new IllegalArgumentException("Key cannot start or end with '.'");
        }

        // Check if key contains multiple consecutive '.'
        if (key.contains("..")) {
            throw new IllegalArgumentException("Key cannot contain multiple consecutive '.'");
        }

        // Check if each part of the key separated by '.' is empty
        if (!key.isEmpty()) {
            String[] parts = key.split("\\.");
            for (String part : parts) {
                if (part.isEmpty()) {
                    throw new IllegalArgumentException("Key contains empty part(s).");
                }
            }
        }
        return true;
    }

    public static boolean validateName(String name, boolean throwError) {
        // Check if name is null or empty
        if (name == null) {
            if (throwError) throw new IllegalArgumentException("Name cannot be null.");
            else return false;
        }

        // Check if name contains spaces
        if (name.contains(" ")) {
            if (throwError) throw new IllegalArgumentException("Name cannot contain spaces.");
            else return false;
        }

        // Check if name contains '.'
        if (name.contains(".")) {
            if (throwError) throw new IllegalArgumentException("Name cannot contain '.'");
            else return false;
        }


        return true;
    }
}
