package org.kobe.xbot.Utilities;


import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

public class Utilities {

    public static byte[] matToByteArray(Mat mat) {
        BytePointer bytePointer = new BytePointer();
        opencv_imgcodecs.imencode(".jpg", mat, bytePointer); // Encode the image
        byte[] byteArray = new byte[(int) bytePointer.limit()];
        bytePointer.get(byteArray);
        return byteArray;
    }

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

            return true; // If parsing succeeds, JSON is valid
        } catch ( Error | Exception e) {
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
    public static int extractPortFromDescription(String description) {
        for (String part : description.split(";")) {
            part = part.trim();
            if (part.startsWith("Port=")) {
                return Integer.parseInt(part.substring(5));
            }
        }
        throw new IllegalArgumentException("Port not found in service description");
    }
    public static boolean validateKey(String key, boolean throwError) {
        // Check if key is null or empty
        if (key == null) {
            if (throwError) throw new IllegalArgumentException("Key cannot be null.");
            else return false;
        }

        // Check if key contains spaces
        if (key.contains(" ")) {
            if (throwError) throw new IllegalArgumentException("Key cannot contain spaces.");
            else return false;
        }

        // Check if key starts or ends with '.'
        if (key.startsWith(".") || key.endsWith(".")) {
            if (throwError) throw new IllegalArgumentException("Key cannot start or end with '.'");
            else return false;
        }

        // Check if key contains multiple consecutive '.'
        if (key.contains("..")) {
            if (throwError) throw new IllegalArgumentException("Key cannot contain multiple consecutive '.'");
            else return false;
        }

        // Check if each part of the key separated by '.' is empty
        if (!key.isEmpty()) {
            String[] parts = key.split("\\.");
            for (String part : parts) {
                if (part.isEmpty()) {
                    if (throwError) throw new IllegalArgumentException("Key contains empty part(s).");
                    else return false;
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
