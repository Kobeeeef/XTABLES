package org.kobe.xbot.Utilities;



import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class Utilities {
    private static final Logger logger = XTablesLogger.getLogger();

    public static String getLocalIPAddress() {
        try {
                return findBestNetworkAddress().getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }
    public static InetAddress getLocalInetAddress() {
        try {
                return findBestNetworkAddress();
        } catch (Exception ignored) {
            return null;
        }

    }


    private static InetAddress findBestNetworkAddress() throws SocketException {
        List<NetworkInterface> sortedInterfaces = getSortedNetworkInterfaces();

        for (NetworkInterface networkInterface : sortedInterfaces) {
            if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) continue;
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();

                // Prioritize site-local IPv4 addresses, excluding Docker subnets
                if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress() && inetAddress instanceof Inet4Address) {
                    String ip = inetAddress.getHostAddress();
                    if (!isDockerSubnet(ip)) {
                        return inetAddress;
                    }
                }
            }
        }
        throw new SocketException("No suitable non-loopback IPv4 address found");
    }

    private static List<NetworkInterface> getSortedNetworkInterfaces() throws SocketException {
        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

        // Sort interfaces: Ethernet first, then WiFi, then others
        interfaces.sort(Comparator.comparingInt(Utilities::getInterfacePriority));
        return interfaces;
    }

    private static int getInterfacePriority(NetworkInterface networkInterface) {
        String os = System.getProperty("os.name").toLowerCase();
        String name = os.contains("win") ? networkInterface.getDisplayName().toLowerCase() : networkInterface.getName().toLowerCase();
        if (name.startsWith("eth") || name.startsWith("enp") || name.startsWith("eno") || name.startsWith("ens") || name.contains("ethernet")) {
            return 0; // Ethernet (highest priority)
        } else if (name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("wlp") || name.startsWith("wlo") || name.contains("wi-fi")) {
            return 1; // WiFi
        } else if (name.contains("tailscale") || name.contains("docker") || name.contains("virtual") || name.contains("veth")) {
            return 99; // Tailscale, Docker, Virtual interfaces (lowest priority)
        }
        return 2; // Other interfaces
    }


    /**
     * Check if the IP belongs to Docker's typical private subnet ranges.
     *
     * @param ip The IP address as a string.
     * @return true if the IP is in Docker's subnet; false otherwise.
     */
    private static boolean isDockerSubnet(String ip) {
        return (ip.startsWith("172.") && isWithinRange(ip, 16, 31)) || ip.startsWith("169.254");
    }
    /**
     * Helper method to determine if an IP's second octet is within a specific range.
     *
     * @param ip       The IP address as a string.
     * @param start    The start of the range (inclusive).
     * @param end      The end of the range (inclusive).
     * @return true if within range; false otherwise.
     */
    private static boolean isWithinRange(String ip, int start, int end) {
        try {
            String[] parts = ip.split("\\.");
            int secondOctet = Integer.parseInt(parts[1]);
            return secondOctet >= start && secondOctet <= end;
        } catch (Exception e) {
            return false;
        }
    }
    public static <E extends Enum<E>> boolean contains(Class<E> enumClass, String constant) {
        for (E e : enumClass.getEnumConstants()) {
            if (e.name().equals(constant)) {
                return true;
            }
        }
        return false;
    }

    public static String[] tokenize(String input, char delimiter, int maxTokens) {
        int count = 1;
        int length = input.length();

        // First pass: Calculate the number of delimiters up to maxTokens
        for (int i = 0; i < length && (maxTokens == 0 || count < maxTokens); i++) {
            if (input.charAt(i) == delimiter) {
                count++;
            }
        }

        // Allocate array for results, with either full count or maxTokens
        String[] result = new String[count];
        int index = 0;
        int tokenStart = 0;

        // Second pass: Extract tokens up to maxTokens
        for (int i = 0; i < length; i++) {
            if (input.charAt(i) == delimiter) {
                result[index++] = input.substring(tokenStart, i);
                tokenStart = i + 1;
                if (maxTokens > 0 && index == maxTokens - 1) {
                    break;
                }
            }
        }

        // Add last token or the remainder if maxTokens was reached
        result[index] = input.substring(tokenStart);

        return result;
    }


    @SafeVarargs
    public static <K, V> ConcurrentHashMap<K, V> combineConcurrentHashMaps(ConcurrentHashMap<K, V>... maps) {
        ConcurrentHashMap<K, V> combinedMap = new ConcurrentHashMap<>();
        for (ConcurrentHashMap<K, V> map : maps) {
            combinedMap.putAll(map);
        }
        return combinedMap;
    }

    public static boolean validateKey(String key, boolean throwError) {
        // Check if the key is null or empty
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
            int length = key.length();
            int start = 0;
            boolean partFound = false;

            for (int i = 0; i < length; i++) {
                if (key.charAt(i) == '.') {
                    if (i == start) {
                        // Empty part found
                        if (throwError) throw new IllegalArgumentException("Key contains empty part(s).");
                        else return false;
                    }
                    // Process the part if needed here
                    start = i + 1;
                    partFound = true;
                }
            }

            // Check the last part
            if (start < length) {
                partFound = true;
            } else {
                if (throwError) throw new IllegalArgumentException("Key contains empty part(s).");
                else return false;
            }

            return partFound;
        }

        return true;
    }

    public static boolean validateName(String name, boolean throwError) {
        // Check if the name is null or empty
        if (name == null) {
            if (throwError) throw new IllegalArgumentException("Name cannot be null.");
            else return false;
        }

        // Check if the name contains spaces
        if (name.contains(" ")) {
            if (throwError) throw new IllegalArgumentException("Name cannot contain spaces.");
            else return false;
        }

        // Check if the name contains '.'
        if (name.contains(".")) {
            if (throwError) throw new IllegalArgumentException("Name cannot contain '.'");
            else return false;
        }


        return true;
    }

    /**
     * Measures how many times a simple while loop can execute in one second.
     *
     * @return the number of iterations the while loop can execute in one second
     */
    public static int measureWhileLoopIterationsPerSecond() {
        long startTime = System.nanoTime();
        long endTime = startTime + 1_000_000_000;
        int iterations = 0;
        XTablesData xTablesData = new XTablesData();
        byte[] bytes = XTableProto.XTableMessage.newBuilder()
                .setCommand(getRandomCommand())
                .setValue(ByteString.copyFrom(new byte[]{12, 12, 3}))
                .build()
                .toByteArray();
        while (System.nanoTime() < endTime) {
            iterations++;
            try {
                XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(bytes);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
            xTablesData.put(getRandomCommand().name(), new byte[]{23, 21, 12}, XTableProto.XTableMessage.Type.UNKNOWN);
        }

        return iterations / 6;
    }





    public static byte[] generateRandomBytes(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[length];
        secureRandom.nextBytes(randomBytes);
        return randomBytes;
    }


    private static XTableProto.XTableMessage.Command getRandomCommand() {
        XTableProto.XTableMessage.Command[] commands = XTableProto.XTableMessage.Command.values();
        int randomIndex = ThreadLocalRandom.current().nextInt(commands.length - 1);
        return commands[randomIndex];
    }

    public static void warmupProtobuf() {
        logger.info("Starting Protobuf warmup with 1,000,000 iterations to allow JIT compiler optimizations...");
        for (int i = 0; i < 10000; i++) {
            String stringValue = "Hello, world!";
            byte[] stringBytes = stringValue.getBytes(StandardCharsets.UTF_8);
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(stringBytes))
                    .build()
                    .toByteArray();
        }
        for (int i = 0; i < 10000; i++) {
            int intValue = 12345;
            byte[] intBytes = ByteBuffer.allocate(4).putInt(intValue).array();
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(intBytes))
                    .build()
                    .toByteArray();
        }
        for (int i = 0; i < 10000; i++) {
            long longValue = 123456789L;
            byte[] longBytes = ByteBuffer.allocate(8).putLong(longValue).array();
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(longBytes))
                    .build()
                    .toByteArray();
        }
        for (int i = 0; i < 10000; i++) {
            float floatValue = 3.14f;
            byte[] floatBytes = ByteBuffer.allocate(4).putFloat(floatValue).array();
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(floatBytes))
                    .build()
                    .toByteArray();
        }
        for (int i = 0; i < 10000; i++) {
            double doubleValue = 3.14159265359;
            byte[] doubleBytes = ByteBuffer.allocate(8).putDouble(doubleValue).array();
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(doubleBytes))
                    .build()
                    .toByteArray();
        }
        for (int i = 0; i < 10000; i++) {
            String[] stringArray = {"Hello", "world", "this", "is", "protobuf"};  // Example String array
            int totalSize = 0;
            for (String s : stringArray) {
                totalSize += s.getBytes(StandardCharsets.UTF_8).length;  // Add the length of each string in bytes
            }

            byte[] stringArrayBytes = new byte[totalSize];
            int offset = 0;
            for (String s : stringArray) {
                byte[] strBytes = s.getBytes(StandardCharsets.UTF_8);
                System.arraycopy(strBytes, 0, stringArrayBytes, offset, strBytes.length);
                offset += strBytes.length;  // Move the offset for the next string
            }

            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(stringArrayBytes))
                    .build()
                    .toByteArray();
        }

        for (int i = 0; i < 10000; i++) {
            int[] intArray = {12345, 67890, 111213};  // Example array
            byte[] intArrayBytes = new byte[intArray.length * 4];
            ByteBuffer buffer = ByteBuffer.wrap(intArrayBytes);
            for (int value : intArray) {
                buffer.putInt(value);
            }
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(intArrayBytes))
                    .build()
                    .toByteArray();
        }

        for (int i = 0; i < 10000; i++) {
            long[] longArray = {123456789L, 987654321L};  // Example array
            byte[] longArrayBytes = new byte[longArray.length * 8];
            ByteBuffer buffer = ByteBuffer.wrap(longArrayBytes);
            for (long value : longArray) {
                buffer.putLong(value);
            }
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(longArrayBytes))
                    .build()
                    .toByteArray();
        }

        for (int i = 0; i < 10000; i++) {
            float[] floatArray = {3.14f, 1.618f};  // Example array
            byte[] floatArrayBytes = new byte[floatArray.length * 4];
            ByteBuffer buffer = ByteBuffer.wrap(floatArrayBytes);
            for (float value : floatArray) {
                buffer.putFloat(value);
            }
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(floatArrayBytes))
                    .build()
                    .toByteArray();
        }

        for (int i = 0; i < 10000; i++) {

            double[] doubleArray = {3.14159265359, 2.71828182846};  // Example array
            byte[] doubleArrayBytes = new byte[doubleArray.length * 8];
            ByteBuffer buffer = ByteBuffer.wrap(doubleArrayBytes);
            for (double value : doubleArray) {
                buffer.putDouble(value);
            }
            XTableProto.XTableMessage.newBuilder()
                    .setCommand(getRandomCommand())
                    .setValue(ByteString.copyFrom(doubleArrayBytes))
                    .build()
                    .toByteArray();
        }
        logger.info("Protobuf warmup has finished to allow JIT compiler optimizations.");
    }
}
