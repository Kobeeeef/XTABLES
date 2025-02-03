package org.kobe.xbot.JClient;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.kobe.xbot.Utilities.DataCompression;
import org.kobe.xbot.Utilities.Entities.*;
import org.kobe.xbot.Utilities.Exceptions.XTablesException;
import org.kobe.xbot.Utilities.Logger.XTablesLogger;
import org.kobe.xbot.Utilities.SystemStatistics;
import org.kobe.xbot.Utilities.XTablesByteUtils;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.kobe.xbot.JClient.XTablesClient.*;

public class XTableContext implements PushRequests, GetRequests, AutoCloseable {
    private static final XTablesLogger logger = XTablesLogger.getLogger(XTableContext.class);
    private final ZMQ.Socket push;
    private ZMQ.Socket get;
    private final XTablesClient xTablesClient;
    private final String key;

    public XTableContext(ZMQ.Socket push, ZMQ.Socket get, String key, XTablesClient xTablesClient) {
        this.push = push;
        this.get = get;
        this.xTablesClient = xTablesClient;
        this.key = key;
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
    @Override
    public boolean putBytes(String key, byte[] value) {
        return sendPutMessage(key, value, XTableProto.XTableMessage.Type.BYTES);
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
    @Override
    public boolean putBytes(String key, byte[] value, XTableProto.XTableMessage.Type type) {
        return sendPutMessage(key, value, type);
    }

    /**
     * Sends a PUT request with a byte array value to the server with an UNKNOWN message type.
     * <p>
     * This method is a convenience method that uses the UNKNOWN type for sending byte array data.
     * </p>
     *
     * @param key   The key associated with the value. This is the identifier for the data.
     * @param value The byte array value to be sent. Contains the data to be sent to the server.
     * @return True if the message was sent successfully; otherwise, false.
     */
    @Override
    public boolean putUnknownBytes(String key, byte[] value) {
        return sendPutMessage(key, value, XTableProto.XTableMessage.Type.UNKNOWN);
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
    @Override
    public boolean putString(String key, String value) {
        return sendPutMessage(key, value.getBytes(StandardCharsets.UTF_8), XTableProto.XTableMessage.Type.STRING);
    }

    /**
     * Sends a PUT request with a list of coordinates to the server.
     * <p>
     * This method takes a list of `Coordinate` objects, builds a `CoordinateList` message from the list,
     * and sends it to the server as a byte array with the BYTES type.
     *
     * @param key   The key associated with the list of coordinates.
     * @param value The list of `Coordinate` objects to be sent.
     * @return True if the message was sent successfully; otherwise, false.
     */
    @Override
    public boolean putCoordinates(String key, List<XTableValues.Coordinate> value) {
        XTableValues.CoordinateList list = XTableValues.CoordinateList.newBuilder()
                .addAllCoordinates(value).build();
        return sendPutMessage(key, list.toByteArray(), XTableProto.XTableMessage.Type.BYTES);
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
    @Override
    public boolean putInteger(String key, Integer value) {
        byte[] valueBytes = ByteBuffer.allocate(4).putInt(value).array();
        return sendPutMessage(key, valueBytes, XTableProto.XTableMessage.Type.INT64); // Using INT64 for 4-byte Integer
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
    @Override
    public boolean putLong(String key, Long value) {
        byte[] valueBytes = ByteBuffer.allocate(8).putLong(value).array();
        return sendPutMessage(key, valueBytes, XTableProto.XTableMessage.Type.INT64); // Using INT64 for 8-byte Long
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
    @Override
    public boolean putDouble(String key, Double value) {
        byte[] valueBytes = ByteBuffer.allocate(8).putDouble(value).array();
        return sendPutMessage(key, valueBytes, XTableProto.XTableMessage.Type.DOUBLE); // Using DOUBLE for 8-byte Double
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
    @Override
    public boolean putBoolean(String key, boolean value) {
        return sendPutMessage(key, value ? success : fail, XTableProto.XTableMessage.Type.BOOL); // Using DOUBLE for 8-byte Double
    }

    @Override
    public boolean putDoubleList(String key, List<Double> value) {
        XTableValues.DoubleList.Builder builder = XTableValues.DoubleList.newBuilder()
                .addAllV(value);
        return sendPutMessage(key, builder.build().toByteArray(), XTableProto.XTableMessage.Type.DOUBLE_LIST);
    }

    @Override
    public boolean putStringList(String key, List<String> value) {
        XTableValues.StringList.Builder builder = XTableValues.StringList.newBuilder()
                .addAllV(value);
        return sendPutMessage(key, builder.build().toByteArray(), XTableProto.XTableMessage.Type.STRING_LIST);
    }

    @Override
    public boolean putIntegerList(String key, List<Integer> value) {
        XTableValues.IntegerList.Builder builder = XTableValues.IntegerList.newBuilder()
                .addAllV(value);
        return sendPutMessage(key, builder.build().toByteArray(), XTableProto.XTableMessage.Type.INTEGER_LIST);
    }

    @Override
    public boolean putBytesList(String key, List<ByteString> value) {
        XTableValues.BytesList.Builder builder = XTableValues.BytesList.newBuilder()
                .addAllV(value);
        return sendPutMessage(key, builder.build().toByteArray(), XTableProto.XTableMessage.Type.BYTES_LIST);
    }

    @Override
    public boolean putLongList(String key, List<Long> value) {
        XTableValues.LongList.Builder builder = XTableValues.LongList.newBuilder()
                .addAllV(value);
        return sendPutMessage(key, builder.build().toByteArray(), XTableProto.XTableMessage.Type.LONG_LIST);
    }

    @Override
    public boolean putFloatList(String key, List<Float> value) {
        XTableValues.FloatList.Builder builder = XTableValues.FloatList.newBuilder()
                .addAllV(value);
        return sendPutMessage(key, builder.build().toByteArray(), XTableProto.XTableMessage.Type.FLOAT_LIST);
    }

    @Override
    public boolean putBooleanList(String key, List<Boolean> value) {
        XTableValues.BoolList.Builder builder = XTableValues.BoolList.newBuilder()
                .addAllV(value);
        return sendPutMessage(key, builder.build().toByteArray(), XTableProto.XTableMessage.Type.BOOLEAN_LIST);
    }

    @Override
    public boolean putTypedBytes(String key, XTableProto.XTableMessage.Type type, byte[] value) {
        return sendPutMessage(key, value, type);
    }


    /**
     * Sends a message via the PUSH socket to the server.
     * <p>
     * This helper method builds an XTableMessage and sends it using the PUSH socket. The message contains the specified key, command, value, and type.
     *
     * @param key   The key associated with the value.
     * @param value The byte array representing the value to be sent.
     * @param type  The type of the value being sent (e.g., STRING, INT64, etc.).
     * @return True if the message was sent successfully; otherwise, false.
     */
    private boolean sendPutMessage(String key, byte[] value, XTableProto.XTableMessage.Type type) {
        try {
            return push.send(XTableProto.XTableMessage.newBuilder()
                    .setKey(key)
                    .setCommand(XTableProto.XTableMessage.Command.PUT)
                    .setValue(ByteString.copyFrom(value))
                    .setType(type)
                    .build()
                    .toByteArray(), ZMQ.DONTWAIT);
        } catch (Exception e) {
            throw new XTablesException(e);
        }
    }

    /**
     * Publishes a message with a specified key and value to the push socket.
     * The message is sent with the "PUBLISH" command.
     *
     * @param key   The key associated with the message being published.
     * @param value The value (byte array) to be published with the key.
     * @return true if the message was successfully sent, false otherwise.
     * @throws XTablesException if there is an exception during the publication process.
     */
    @Override
    public boolean publish(String key, byte[] value) {
        try {
            return push.send(XTableProto.XTableMessage.newBuilder()
                    .setKey(key)
                    .setCommand(XTableProto.XTableMessage.Command.PUBLISH)
                    .setValue(ByteString.copyFrom(value))
                    .build()
                    .toByteArray(), ZMQ.DONTWAIT);
        } catch (Exception e) {
            throw new XTablesException(e);
        }
    }

    /**
     * Sends a batch of push requests to the push socket. The requests are sent using
     * the "BATCH" command. The batch is serialized into a single message and sent
     * as a payload.
     *
     * @param batchedPushRequests The collection of push requests to be sent.
     *                            This should contain the data that will be part of
     *                            the batched message.
     * @return true if the batched push requests were successfully sent; false otherwise.
     * @throws XTablesException if any exception occurs during the process of sending
     *                          the batched push requests.
     */
    public boolean sendBatchedPushRequests(BatchedPushRequests batchedPushRequests) {
        try {
            return push.send(XTableProto.XTableMessage.newBuilder()
                    .setCommand(XTableProto.XTableMessage.Command.BATCH)
                    .addAllBatch(batchedPushRequests.getData())
                    .build()
                    .toByteArray(), ZMQ.DONTWAIT);
        } catch (Exception e) {
            throw new XTablesException(e);
        }
    }









    // ------------



    private void reconnectRequestSocket() {
        this.xTablesClient.getSocketMonitor().removeSocket("REQUEST-" + key);
        this.get.close();
        this.get = this.xTablesClient.getContext().createSocket(SocketType.REQ);
        this.xTablesClient.getSocketMonitor().addSocket("REQUEST-" + key, get);
        this.get.setHWM(500);
        this.get.setReceiveTimeOut(3000);
        this.get.connect("tcp://" + this.xTablesClient.getIp() + ":" + this.xTablesClient.getRequestSocketPort());
    }


    /**
     * Retrieves the raw JSON data from the server.
     * <p>
     * This method sends a GET_RAW_JSON request to the server, waits for the response,
     * and then processes the received message.
     * If the message contains a valid value,
     * it decompresses and decodes the Base64-encoded data into a readable JSON string.
     * In case of an error or an invalid response, it returns null.
     * <p>
     * This method handles potential exceptions, including protocol buffer parsing issues
     * and ZeroMQ communication errors, automatically reconnecting the request socket when necessary.
     *
     * @return A string containing the raw JSON data, or null if the request fails or the response is invalid
     */
    @Override
    public String getRawJson() {
        try {
            get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.GET_RAW_JSON).build().toByteArray());
            byte[] response = get.recv();
            if (response == null) return null;
            XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(response);
            if (message.hasValue()) {
                byte[] compressed = message.getValue().toByteArray();
                byte[] decompressed = DataCompression.decompress(compressed);
                if (decompressed == null) return null;
                return new String(decompressed);
            } else return null;
        } catch (InvalidProtocolBufferException e) {
            return null;
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return null;
        }
    }

    /**
     * Reboots the server and checks the success of the operation.
     * <p>
     * This method sends a reboot request to the server and waits for a response.
     * It checks if the server acknowledges the reboot request and returns true if successful.
     *
     * @return true if the server was successfully rebooted, false otherwise
     */
    @Override
    public boolean reboot() {
        try {
            get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.REBOOT_SERVER).build().toByteArray());
            byte[] response = get.recv();
            if (response == null) return false;


            XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(response);
            if (message.hasValue()) return message.getValue().equals(successByte);
            else return false;
        } catch (InvalidProtocolBufferException e) {
            return false;
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return false;
        }
    }

    /**
     * Deletes a table or data identified by the provided key.
     * <p>
     * This method sends a request to delete a table or data.
     * If a key is provided, the request will specify the key.
     * The response is checked to determine if the
     * deletion was successful.
     *
     * @param key The key identifying the table or data to be deleted, or null to delete the default item
     * @return true if the deletion was successful, false otherwise
     */
    @Override
    public boolean delete(String key) {
        try {
            if (key == null) {
                get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.DELETE).build().toByteArray());
            } else {
                get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.DELETE).setKey(key).build().toByteArray());
            }
            byte[] response = get.recv();
            if (response == null) return false;
            XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(response);
            if (!message.hasValue()) return false;
            return message.getValue().equals(successByte);
        } catch (InvalidProtocolBufferException | NullPointerException e) {
            logger.warning(e.getMessage());
            return false;
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return false;
        }
    }

    /**
     * Deletes the default table or data.
     * <p>
     * This method calls the {@link #delete(String)} method with a null value for the key,
     * effectively deleting all the tables.
     *
     * @return true if the deletion was successful, false otherwise
     */
    @Override
    public boolean delete() {
        return delete(null);
    }

    /**
     * Sets the XTABLES debug mode to the specified value.
     * <p>
     * This method constructs and sends a message to toggle the server's debug mode.
     * It verifies the server's response to ensure the operation was successful.
     *
     * @param value a boolean indicating whether to enable or disable debug mode
     * @return true if the server acknowledged the operation successfully, false otherwise
     */
    @Override
    public boolean setServerDebug(boolean value) {
        try {
            get.send(XTableProto.XTableMessage.newBuilder().setValue(value ? successByte : failByte).setCommand(XTableProto.XTableMessage.Command.DEBUG).build().toByteArray());
            byte[] response = get.recv();
            XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(response);
            return message.hasValue() && message.getValue().equals(successByte);
        } catch (InvalidProtocolBufferException | NullPointerException e) {
            return false;
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return false;
        }
    }

    @Override
    public SystemStatistics getServerStatistics() {
        try {
            get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.INFORMATION).build().toByteArray());
            byte[] response = get.recv();
            if (response == null) return null;
            XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(response);
            if (message.hasValue()) {
                return XTablesByteUtils.toObject(message.getValue().toByteArray(), SystemStatistics.class);
            } else {
                return null;
            }
        } catch (InvalidProtocolBufferException | NullPointerException | XTablesException e) {
            return null;
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return null;
        }
    }

    /**
     * Retrieves a list of table names based on the provided key.
     * <p>
     * This method sends a request to retrieve the available tables. If a key is provided,
     * the request includes the key. The response is parsed and returned as a list of strings.
     *
     * @param key The key used to retrieve the tables or null to get all root tables
     * @return A list of table names or an empty list if the request fails
     */
    @Override
    public List<String> getTables(String key) {
        try {
            if (key == null) {
                get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.GET_TABLES).build().toByteArray());
            } else {
                get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.GET_TABLES).setKey(key).build().toByteArray());
            }
            byte[] response = get.recv();
            if (response == null) return new ArrayList<>();
            XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(response);
            if (!message.hasValue()) return new ArrayList<>();
            return XTableValues.StringList.parseFrom(message.getValue()).getVList();
        } catch (InvalidProtocolBufferException | NullPointerException e) {
            return new ArrayList<>();
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return new ArrayList<>();
        }
    }

    /**
     * Retrieves a list of all root table names.
     * <p>
     * This method calls the {@link #getTables(String)} method with a null value for the key,
     * effectively requesting the root tables.
     *
     * @return A list of root table names or an empty list if the request fails
     */
    @Override
    public List<String> getTables() {
        return getTables(null);
    }

    public XTableProto.XTableMessage.XTablesData _getXTablesDataProto() {
        try {
            get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.GET_PROTO_DATA).build().toByteArray());
            byte[] response = get.recv();
            if (response == null) return null;
            XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(response);
            if (message.hasValue()) {
                return XTableProto.XTableMessage.XTablesData.parseFrom(message.getValue());
            } else return null;
        } catch (InvalidProtocolBufferException e) {
            return null;
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return null;
        }
    }


    /**
     * Sends a ping request to the server and measures the round-trip time.
     * <p>
     * This method sends a ping request to the server, measures the time taken for the response,
     * and returns a PingResponse object containing the result of the ping and the round-trip time.
     *
     * @return A PingResponse containing the success status and round-trip time
     */
    @Override
    public PingResponse getPing() {
        try {
            long time = System.nanoTime();
            get.send(XTableProto.XTableMessage.newBuilder().setCommand(XTableProto.XTableMessage.Command.PING).build().toByteArray());
            byte[] response = get.recv();
            long diff = System.nanoTime() - time;
            if (response == null) return new PingResponse(false, -1);
            XTableProto.XTableMessage message = XTableProto.XTableMessage.parseFrom(response);
            if (message.hasValue()) {
                return new PingResponse(message.getValue().equals(successByte), diff);
            } else return new PingResponse(false, -1);
        } catch (InvalidProtocolBufferException e) {
            return new PingResponse(false, -1);
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return new PingResponse(false, -1);
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
            get.send(XTableProto.XTableMessage.newBuilder().setKey(key).setCommand(XTableProto.XTableMessage.Command.GET).build().toByteArray());
            return XTableProto.XTableMessage.parseFrom(get.recv());
        } catch (InvalidProtocolBufferException | NullPointerException e) {
            logger.warning(e.getMessage());
            return null;
        } catch (ZMQException e) {
            logger.warning("ZMQ Exception on request socket, reconnecting to clear states.");
            reconnectRequestSocket();
            return null;
        }
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
    @Override
    public String getString(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.STRING)) {
            return message.getValue().toStringUtf8();
        } else if (message == null || message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN)) {
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
    @Override

    public Integer getInteger(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.INT64)) {
            byte[] valueBytes = message.getValue().toByteArray();
            return ByteBuffer.wrap(valueBytes).getInt();
        } else if (message == null || message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN)) {
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
    @Override

    public Boolean getBoolean(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.BOOL)) {
            return message.getValue().equals(successByte);
        } else if (message == null || message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN)) {
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
    @Override

    public Long getLong(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.INT64)) {
            byte[] valueBytes = message.getValue().toByteArray();
            return ByteBuffer.wrap(valueBytes).getLong();
        } else if (message == null || message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN)) {
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
    @Override

    public Double getDouble(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.DOUBLE)) {
            byte[] valueBytes = message.getValue().toByteArray();
            return ByteBuffer.wrap(valueBytes).getDouble();
        } else if (message == null || message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN)) {
            return null;
        } else {
            throw new IllegalArgumentException("Expected DOUBLE type, but got: " + message.getType());
        }
    }

    @Override

    public byte[] getBytes(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null && message.getType().equals(XTableProto.XTableMessage.Type.BYTES)) {
            return message.getValue().toByteArray();
        } else if (message == null || message.getType().equals(XTableProto.XTableMessage.Type.UNKNOWN)) {
            return null;
        } else {
            throw new IllegalArgumentException("Expected BYTES type, but got: " + message.getType());
        }
    }

    @Override

    public byte[] getUnknownBytes(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message != null) {
            return message.getValue().toByteArray();
        } else {
            throw new IllegalArgumentException("There was no message received back from the XTABLES server.");
        }
    }

    /**
     * Executes a GET request to retrieve a list of coordinates associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is BYTES.
     * If so, it parses the byte array to extract a list of coordinates and returns it.
     * Otherwise, it throws an IllegalArgumentException indicating the wrong type or if parsing fails.
     *
     * @param key The key associated with the list of coordinates.
     * @return A List of `Coordinate` objects associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not BYTES or if parsing fails.
     */
    @Override
    public List<XTableValues.Coordinate> getCoordinates(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message == null) {
            throw new IllegalArgumentException("No message received from the XTABLES server.");
        }
        if (!message.hasValue()) {
            return null;
        }
        if (message.getType() == XTableProto.XTableMessage.Type.BYTES) {
            try {
                return XTableValues.CoordinateList.parseFrom(message.getValue().toByteArray()).getCoordinatesList();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid bytes returned from server: " + Arrays.toString(message.getValue().toByteArray()));
            }
        }


        throw new IllegalArgumentException("Expected BYTES type, but got: " + message.getType());
    }

    /**
     * Retrieves a list of strings associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is STRING_LIST. If so, it parses the byte array to extract the list of strings
     * and returns it. Otherwise, it throws an IllegalArgumentException indicating the wrong type
     * or if parsing fails.
     *
     * @param key The key associated with the list of strings.
     * @return A List of strings associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not STRING_LIST or if parsing fails.
     */
    @Override

    public List<String> getStringList(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message == null) {
            throw new IllegalArgumentException("No message received from the XTABLES server.");
        }
        if (!message.hasValue()) {
            return null;
        }
        if (message.getType() == XTableProto.XTableMessage.Type.STRING_LIST) {
            try {
                return XTableValues.StringList.parseFrom(message.getValue().toByteArray()).getVList();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid bytes returned from server: " + Arrays.toString(message.getValue().toByteArray()));
            }
        }
        throw new IllegalArgumentException("Expected STRING_LIST type, but got: " + message.getType());
    }

    /**
     * Retrieves a list of doubles associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is DOUBLE_LIST. If so, it parses the byte array to extract the list of doubles
     * and returns it. Otherwise, it throws an IllegalArgumentException indicating the wrong type
     * or if parsing fails.
     *
     * @param key The key associated with the list of doubles.
     * @return A List of doubles associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not DOUBLE_LIST or if parsing fails.
     */
    @Override

    public List<Double> getDoubleList(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message == null) {
            throw new IllegalArgumentException("No message received from the XTABLES server.");
        }
        if (!message.hasValue()) {
            return null;
        }
        if (message.getType() == XTableProto.XTableMessage.Type.DOUBLE_LIST) {
            try {
                return XTableValues.DoubleList.parseFrom(message.getValue().toByteArray()).getVList();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid bytes returned from server: " + Arrays.toString(message.getValue().toByteArray()));
            }
        }
        throw new IllegalArgumentException("Expected DOUBLE_LIST type, but got: " + message.getType());
    }

    /**
     * Retrieves a list of integers associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is INTEGER_LIST. If so, it parses the byte array to extract the list of integers
     * and returns it. Otherwise, it throws an IllegalArgumentException indicating the wrong type
     * or if parsing fails.
     *
     * @param key The key associated with the list of integers.
     * @return A List of integers associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not INTEGER_LIST or if parsing fails.
     */
    @Override

    public List<Integer> getIntegerList(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message == null) {
            throw new IllegalArgumentException("No message received from the XTABLES server.");
        }
        if (!message.hasValue()) {
            return null;
        }
        if (message.getType() == XTableProto.XTableMessage.Type.INTEGER_LIST) {
            try {
                return XTableValues.IntegerList.parseFrom(message.getValue().toByteArray()).getVList();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid bytes returned from server: " + Arrays.toString(message.getValue().toByteArray()));
            }
        }
        throw new IllegalArgumentException("Expected INTEGER_LIST type, but got: " + message.getType());
    }

    /**
     * Retrieves a list of ByteString objects associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is BYTES_LIST. If so, it parses the byte array to extract the list of ByteStrings
     * and returns it. Otherwise, it throws an IllegalArgumentException indicating the wrong type
     * or if parsing fails.
     *
     * @param key The key associated with the list of ByteString objects.
     * @return A List of ByteString objects associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not BYTES_LIST or if parsing fails.
     */
    @Override
    public List<ByteString> getBytesList(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message == null) {
            throw new IllegalArgumentException("No message received from the XTABLES server.");
        }
        if (!message.hasValue()) {
            return null;
        }
        if (message.getType() == XTableProto.XTableMessage.Type.BYTES_LIST) {
            try {
                return XTableValues.BytesList.parseFrom(message.getValue().toByteArray()).getVList();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid bytes returned from server: " + Arrays.toString(message.getValue().toByteArray()));
            }
        }
        throw new IllegalArgumentException("Expected BYTES_LIST type, but got: " + message.getType());
    }

    /**
     * Retrieves a list of longs associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is LONG_LIST. If so, it parses the byte array to extract the list of longs
     * and returns it. Otherwise, it throws an IllegalArgumentException indicating the wrong type
     * or if parsing fails.
     *
     * @param key The key associated with the list of longs.
     * @return A List of longs associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not LONG_LIST or if parsing fails.
     */
    @Override
    public List<Long> getLongList(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message == null) {
            throw new IllegalArgumentException("No message received from the XTABLES server.");
        }
        if (!message.hasValue()) {
            return null;
        }
        if (message.getType() == XTableProto.XTableMessage.Type.LONG_LIST) {
            try {
                return XTableValues.LongList.parseFrom(message.getValue().toByteArray()).getVList();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid bytes returned from server: " + Arrays.toString(message.getValue().toByteArray()));
            }
        }
        throw new IllegalArgumentException("Expected LONG_LIST type, but got: " + message.getType());
    }

    /**
     * Retrieves a list of floats associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is FLOAT_LIST. If so, it parses the byte array to extract the list of floats
     * and returns it. Otherwise, it throws an IllegalArgumentException indicating the wrong type
     * or if parsing fails.
     *
     * @param key The key associated with the list of floats.
     * @return A List of floats associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not FLOAT_LIST or if parsing fails.
     */
    @Override
    public List<Float> getFloatList(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message == null) {
            throw new IllegalArgumentException("No message received from the XTABLES server.");
        }
        if (!message.hasValue()) {
            return null;
        }
        if (message.getType() == XTableProto.XTableMessage.Type.FLOAT_LIST) {
            try {
                return XTableValues.FloatList.parseFrom(message.getValue().toByteArray()).getVList();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid bytes returned from server: " + Arrays.toString(message.getValue().toByteArray()));
            }
        }
        throw new IllegalArgumentException("Expected FLOAT_LIST type, but got: " + message.getType());
    }

    /**
     * Retrieves a list of booleans associated with the specified key.
     * <p>
     * This method sends a GET request for the given key and checks if the returned message
     * type is BOOLEAN_LIST. If so, it parses the byte array to extract the list of booleans
     * and returns it. Otherwise, it throws an IllegalArgumentException indicating the wrong type
     * or if parsing fails.
     *
     * @param key The key associated with the list of booleans.
     * @return A List of booleans associated with the given key.
     * @throws IllegalArgumentException If the returned message type is not BOOLEAN_LIST or if parsing fails.
     */
    @Override
    public List<Boolean> getBooleanList(String key) {
        XTableProto.XTableMessage message = getXTableMessage(key);
        if (message == null) {
            throw new IllegalArgumentException("No message received from the XTABLES server.");
        }
        if (!message.hasValue()) {
            return null;
        }
        if (message.getType() == XTableProto.XTableMessage.Type.BOOLEAN_LIST) {
            try {
                return XTableValues.BoolList.parseFrom(message.getValue().toByteArray()).getVList();
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid bytes returned from server: " + Arrays.toString(message.getValue().toByteArray()));
            }
        }
        throw new IllegalArgumentException("Expected BOOLEAN_LIST type, but got: " + message.getType());
    }

    @Override
    public void close() {
        this.push.close();
        this.get.close();
        this.xTablesClient.shutdownXTableContext(this.key);
    }

}
