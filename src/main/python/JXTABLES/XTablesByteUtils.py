import struct
import traceback
import math


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

    @staticmethod
    def pack_pose3d(pose):
        """
        Packs a (x, y, z, roll_radians, pitch_radians, yaw_radians) tuple into a byte array.

        :param pose: A tuple (x, y, z, roll_radians, pitch_radians, yaw_radians).
        :return: A byte array representation of the Pose3d.
        """
        try:
            x, y, z, roll, pitch, yaw = pose
            return struct.pack('<dddddd', x, y, z, roll, pitch, yaw)  # Little-endian double precision floats
        except Exception as e:
            print(f"Error packing Pose3d: {e}")
            return None  # Return None if packing fails

    @staticmethod
    def unpack_pose3d(data):
        """
        Unpacks a byte array into a (x, y, z, roll_radians, pitch_radians, yaw_radians) tuple.

        :param data: The byte array to unpack.
        :return: A (x, y, z, roll_radians, pitch_radians, yaw_radians) tuple if successful, or None if unpacking fails.
        """
        try:
            if data is None or len(data) != 48:  # 6 doubles (8 bytes each)
                return None  # Invalid input length
            return struct.unpack('<dddddd', data)
        except Exception as e:
            print(f"Error unpacking Pose3d: {e}")
            return None  # Return None on failure

    @staticmethod
    def pose3d_to_string(data):
        """
        Converts a Pose3d byte array into a human-readable string.
        If deserialization fails, it returns "Invalid Pose3d Data".

        :param data: The byte array representing a serialized Pose3d.
        :return: A human-readable string representation of the Pose3d.
        """
        pose = Pose3dUtils.unpack_pose3d(data)
        if pose is None:
            return "Invalid Pose3d Data"

        return Pose3dUtils.pose3d_tuple_to_string(pose)

    @staticmethod
    def pose3d_tuple_to_string(pose):
        """
        Converts a (x, y, z, roll_radians, pitch_radians, yaw_radians) tuple into a human-readable string.

        :param pose: A tuple (x, y, z, roll_radians, pitch_radians, yaw_radians).
        :return: A human-readable string representation of the Pose3d.
        """
        if pose is None:
            return "Invalid Pose3d Data"

        x, y, z, roll, pitch, yaw = pose
        roll_degrees = math.degrees(roll)
        pitch_degrees = math.degrees(pitch)
        yaw_degrees = math.degrees(yaw)
        return f"Pose3d(X: {x:.2f} m, Y: {y:.2f} m, Z: {z:.2f} m, Roll: {roll_degrees:.2f}°, Pitch: {pitch_degrees:.2f}°, Yaw: {yaw_degrees:.2f}°)"

    @staticmethod
    def pack_pose2d(pose):
        """
        Packs a (x, y, rotation_radians) tuple into a byte array.

        :param pose: A tuple (x, y, rotation_radians).
        :return: A byte array representation of the Pose2d.
        """
        try:
            x, y, rotation = pose
            return struct.pack('<ddd', x, y, rotation)  # Little-endian double precision floats
        except Exception as e:
            print(f"Error packing Pose2d: {e}")
            return None  # Return None if packing fails

    @staticmethod
    def unpack_pose2d(data):
        """
        Unpacks a byte array into a (x, y, rotation_radians) tuple.

        :param data: The byte array to unpack.
        :return: A (x, y, rotation_radians) tuple if successful, or None if unpacking fails.
        """
        try:
            if data is None or len(data) != 24:  # 3 doubles (8 bytes each)
                return None  # Invalid input length
            return struct.unpack('<ddd', data)
        except Exception as e:
            print(f"Error unpacking Pose2d: {e}")
            return None  # Return None on failure

    @staticmethod
    def pose2d_to_string(data):
        """
        Converts a Pose2d byte array into a human-readable string.
        If deserialization fails, it returns "Invalid Pose2d Data".

        :param data: The byte array representing a serialized Pose2d.
        :return: A human-readable string representation of the Pose2d.
        """
        pose = unpack_pose2d(data)
        if pose is None:
            return "Invalid Pose2d Data"

        x, y, rotation_radians = pose
        rotation_degrees = math.degrees(rotation_radians)
        return f"Pose2d(X: {x:.2f} m, Y: {y:.2f} m, Rotation: {rotation_degrees:.2f}°)"

    @staticmethod
    def pose2d_tuple_to_string(pose):
        """
        Converts a (x, y, rotation_radians) tuple into a human-readable string.

        :param pose: A tuple (x, y, rotation_radians).
        :return: A human-readable string representation of the Pose2d.
        """
        if pose is None:
            return "Invalid Pose2d Data"

        x, y, rotation_radians = pose
        rotation_degrees = math.degrees(rotation_radians)
        return f"Pose2d(X: {x:.2f} m, Y: {y:.2f} m, Rotation: {rotation_degrees:.2f}°)"
