package org.kobe.xbot.JClient;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Exceptions.XTablesServerNotFound;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.Utilities;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * XTablesClient - A client class for interacting with the XTABLES server.
 * <p>
 * This class is responsible for client-side functionality in the XTABLES system,
 * including resolving the server's IP address, handling versioning information,
 * and providing socket initialization for communication with the XTABLES server.
 * It manages connections and provides utility methods for interacting with the server.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: XTABLES
 * <p>
 * This is part of the XTABLES project and provides client-side functionality for
 * socket communication with the server.
 */
public class XTablesClient {
    // =============================================================
    // Static Variables
    // These variables belong to the class itself and are shared
    // across all instances of the class.
    // =============================================================
    private static final XTablesLogger logger = XTablesLogger.getLogger();
    private static final byte[] success = new byte[]{(byte) 0x01};
    private static final byte[] fail = new byte[]{(byte) 0x00};
    private static final ByteString successByte = ByteString.copyFrom(success);
    private static final ByteString failByte = ByteString.copyFrom(fail);
    // =============================================================
    // Instance Variables
    // These variables are unique to each instance of the class.
    // =============================================================
    private String XTABLES_CLIENT_VERSION =
            "XTABLES Jero Client v1.0.0 | Build Date: 12/24/2024";
    private final ZContext context;
    private final ZMQ.Socket subSocket;
    private final ZMQ.Socket pushSocket;
    private final ZMQ.Socket reqSocket;

    /**
     * Default constructor for XTablesClient.
     * Initializes the client without specifying an IP address.
     */
    public XTablesClient() {
        this(null);
    }

    /**
     * Constructor for XTablesClient with a specified IP address.
     * Initializes the client with the given IP address.
     * <p>
     * If the IP address is not provided (i.e., it's null), the client will attempt to
     * automatically resolve the IP address of the XTABLES server using mDNS (Multicast DNS).
     * <p>
     * The following default ports are used for socket communication:
     * - Push socket port: 1735
     * - Request socket port: 1736
     * - Subscribe socket port: 1737
     *
     * @param ip The IP address of the XTABLES server. If null, it will resolve the IP automatically.
     */
    public XTablesClient(String ip) {
        this(ip, 1735, 1736, 1737);
    }


    /**
     * Constructor for XTablesClient with a specified IP address and ports.
     * Initializes the client with the given parameters.
     *
     * @param ip                  The IP address of the XTABLES server.
     * @param pushSocketPort      The port for the push socket.
     * @param requestSocketPort   The port for the request socket.
     * @param subscribeSocketPort The port for the subscriber socket.
     */
    public XTablesClient(String ip, int pushSocketPort, int requestSocketPort, int subscribeSocketPort) {
        if (ip == null) {
            InetAddress inetAddress = resolveHostByName();
            if (inetAddress == null) {
                throw new XTablesServerNotFound("Could not resolve XTABLES hostname server.");
            }
            ip = inetAddress.getHostAddress();
        }
        logger.info("\n" +
                "Connecting to XTABLES Server...\n" +
                "------------------------------------------------------------\n" +
                "Server IP: " + ip + "\n" +
                "Push Socket Port: " + pushSocketPort + "\n" +
                "Request Socket Port: " + requestSocketPort + "\n" +
                "Subscribe Socket Port: " + subscribeSocketPort + "\n" +
                "------------------------------------------------------------");
        this.context = new ZContext();
        this.pushSocket = context.createSocket(SocketType.PUSH);
        this.pushSocket.setHWM(500);
        this.pushSocket.connect("tcp://" + ip + ":" + pushSocketPort);
        this.reqSocket = context.createSocket(SocketType.REQ);
        this.reqSocket.setHWM(500);
        this.reqSocket.connect("tcp://" + ip + ":" + requestSocketPort);
        this.reqSocket.setReceiveTimeOut(3000);
        this.subSocket = context.createSocket(SocketType.SUB);
        this.subSocket.setHWM(500);
        this.subSocket.connect("tcp://" + ip + ":" + subscribeSocketPort);
    }

    /**
     * Sends a PUT request with a byte array value to the server.
     * <p>
     * This method sends the specified key and byte array value to the server using the PUSH socket.
     * The message type is set to UNKNOWN.
     *
     * @param key   The key associated with the value.
     * @param value The byte array value to be sent.
     * @return True if the message was sent successfully; otherwise, false.
     */
    public boolean executePutBytes(String key, byte[] value) {
        return sendPushMessage(XTableProto.XTableMessage.Command.PUT, key, value, XTableProto.XTableMessage.Type.UNKNOWN);
    }

    /**
     * Sends a PUT request with a byte array value to the server with a specified message type.
     * <p>
     * This method allows sending a byte array with a specific type (e.g., STRING, INT64).
     * The caller defines the type.
     *
     * @param key   The key associated with the value.
     * @param value The byte array value to be sent.
     * @param type  The type of the value being sent (e.g., STRING, INT64, etc.).
     * @return True if the message was sent successfully; otherwise, false.
     */
    public boolean executePutBytes(String key, byte[] value, XTableProto.XTableMessage.Type type) {
        return sendPushMessage(XTableProto.XTableMessage.Command.PUT, key, value, type);
    }

    /**
     * Sends a PUT request with a String value to the server.
     * <p>
     * This method converts the provided string value to a byte array using UTF-8 encoding and sends it to the server with the STRING type.
     *
     * @param key   The key associated with the string value.
     * @param value The string value to be sent.
     * @return True if the message was sent successfully; otherwise, false.
     */
    public boolean executePutString(String key, String value) {
        return sendPushMessage(XTableProto.XTableMessage.Command.PUT, key, value.getBytes(StandardCharsets.UTF_8), XTableProto.XTableMessage.Type.STRING);
    }

    /**
     * Sends a PUT request with an Integer value to the server.
     * <p>
     * This method converts the provided Integer value into a 4-byte array (using INT32 format) and sends it to the server with the INT64 type.
     * While INT64 is typically used for 8-byte data, it is used here to send the integer value.
     *
     * @param key   The key associated with the Integer value.
     * @param value The Integer value to be sent.
     * @return True if the message was sent successfully; otherwise, false.
     */
    public boolean executePutInteger(String key, Integer value) {
        byte[] valueBytes = ByteBuffer.allocate(4).putInt(value).array();
        return sendPushMessage(XTableProto.XTableMessage.Command.PUT, key, valueBytes, XTableProto.XTableMessage.Type.INT64); // Using INT64 for 4-byte Integer
    }

    /**
     * Sends a PUT request with a Long value to the server.
     * <p>
     * This method converts the provided Long value into an 8-byte array and sends it to the server with the INT64 type.
     *
     * @param key   The key associated with the Long value.
     * @param value The Long value to be sent.
     * @return True if the message was sent successfully; otherwise, false.
     */
    public boolean executePutLong(String key, Long value) {
        byte[] valueBytes = ByteBuffer.allocate(8).putLong(value).array();
        return sendPushMessage(XTableProto.XTableMessage.Command.PUT, key, valueBytes, XTableProto.XTableMessage.Type.INT64); // Using INT64 for 8-byte Long
    }

    /**
     * Sends a PUT request with a Double value to the server.
     * <p>
     * This method converts the provided Double value into an 8-byte array and sends it to the server with the DOUBLE type.
     *
     * @param key   The key associated with the Double value.
     * @param value The Double value to be sent.
     * @return True if the message was sent successfully; otherwise, false.
     */
    public boolean executePutDouble(String key, Double value) {
        byte[] valueBytes = ByteBuffer.allocate(8).putDouble(value).array();
        return sendPushMessage(XTableProto.XTableMessage.Command.PUT, key, valueBytes, XTableProto.XTableMessage.Type.DOUBLE); // Using DOUBLE for 8-byte Double
    }

    /**
     * Sends a PUT request with a boolean value to the server.
     * <p>
     * This method converts the provided boolean value to a byte array representation
     * and sends it to the server with the BOOL type.
     * The boolean value is converted
     * into one of two byte values: 'success' (for true) or 'fail' (for false).
     *
     * @param key   The key associated with the boolean value.
     * @param value The boolean value to be sent (true or false).
     * @return True if the message was sent successfully; otherwise, false.
     */
    public boolean executePutBoolean(String key, boolean value) {
        return sendPushMessage(XTableProto.XTableMessage.Command.PUT, key, value ? success : fail, XTableProto.XTableMessage.Type.BOOL); // Using DOUBLE for 8-byte Double
    }

    /**
     * Sends a PUT request with a List of values to the server.
     * <p>
     * This method serializes the provided List to a byte array and sends it to the server with the DOUBLE type.
     * The `toByteArray` method of the Utilities class is assumed to handle the serialization of the list to a byte array.
     *
     * @param key   The key associated with the List value.
     * @param value The List of values to be sent.
     * @param <T>   The type of the elements in the list.
     * @return True if the message was sent successfully; otherwise, false.
     */
    public <T> boolean executePutList(String key, List<T> value) {
        return sendPushMessage(XTableProto.XTableMessage.Command.PUT, key, Utilities.toByteArray(value), XTableProto.XTableMessage.Type.DOUBLE); // Using DOUBLE as the type for List serialization
    }

    /**
     * Sends a message via the PUSH socket to the server.
     * <p>
     * This helper method builds an XTableMessage and sends it using the PUSH socket. The message contains the specified key, command, value, and type.
     *
     * @param command The command to be sent (e.g., PUT).
     * @param key     The key associated with the value.
     * @param value   The byte array representing the value to be sent.
     * @param type    The type of the value being sent (e.g., STRING, INT64, etc.).
     * @return True if the message was sent successfully; otherwise, false.
     */
    private boolean sendPushMessage(XTableProto.XTableMessage.Command command, String key, byte[] value, XTableProto.XTableMessage.Type type) {
        return pushSocket.send(XTableProto.XTableMessage.newBuilder()
                .setKey(key)
                .setCommand(command)
                .setValue(ByteString.copyFrom(value))
                .setType(type)
                .build()
                .toByteArray(), ZMQ.DONTWAIT);
    }

    /**
     * Executes a GET request to retrieve a String value associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is STRING. If so, it converts the value to a UTF-8 string and returns it. Otherwise,
     * it throws an IllegalArgumentException indicating the wrong type.
     *
     * @param key The key associated with the String value.
     * @return The String value associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not STRING.
     */
    public String executeGetString(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.STRING)) {
            return message.getValue().toStringUtf8();
        } else if (message == null || (message != null && message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN))) {
            return null;
        } else {
            throw new IllegalArgumentException("Expected STRING type, but got: " + (message.getType()));
        }
    }

    /**
     * Executes a GET request to retrieve an Integer value associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is INT64. If so, it converts the byte array value to an Integer and returns it.
     * Otherwise, it throws an IllegalArgumentException indicating the wrong type.
     *
     * @param key The key associated with the Integer value.
     * @return The Integer value associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not INT64.
     */
    public Integer executeGetInteger(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.INT64)) {
            byte[] valueBytes = message.getValue().toByteArray();
            return ByteBuffer.wrap(valueBytes).getInt();
        } else if (message == null || (message != null && message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN))) {
            return null;
        } else {
            throw new IllegalArgumentException("Expected INT64 type, but got: " + message.getType());
        }
    }

    /**
     * Executes a GET request to retrieve a Boolean value associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is BOOL. If so, it converts the byte array value to a Boolean based on the value
     * of `successByte`. Otherwise, it throws an IllegalArgumentException indicating the wrong type.
     *
     * @param key The key associated with the Boolean value.
     * @return The Boolean value associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not BOOL.
     */
    public Boolean executeGetBoolean(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.BOOL)) {
            return message.getValue().equals(successByte);
        } else if (message == null || (message != null && message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN))) {
            return null;
        } else {
            throw new IllegalArgumentException("Expected BOOL type, but got: " + message.getType());
        }
    }

    /**
     * Executes a GET request to retrieve a Long value associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is INT64. If so, it converts the byte array value to a Long and returns it.
     * Otherwise, it throws an IllegalArgumentException indicating the wrong type.
     *
     * @param key The key associated with the Long value.
     * @return The Long value associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not INT64.
     */
    public Long executeGetLong(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.INT64)) {
            byte[] valueBytes = message.getValue().toByteArray();
            return ByteBuffer.wrap(valueBytes).getLong();
        } else if (message == null || (message != null && message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN))) {
            return null;
        } else {
            throw new IllegalArgumentException("Expected INT64 type, but got: " + message.getType());
        }
    }

    /**
     * Executes a GET request to retrieve a Double value associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is DOUBLE. If so, it converts the byte array value to a Double and returns it.
     * Otherwise, it throws an IllegalArgumentException indicating the wrong type.
     *
     * @param key The key associated with the Double value.
     * @return The Double value associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not DOUBLE.
     */
    public Double executeGetDouble(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.DOUBLE)) {
            byte[] valueBytes = message.getValue().toByteArray();
            return ByteBuffer.wrap(valueBytes).getDouble();
        } else if (message == null || (message != null && message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN))) {
            return null;
        } else {
            throw new IllegalArgumentException("Expected DOUBLE type, but got: " + message.getType());
        }
    }

    /**
     * Executes a GET request to retrieve a List of values associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is ARRAY. If so, it converts the byte array value to a List of the specified type
     * using the `Utilities.fromByteArray()` method. Otherwise, it throws an IllegalArgumentException
     * indicating the wrong type.
     *
     * @param key  The key associated with the List value.
     * @param type The class of the elements in the list.
     * @param <T>  The type of the elements in the list.
     * @return A List of values associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not ARRAY.
     */
    public <T> List<T> executeGetList(String key, Class<T> type) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.ARRAY)) {
            byte[] valueBytes = message.getValue().toByteArray();
            return Utilities.fromByteArray(valueBytes, type);
        } else if (message == null || (message != null && message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN))) {
            return null;
        } else {
            throw new IllegalArgumentException("Expected ARRAY type, but got: " + message.getType());
        }
    }

    /**
     * Sends a GET request to the server for the specified key and returns the parsed message.
     * <p>
     * This method constructs a GET message, sends it to the server, and then parses the response
     * into an XTableProto.XTableMessage object.
     * If there is an error during parsing, it returns null.
     *
     * @param key The key for which the GET request is being made.
     * @return The parsed XTableProto.XTableMessage object, or null if an error occurs.
     */
    private XTableProto.XTableMessage getXTableMessage(String key) {
        try {
            reqSocket.send(XTableProto.XTableMessage.newBuilder()
                    .setKey(key)
                    .setCommand(XTableProto.XTableMessage.Command.GET)
                    .build()
                    .toByteArray());
            return XTableProto.XTableMessage.parseFrom(reqSocket.recv());
        } catch (InvalidProtocolBufferException | NullPointerException e) {
            return null;
        }
    }


    /**
     * Returns the version information of the XTables client.
     *
     * @return A string containing the version details.
     */
    public String getVersion() {
        return XTABLES_CLIENT_VERSION;
    }

    /**
     * Adds a custom property to the version string.
     *
     * @param prop The property to be added to the version string.
     * @return The updated version string with the new property.
     */
    public String addVersionProperty(String prop) {
        XTABLES_CLIENT_VERSION = XTABLES_CLIENT_VERSION + " | " + prop.trim();
        return XTABLES_CLIENT_VERSION;
    }

    /**
     * Resolves the host by name (XTABLES.local).
     * This method attempts to resolve the IP address of the XTABLES server.
     * If a resolution fails, it retries every second until successful.
     *
     * @return The resolved InetAddress of the XTABLES server, or null if failed to resolve.
     */
    private InetAddress resolveHostByName() {
        InetAddress address = null;
        while (address == null) {
            try {
                logger.info("Attempting to resolve host 'XTABLES.local'...");
                address = Inet4Address.getByName("XTABLES.local");
                logger.info("Host resolution successful: IP address found at " + address.getHostAddress());
                logger.info("Proceeding with socket client initialization.");
            } catch (UnknownHostException e) {
                logger.severe("Failed to resolve 'XTABLES.local'. Host not found. Retrying in 1 second...");
                logger.severe("Exception details: " + e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warning("Retry wait interrupted. Exiting...");
                    return null;
                }
            }
        }
        return address;
    }
}
