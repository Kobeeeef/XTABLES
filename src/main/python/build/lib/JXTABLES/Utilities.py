import msgpack


def serialize_object(obj):
    """
    Utility method to serialize an object into a byte array.

    This method uses MessagePack to serialize the given object into a byte array.
    This is useful for storing or transmitting objects in a compact binary format
    across different systems.

    :param obj: The object to be serialized.
    :return: A byte array containing the serialized object.
    """
    return msgpack.packb(obj, use_bin_type=True)  # use_bin_type ensures compatibility with binary data


def deserialize_object(bytes_obj, cls):
    """
    Utility method to deserialize a byte array back into an object of the specified class.

    This method uses MessagePack to deserialize the byte array back into an object of the specified class.
    The object is returned as a dictionary, and you can convert it into an instance of the given class.

    :param bytes_obj: The byte array containing the serialized object.
    :param cls: The class type to which the object should be deserialized.
    :return: The deserialized object of the specified class type.
    """
    # Deserialize the byte array into a dictionary
    unpacked = msgpack.unpackb(bytes_obj, raw=False)

    # Optionally, convert the dictionary to the specified class instance
    obj = cls(**unpacked)  # assuming the class constructor takes keyword arguments
    return obj
