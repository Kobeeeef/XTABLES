package org.kobe.xbot.Utilities;

import com.google.gson.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Rotation3d;
import org.kobe.xbot.Utilities.Entities.XTableProto;
import org.kobe.xbot.Utilities.Entities.XTableValues;
import org.kobe.xbot.Utilities.Exceptions.XTablesException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * XTablesByteUtils - A utility class for converting between byte arrays, ByteString, and various data types.
 * <p>
 * This class provides static methods for converting byte arrays and ByteString objects into common Java types such as
 * int, long, double, String, and boolean. These conversions are useful for handling raw byte data in a protocol-agnostic manner.
 * <p>
 * Author: Kobe Lei
 * Version: 1.0
 * Package: org.kobe.xbot.Utilities
 * <p>
 * This class is a part of the XTABLES project and provides essential utility functions for byte conversion in data processing.
 */
public class XTablesByteUtils {
    private static final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .setLenient()
            .create();

    public static String convertXTableUpdateToJsonString(XTableProto.XTableMessage.XTableUpdate node) {
        return convertTypeValueToJsonString(node.getType(), node.getValue().toByteArray());
    }


    public static Map.Entry<XTableProto.XTableMessage.Type, byte[]> convertJsonStringToTypeValue(String json) {
        JsonElement jsonElement = JsonParser.parseString(json);

        if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();

            if (primitive.isString()) {
                return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.STRING, primitive.getAsString().getBytes());
            } else if (primitive.isNumber()) {
                if (primitive.getAsString().contains(".")) {
                    return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.DOUBLE, doubleToBytes(primitive.getAsDouble()));
                } else {
                    return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.INT64, longToBytes(primitive.getAsLong()));
                }
            } else if (primitive.isBoolean()) {
                return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.BOOL, new byte[]{(byte) (primitive.getAsBoolean() ? 0x01 : 0x00)});
            }
        } else if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();

            if (jsonArray.size() > 0) {
                JsonElement firstElement = jsonArray.get(0);

                if (firstElement.isJsonPrimitive()) {
                    JsonPrimitive firstPrimitive = firstElement.getAsJsonPrimitive();

                    if (firstPrimitive.isNumber()) {
                        if (firstPrimitive.getAsString().contains(".")) {
                            return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.DOUBLE_LIST, serializeList(jsonArray, XTableValues.DoubleList.newBuilder()));
                        } else {
                            return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.LONG_LIST, serializeList(jsonArray, XTableValues.LongList.newBuilder()));
                        }
                    } else if (firstPrimitive.isString()) {
                        return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.STRING_LIST, serializeList(jsonArray, XTableValues.StringList.newBuilder()));
                    } else if (firstPrimitive.isBoolean()) {
                        return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.BOOLEAN_LIST, serializeList(jsonArray, XTableValues.BoolList.newBuilder()));
                    }
                } else {
                    return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.BYTES_LIST, jsonArrayToByteArray(jsonArray));
                }
            }
        }

        return new AbstractMap.SimpleEntry<>(XTableProto.XTableMessage.Type.UNKNOWN, new byte[0]);
    }

    private static byte[] doubleToBytes(double value) {
        return ByteBuffer.allocate(Double.BYTES).putDouble(value).array();
    }

    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static byte[] serializeList(JsonArray jsonArray, Message.Builder builder) {
        for (JsonElement element : jsonArray) {
            if (builder instanceof XTableValues.DoubleList.Builder dblBuilder) {
                dblBuilder.addV(element.getAsDouble());
            } else if (builder instanceof XTableValues.LongList.Builder longBuilder) {
                longBuilder.addV(element.getAsLong());
            } else if (builder instanceof XTableValues.StringList.Builder strBuilder) {
                strBuilder.addV(element.getAsString());
            } else if (builder instanceof XTableValues.BoolList.Builder boolBuilder) {
                boolBuilder.addV(element.getAsBoolean());
            }
        }
        return builder.build().toByteArray();
    }

    private static byte[] jsonArrayToByteArray(JsonArray jsonArray) {
        byte[] byteArray = new byte[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            byteArray[i] = (byte) jsonArray.get(i).getAsInt();
        }
        return byteArray;
    }

    public static XTableValues.Coordinate getCoordinate(int x, int y) {
        return XTableValues.Coordinate.newBuilder().setX(x).setY(y).build();
    }

    public static List<XTableValues.Coordinate> getCoordinateList(XTableValues.Coordinate... coordinate) {
        return new ArrayList<>(Arrays.asList(coordinate));
    }

    public static XTableValues.CoordinateList getCoordinateListProto(XTableValues.Coordinate... coordinate) {
        return XTableValues.CoordinateList.newBuilder().addAllCoordinates(List.of(coordinate)).buildPartial();
    }
    /**
     * Converts a list of Coordinate protobuf objects into a human-readable string.
     *
     * @param coordinateList The CoordinateList protobuf object.
     * @return A formatted string representing the list of coordinates.
     */
    public static String coordinateListToString(XTableValues.CoordinateList coordinateList) {
        if (coordinateList == null || coordinateList.getCoordinatesList().isEmpty()) {
            return "No Coordinates Available";
        }

        StringBuilder sb = new StringBuilder("Coordinates: [");
        List<XTableValues.Coordinate> coordinates = coordinateList.getCoordinatesList();

        for (int i = 0; i < coordinates.size(); i++) {
            XTableValues.Coordinate coord = coordinates.get(i);
            sb.append(String.format("(X: %.2f, Y: %.2f)", coord.getX(), coord.getY()));
            if (i < coordinates.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    /**
     * Converts a raw protobuf byte array into a human-readable string of coordinates.
     *
     * @param rawData The byte array representing a serialized CoordinateList.
     * @return A formatted string representation of the list of coordinates, or an error message if parsing fails.
     */
    public static String coordinateListToString(byte[] rawData) {
        try {
            XTableValues.CoordinateList coordinateList = XTableValues.CoordinateList.parseFrom(rawData);
            return coordinateListToString(coordinateList);
        } catch (Exception e) {
            return "Invalid CoordinateList Data";
        }
    }
    public static String convertTypeValueToJsonString(XTableProto.XTableMessage.Type type, byte[] value) {
        JsonElement jsonElement = switch (type) {
            case STRING -> new JsonPrimitive(new String(value));
            case POSE2D -> new JsonPrimitive(XTablesByteUtils.pose2dToString(value));
            case POSE3D -> new JsonPrimitive(XTablesByteUtils.pose3dToString(value));
            case COORDINATES -> new JsonPrimitive(XTablesByteUtils.coordinateListToString(value));
            case INT64 -> new JsonPrimitive(bytesToLong(value));
            case BOOL -> new JsonPrimitive(value[0] == 0x01);
            case DOUBLE -> new JsonPrimitive(bytesToDouble(value));
            case FLOAT_LIST -> parseList(value, XTableValues.FloatList.parser());
            case DOUBLE_LIST -> parseList(value, XTableValues.DoubleList.parser());
            case STRING_LIST -> parseList(value, XTableValues.StringList.parser());
            case INTEGER_LIST -> parseList(value, XTableValues.IntegerList.parser());
            case LONG_LIST -> parseList(value, XTableValues.LongList.parser());
            case BOOLEAN_LIST -> parseList(value, XTableValues.BoolList.parser());
            case BYTES_LIST, BYTES, UNKNOWN -> {
                JsonArray byteArray = new JsonArray();
                for (byte b : value) {
                    byteArray.add(b);
                }
                yield byteArray;
            }
            default -> JsonNull.INSTANCE;
        };

        return gson.toJson(jsonElement);
    }

    /**
     * Generic helper function to parse repeated Protobuf lists into JSON.
     */
    private static <T extends com.google.protobuf.Message> JsonArray parseList(
            byte[] value,
            com.google.protobuf.Parser<T> parser
    ) {
        JsonArray jsonArray = new JsonArray();
        try {
            T parsedMessage = parser.parseFrom(value);
            if (parsedMessage instanceof XTableValues.FloatList floatList) {
                floatList.getVList().forEach(f -> jsonArray.add(new JsonPrimitive(f)));
            } else if (parsedMessage instanceof XTableValues.DoubleList doubleList) {
                doubleList.getVList().forEach(d -> jsonArray.add(new JsonPrimitive(d)));
            } else if (parsedMessage instanceof XTableValues.StringList stringList) {
                stringList.getVList().forEach(jsonArray::add);
            } else if (parsedMessage instanceof XTableValues.IntegerList intList) {
                intList.getVList().forEach(i -> jsonArray.add(new JsonPrimitive(i)));
            } else if (parsedMessage instanceof XTableValues.LongList longList) {
                longList.getVList().forEach(l -> jsonArray.add(new JsonPrimitive(l)));
            } else if (parsedMessage instanceof XTableValues.BoolList boolList) {
                boolList.getVList().forEach(b -> jsonArray.add(new JsonPrimitive(b)));
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to parse list: " + parser.getClass().getSimpleName(), e);
        }
        return jsonArray;
    }


    /**
     * Converts a byte array into an integer.
     * <p>
     * This method interprets the first four bytes of the byte array as an integer. It expects the byte array to have a length of 4.
     *
     * @param bytes The byte array to convert.
     * @return The integer value represented by the byte array.
     * @throws IllegalArgumentException If the byte array is null or does not have a length of 4.
     */
    public static int toInt(byte[] bytes) {
        if (bytes == null || bytes.length != Integer.BYTES) {
            throw new IllegalArgumentException("Invalid byte array for int conversion.");
        }
        return ByteBuffer.wrap(bytes).getInt();
    }

    /**
     * Converts a byte array into a long.
     * <p>
     * This method interprets the first eight bytes of the byte array as a long. It expects the byte array to have a length of 8.
     *
     * @param bytes The byte array to convert.
     * @return The long value represented by the byte array.
     * @throws IllegalArgumentException If the byte array is null or does not have a length of 8.
     */
    public static long toLong(byte[] bytes) {
        if (bytes == null || bytes.length != Long.BYTES) {
            throw new IllegalArgumentException("Invalid byte array for long conversion.");
        }
        return ByteBuffer.wrap(bytes).getLong();
    }

    /**
     * Converts a byte array into a double.
     * <p>
     * This method interprets the first eight bytes of the byte array as a double. It expects the byte array to have a length of 8.
     *
     * @param bytes The byte array to convert.
     * @return The double value represented by the byte array.
     * @throws IllegalArgumentException If the byte array is null or does not have a length of 8.
     */
    public static double toDouble(byte[] bytes) {
        if (bytes == null || bytes.length != Double.BYTES) {
            throw new IllegalArgumentException("Invalid byte array for double conversion.");
        }
        return ByteBuffer.wrap(bytes).getDouble();
    }

    /**
     * Converts a byte array into a string.
     * <p>
     * This method decodes the byte array into a string using UTF-8 encoding.
     *
     * @param bytes The byte array to convert.
     * @return The string represented by the byte array.
     * @throws IllegalArgumentException If the byte array is null.
     */
    public static String toString(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null.");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Converts a byte array into a boolean.
     * <p>
     * This method checks if the first byte of the byte array is equal to 0x01, which represents true in boolean form.
     *
     * @param bytes The byte array to convert.
     * @return The boolean value represented by the byte array.
     * @throws IllegalArgumentException If the byte array is null or does not have a length of 1.
     */
    public static boolean toBoolean(byte[] bytes) {
        if (bytes == null || bytes.length != 1) {
            throw new IllegalArgumentException("Invalid byte array for boolean conversion.");
        }
        return bytes[0] == (byte) 0x01;
    }

    /**
     * Converts a ByteString into an integer.
     * <p>
     * This method interprets the first four bytes of the ByteString as an integer. It expects the ByteString to have a size of 4.
     *
     * @param byteString The ByteString to convert.
     * @return The integer value represented by the ByteString.
     * @throws IllegalArgumentException If the ByteString is null or does not have a size of 4.
     */
    public static int toInt(ByteString byteString) {
        if (byteString == null || byteString.size() != Integer.BYTES) {
            throw new IllegalArgumentException("Invalid ByteString for int conversion.");
        }
        return ByteBuffer.wrap(byteString.toByteArray()).getInt();
    }

    /**
     * Converts a ByteString into a long.
     * <p>
     * This method interprets the first eight bytes of the ByteString as a long. It expects the ByteString to have a size of 8.
     *
     * @param byteString The ByteString to convert.
     * @return The long value represented by the ByteString.
     * @throws IllegalArgumentException If the ByteString is null or does not have a size of 8.
     */
    public static long toLong(ByteString byteString) {
        if (byteString == null || byteString.size() != Long.BYTES) {
            throw new IllegalArgumentException("Invalid ByteString for long conversion.");
        }
        return ByteBuffer.wrap(byteString.toByteArray()).getLong();
    }

    /**
     * Converts a ByteString into a double.
     * <p>
     * This method interprets the first eight bytes of the ByteString as a double. It expects the ByteString to have a size of 8.
     *
     * @param byteString The ByteString to convert.
     * @return The double value represented by the ByteString.
     * @throws IllegalArgumentException If the ByteString is null or does not have a size of 8.
     */
    public static double toDouble(ByteString byteString) {
        if (byteString == null || byteString.size() != Double.BYTES) {
            throw new IllegalArgumentException("Invalid ByteString for double conversion.");
        }
        return ByteBuffer.wrap(byteString.toByteArray()).getDouble();
    }

    /**
     * Converts a ByteString into a string.
     * <p>
     * This method decodes the ByteString into a string using UTF-8 encoding.
     *
     * @param byteString The ByteString to convert.
     * @return The string represented by the ByteString.
     * @throws IllegalArgumentException If the ByteString is null.
     */
    public static String toString(ByteString byteString) {
        if (byteString == null) {
            throw new IllegalArgumentException("ByteString cannot be null.");
        }
        return new String(byteString.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Converts a ByteString into a boolean.
     * <p>
     * This method checks if the first byte of the ByteString is equal to 0x01, which represents true in boolean form.
     *
     * @param byteString The ByteString to convert.
     * @return The boolean value represented by the ByteString.
     * @throws IllegalArgumentException If the ByteString is null or does not have a size of 1.
     */
    public static boolean toBoolean(ByteString byteString) {
        if (byteString == null || byteString.size() != 1) {
            throw new IllegalArgumentException("Invalid ByteString for boolean conversion.");
        }
        return byteString.byteAt(0) == (byte) 0x01;
    }


    /**
     * Converts an integer into a byte array.
     * <p>
     * This method converts the provided integer into a 4-byte array.
     *
     * @param i The integer to convert.
     * @return The byte array representing the integer.
     */
    public static byte[] fromInteger(int i) {
        return ByteBuffer.allocate(4).putInt(i).array();
    }

    /**
     * Converts a long into a byte array.
     * <p>
     * This method converts the provided long into an 8-byte array.
     *
     * @param i The long to convert.
     * @return The byte array representing the long.
     */
    public static byte[] fromLong(long i) {
        return ByteBuffer.allocate(8).putLong(i).array();
    }

    /**
     * Converts a double into a byte array.
     * <p>
     * This method converts the provided double into an 8-byte array.
     *
     * @param i The double to convert.
     * @return The byte array representing the double.
     */
    public static byte[] fromDouble(double i) {
        return ByteBuffer.allocate(8).putDouble(i).array();
    }

    /**
     * Converts a boolean into a byte array.
     * <p>
     * This method converts the provided boolean into a byte array.
     * True is represented by 0x01, and false by 0x00.
     *
     * @param i The boolean to convert.
     * @return The byte array representing the boolean.
     */
    public static byte[] fromBoolean(boolean i) {
        return i ? new byte[]{(byte) 0x01} : new byte[]{(byte) 0x00};
    }


    /**
     * Converts a string into a byte array.
     * <p>
     * This method converts the provided string into a byte array using UTF-8 encoding.
     *
     * @param i The string to convert.
     * @return The byte array representing the string.
     */
    public static byte[] fromString(String i) {
        return i.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Converts an object into a byte array.
     * <p>
     * This method serializes the provided object into a JSON string using Gson,
     * then converts the JSON string into a byte array using UTF-8 encoding.
     *
     * @param v The object to convert.
     * @return A byte array representing the object in JSON format.
     */
    public static byte[] fromObject(Object v) {
        return fromString(gson.toJson(v));
    }


    /**
     * Converts a byte array back into an object of the specified class type.
     * <p>
     * This method deserializes the byte array into a JSON string, then uses Gson
     * to convert the JSON string into an object of the specified type.
     *
     * @param v     The byte array to convert.
     * @param clazz The class type of the object to return.
     * @param <T>   The type of the object.
     * @return The deserialized object of type T.
     * @throws XTablesException If there is an error during deserialization.
     */
    public static <T> T toObject(byte[] v, Class<T> clazz) {
        try {
            return gson.fromJson(toString(v), clazz);
        } catch (JsonSyntaxException e) {
            throw new XTablesException(e);
        }
    }


    private static long bytesToLong(byte[] bytes) {
        if (bytes.length > 8) {
            throw new IllegalArgumentException("Byte array is too large to fit in a long");
        }

        long result = 0;
        for (int i = 0; i < bytes.length; i++) {
            result |= (bytes[i] & 0xFFL) << ((bytes.length - 1 - i) * 8);
        }
        return result;
    }


    private static double bytesToDouble(byte[] bytes) {
        if (bytes.length >= 8) {
            long longBits = ((long) bytes[0] << 56) |
                    ((long) (bytes[1] & 0xFF) << 48) |
                    ((long) (bytes[2] & 0xFF) << 40) |
                    ((long) (bytes[3] & 0xFF) << 32) |
                    ((long) (bytes[4] & 0xFF) << 24) |
                    ((long) (bytes[5] & 0xFF) << 16) |
                    ((long) (bytes[6] & 0xFF) << 8) |
                    ((long) (bytes[7] & 0xFF));
            return Double.longBitsToDouble(longBits);
        }
        return 0.0;
    }

    /**
     * Packs a Pose3d object into a byte array.
     *
     * @param pose The Pose3d object to pack.
     * @return A byte array representation of the Pose3d object.
     */
    public static byte[] packPose3d(Pose3d pose) {
        try {
            ByteBuffer bb = ByteBuffer.allocate(Double.BYTES * 6); // x, y, z, roll, pitch, yaw
            bb.order(ByteOrder.LITTLE_ENDIAN);

            bb.putDouble(pose.getX());
            bb.putDouble(pose.getY());
            bb.putDouble(pose.getZ());
            bb.putDouble(pose.getRotation().getX());
            bb.putDouble(pose.getRotation().getY());
            bb.putDouble(pose.getRotation().getZ());

            return bb.array();
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null if packing fails
        }
    }

    /**
     * Converts a Pose3d byte array into a human-readable string.
     * If deserialization fails, it returns "Invalid Pose3d Data".
     *
     * @param data The byte array representing a serialized Pose3d.
     * @return A human-readable string representation of the Pose3d.
     */
    public static String pose3dToString(byte[] data) {
        Pose3d pose = unpackPose3d(data);
        if (pose == null) {
            return "Invalid Pose3d Data";
        }
        return pose3dToString(pose);
    }

    /**
     * Converts a Pose3d object into a human-readable string.
     * If deserialization fails, it returns "Invalid Pose3d Data".
     *
     * @param pose The Pose3d object.
     * @return A human-readable string representation of the Pose3d.
     */
    public static String pose3dToString(Pose3d pose) {
        if (pose == null) {
            return "Invalid Pose3d Data";
        }
        return String.format("Pose3d(X: %.2f m, Y: %.2f m, Z: %.2f m, Roll: %.2f°, Pitch: %.2f°, Yaw: %.2f°)",
                pose.getX(), pose.getY(), pose.getZ(),
                Math.toDegrees(pose.getRotation().getX()),
                Math.toDegrees(pose.getRotation().getY()),
                Math.toDegrees(pose.getRotation().getZ()));
    }

    /**
     * Unpacks a byte array into a Pose3d object.
     *
     * @param data The byte array to unpack.
     * @return A Pose3d object if successful, or null if unpacking fails.
     */
    public static Pose3d unpackPose3d(byte[] data) {
        try {
            if (data == null || data.length != Double.BYTES * 6) {
                return null; // Invalid input length
            }

            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.LITTLE_ENDIAN);

            double x = bb.getDouble();
            double y = bb.getDouble();
            double z = bb.getDouble();
            double roll = bb.getDouble();
            double pitch = bb.getDouble();
            double yaw = bb.getDouble();

            return new Pose3d(x, y, z, new Rotation3d(roll, pitch, yaw));
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null on failure
        }
    }
    /**
     * Packs a Pose2d object into a byte array.
     *
     * @param pose The Pose2d object to pack.
     * @return A byte array representation of the Pose2d object.
     */
    public static byte[] packPose2d(Pose2d pose) {
        try {
            ByteBuffer bb = ByteBuffer.allocate(Double.BYTES * 3); // x, y, rotation
            bb.order(ByteOrder.LITTLE_ENDIAN);

            bb.putDouble(pose.getX());
            bb.putDouble(pose.getY());
            bb.putDouble(pose.getRotation().getRadians());

            return bb.array();
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null if packing fails
        }
    }

    /**
     * Converts a Pose2d byte array into a human-readable string.
     * If deserialization fails, it returns "Invalid Pose2d Data".
     *
     * @param data The byte array representing a serialized Pose2d.
     * @return A human-readable string representation of the Pose2d.
     */
    public static String pose2dToString(byte[] data) {
        Pose2d pose = unpackPose2d(data);
        if (pose == null) {
            return "Invalid Pose2d Data";
        }
        double degrees = Math.toDegrees(pose.getRotation().getRadians());
        return String.format("Pose2d(X: %.2f m, Y: %.2f m, Rotation: %.2f°)", pose.getX(), pose.getY(), degrees);
    }


    /**
     * Converts a Pose2d byte array into a human-readable string.
     * If deserialization fails, it returns "Invalid Pose2d Data".
     *
     * @param pose The pose
     * @return A human-readable string representation of the Pose2d.
     */
    public static String pose2dToString(Pose2d pose) {
        if (pose == null) {
            return "Invalid Pose2d Data";
        }
        double degrees = Math.toDegrees(pose.getRotation().getRadians());
        return String.format("Pose2d(X: %.2f m, Y: %.2f m, Rotation: %.2f°)", pose.getX(), pose.getY(), degrees);
    }

    /**
     * Unpacks a byte array into a Pose2d object.
     *
     * @param data The byte array to unpack.
     * @return A Pose2d object if successful, or null if unpacking fails.
     */
    public static Pose2d unpackPose2d(byte[] data) {
        try {
            if (data == null || data.length != Double.BYTES * 3) {
                return null; // Invalid input length
            }

            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.LITTLE_ENDIAN);

            double x = bb.getDouble();
            double y = bb.getDouble();
            double rotation = bb.getDouble();

            return new Pose2d(x, y, new Rotation2d(rotation));
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null on failure
        }
    }
}



