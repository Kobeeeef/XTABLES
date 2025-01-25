import struct
import traceback


class XTablesByteUtils:
    def __init__(self):
        pass

    @staticmethod
    def to_int(bytes):
        """
        Converts a byte array into an integer.
        :param bytes: The byte array to convert.
        :return: The integer value represented by the byte array.
        :raises ValueError: If the byte array is None or does not have a length of 4.
        """
        if bytes is None or len(bytes) != 4:
            raise ValueError("Invalid byte array for int conversion.")
        return struct.unpack('>i', bytes)[0]

    @staticmethod
    def to_long(bytes):
        """
        Converts a byte array into a long.
        :param bytes: The byte array to convert.
        :return: The long value represented by the byte array.
        :raises ValueError: If the byte array is None or does not have a length of 8.
        """
        if bytes is None or len(bytes) != 8:
            raise ValueError("Invalid byte array for long conversion.")
        return struct.unpack('>q', bytes)[0]

    @staticmethod
    def to_double(bytes):
        """
        Converts a byte array into a double.
        :param bytes: The byte array to convert.
        :return: The double value represented by the byte array.
        :raises ValueError: If the byte array is None or does not have a length of 8.
        """
        if bytes is None or len(bytes) != 8:
            raise ValueError("Invalid byte array for double conversion.")
        return struct.unpack('>d', bytes)[0]

    @staticmethod
    def to_string(bytes):
        """
        Converts a byte array into a string.
        :param bytes: The byte array to convert.
        :return: The string represented by the byte array.
        :raises ValueError: If the byte array is None.
        """
        if bytes is None:
            raise ValueError("Byte array cannot be None.")
        return bytes.decode('utf-8')

    @staticmethod
    def to_boolean(bytes):
        """
        Converts a byte array into a boolean.
        :param bytes: The byte array to convert.
        :return: The boolean value represented by the byte array.
        :raises ValueError: If the byte array is None or does not have a length of 1.
        """
        if bytes is None or len(bytes) != 1:
            raise ValueError("Invalid byte array for boolean conversion.")
        return bytes[0] == 0x01

    @staticmethod
    def from_integer(i):
        """
        Converts an integer to a byte array.
        :param i: The integer to convert.
        :return: The byte array representation of the integer.
        """
        return struct.pack('>i', i)

    @staticmethod
    def from_long(i):
        """
        Converts a long to a byte array.
        :param i: The long to convert.
        :return: The byte array representation of the long.
        """
        return struct.pack('>q', i)

    @staticmethod
    def from_double(i):
        """
        Converts a double to a byte array.
        :param i: The double to convert.
        :return: The byte array representation of the double.
        """
        return struct.pack('>d', i)

    @staticmethod
    def from_boolean(i):
        """
        Converts a boolean to a byte array.
        :param i: The boolean to convert.
        :return: The byte array representation of the boolean.
        """
        return bytes([0x01 if i else 0x00])



    @staticmethod
    def from_string(i):
        """
        Converts a string to a byte array.
        :param i: The string to convert.
        :return: The byte array representation of the string.
        """
        return i.encode('utf-8')
