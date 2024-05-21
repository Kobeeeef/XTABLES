package org.kobe.xbot.Utilities;


import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

public class Utilities {
    private static final JSONParser parser = new JSONParser();
    public static String getLocalIpAddress() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            // Skip loopback and down interfaces
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress inetAddress = addresses.nextElement();

                // Return the first non-loopback IPv4 address
                if (!inetAddress.isLoopbackAddress() && inetAddress instanceof InetAddress && inetAddress.getHostAddress().contains(".")) {
                    return inetAddress.getHostAddress();
                }
            }
        }
        throw new SocketException("No non-loopback IPv4 address found");
    }
    public static <E extends Enum<E>> boolean contains(Class<E> enumClass, String constant) {
        for (E e : enumClass.getEnumConstants()) {
            if (e.name().equals(constant)) {
                return true;
            }
        }
        return false;
    }
    public static boolean isValidValue(String jsonString) {
        try {
            // Attempt to parse the JSON string
            parser.parse(jsonString);
            return true; // If parsing succeeds, JSON is valid
        } catch (ParseException e) {
            return false; // If parsing fails, JSON is invalid
        }
    }
    @SafeVarargs
    public static <K, V> ConcurrentHashMap<K, V> combineConcurrentHashMaps(ConcurrentHashMap<K, V>... maps) {
        ConcurrentHashMap<K, V> combinedMap = new ConcurrentHashMap<>();
        for (ConcurrentHashMap<K, V> map : maps) {
            combinedMap.putAll(map);
        }
        return combinedMap;
    }
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
