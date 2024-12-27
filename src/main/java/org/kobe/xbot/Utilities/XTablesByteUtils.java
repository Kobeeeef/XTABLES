package org.kobe.xbot.Utilities;

import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
}
