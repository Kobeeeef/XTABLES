import socket
import time
import zmq
import struct
import XTableProto_pb2 as XTableProto
from SubscribeHandler import SubscribeHandler
from typing import Callable, Dict, List
import uuid

class XTablesClient:
    # ================================================================
    # Static Variables
    # ================================================================
    SUCCESS_BYTE = bytes([0x01])
    FAIL_BYTE = bytes([0x00])
    # ================================================================
    # Instance Variables
    # ================================================================
    XTABLES_CLIENT_VERSION = "XTABLES Python Client v1.0.0 | Build Date: 12/24/2024"

    def __init__(self, ip=None, push_port=1735, req_port=1736, sub_port=1737):
        self.context = zmq.Context()
        self.push_socket = self.context.socket(zmq.PUSH)
        self.push_socket.set_hwm(500)
        self.req_socket = self.context.socket(zmq.REQ)
        self.req_socket.set_hwm(500)
        self.req_socket.setsockopt(zmq.RCVTIMEO, 3000)
        self.sub_socket = self.context.socket(zmq.SUB)
        self.sub_socket.set_hwm(500)
        self.subscribe_messages_count = 0
        self.subscription_consumers = {}
        self.uuid = str(uuid.uuid4())
        if ip is None:
            ip = self.resolve_host_by_name()

        if ip is None:
            raise XTablesServerNotFound("Could not resolve XTABLES hostname server.")

        print(f"\nConnecting to XTABLES Server...\n"
              f"------------------------------------------------------------\n"
              f"Server IP: {ip}\n"
              f"Push Socket Port: {push_port}\n"
              f"Request Socket Port: {req_port}\n"
              f"Subscribe Socket Port: {sub_port}\n"
              f"------------------------------------------------------------")

        self.push_socket.connect(f"tcp://{ip}:{push_port}")
        self.req_socket.connect(f"tcp://{ip}:{req_port}")
        self.sub_socket.connect(f"tcp://{ip}:{sub_port}")

        self.subscribe_handler = SubscribeHandler(self.sub_socket, self)
        self.subscribe_handler.start()

    def resolve_host_by_name(self):
        try:
            return socket.gethostbyname('XTABLES.local')
        except socket.gaierror:
            return None

    def send_push_message(self, command, key, value, msg_type):
        message = XTableProto.XTableMessage()
        message.key = key
        message.command = command
        message.value = value
        message.type = msg_type
        try:
            self.push_socket.send(message.SerializeToString())
            return True
        except zmq.ZMQError:
            print(f"Error sending message: {e}")
            return False

    def subscribe(self, key: str, consumer: Callable):
        """
        Subscribes to a specific key and associates a consumer to process updates for that key.

        :param key: The key to subscribe to.
        :param consumer: The consumer function that processes updates for the specified key.
        :return: True if the subscription and consumer addition were successful, False otherwise.
        """
        message = XTableProto.XTableMessage.XTableUpdate()
        message.key = key
        bytes_key = message.SerializeToString()
        try:
            self.sub_socket.setsockopt(zmq.SUBSCRIBE, bytes_key)
            if key not in self.subscription_consumers:
                self.subscription_consumers[key] = []
            self.subscription_consumers[key].append(consumer)
            return True
        except zmq.ZMQError as e:
            print(f"Error subscribing to key {key}: {e}")
            return False

    def subscribe_all(self, consumer: Callable):
        """
        Subscribes to updates for all keys and associates a consumer to process updates for all keys.

        :param consumer: The consumer function that processes updates for any key.
        :return: True if the subscription and consumer addition were successful, False otherwise.
        """
        try:
            self.sub_socket.setsockopt(zmq.SUBSCRIBE, "".encode())
            if "" not in self.subscription_consumers:
                self.subscription_consumers[""] = []
            self.subscription_consumers[""].append(consumer)
            return True
        except zmq.ZMQError as e:
            print(f"Error subscribing to all keys: {e}")
            return False

    def unsubscribe(self, key: str, consumer: Callable):
        """
        Unsubscribes a specific consumer from a given key. If no consumers remain for the key,
        it unsubscribes the key from the subscription socket.

        :param key: The key to unsubscribe from.
        :param consumer: The consumer function to remove from the key's subscription.
        :return: True if the consumer was successfully removed or the key was unsubscribed, False otherwise.
        """
        try:
            if key in self.subscription_consumers:
                consumers = self.subscription_consumers[key]
                if consumer in consumers:
                    consumers.remove(consumer)
                    if not consumers:
                        # If no consumers are left for this key, unsubscribe
                        del self.subscription_consumers[key]
                        message = XTableProto.XTableMessage.XTableUpdate()
                        message.key = key
                        bytes_key = message.SerializeToString()
                        self.sub_socket.setsockopt(zmq.UNSUBSCRIBE, bytes_key)
                    return True
            return False
        except zmq.ZMQError as e:
            print(f"Error unsubscribing to key {key}: {e}")
            return False

    def unsubscribe_all(self, consumer: Callable):
        """
        Unsubscribes a specific consumer from all keys. If no consumers remain for all keys,
        it unsubscribes from the subscription socket for all keys.

        :param consumer: The consumer function to remove from all key subscriptions.
        :return: True if the consumer was successfully removed or unsubscribed from all keys, False otherwise.
        """
        try:
            if "" in self.subscription_consumers:
                consumers = self.subscription_consumers[""]
                if consumer in consumers:
                    consumers.remove(consumer)
                    if not consumers:
                        # If no consumers are left for all keys, unsubscribe from all
                        del self.subscription_consumers[""]
                        self.sub_socket.setsockopt(zmq.UNSUBSCRIBE, "".encode())
                    return True
            return False
        except zmq.ZMQError as e:
            print(f"Error subscribing to all keys: {e}")
            return False

    # ====================
    # PUT Methods
    # ====================
    def putBytes(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value,
                                      XTableProto.XTableMessage.Type.BYTES)

    def putUnknownBytes(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value,
                                      XTableProto.XTableMessage.Type.UNKNOWN)

    def putString(self, key, value):
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value.encode('utf-8'),
                                      XTableProto.XTableMessage.Type.STRING)

    def putInteger(self, key, value):
        value_bytes = struct.pack('!i', value)
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
                                      XTableProto.XTableMessage.Type.INT64)

    def putLong(self, key, value):
        value_bytes = struct.pack('!q', value)
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
                                      XTableProto.XTableMessage.Type.INT64)

    def putDouble(self, key, value):
        value_bytes = struct.pack('!d', value)
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
                                      XTableProto.XTableMessage.Type.DOUBLE)

    def putBoolean(self, key, value):
        value_bytes = self.SUCCESS_BYTE if value else self.FAIL_BYTE
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, value_bytes,
                                      XTableProto.XTableMessage.Type.BOOL)

    def putArray(self, key, values):
        """
        Handles putting arrays to the server.
        Assumes `values` is an array of values that can be serialized into a byte array.
        """
        serialized_array = [struct.pack('!i', value) for value in values]  # Example with integers
        array_value = b''.join(serialized_array)
        return self.send_push_message(XTableProto.XTableMessage.Command.PUT, key, array_value,
                                      XTableProto.XTableMessage.Type.ARRAY)

    # ====================
    # GET Methods
    # ====================

    def getString(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.STRING:
            return message.value.decode('utf-8')
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected STRING type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getInteger(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.INT64:
            return struct.unpack('!i', message.value)[0]
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected INT type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getBoolean(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.BOOL:
            return message.value == self.SUCCESS_BYTE
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected BOOL type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getLong(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.INT64:
            return struct.unpack('!q', message.value)[0]
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected LONG type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getDouble(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.DOUBLE:
            return struct.unpack('!d', message.value)[0]
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected DOUBLE type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getArray(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.ARRAY:
            return message.value
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected ARRAY type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getBytes(self, key):
        message = self._get_xtable_message(key)
        if message is not None and message.type == XTableProto.XTableMessage.Type.BYTES:
            return message.value
        elif message is None or message.type == XTableProto.XTableMessage.Type.UNKNOWN:
            return None
        else:
            raise ValueError(f"Expected ARRAY type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def getUnknownBytes(self, key):
        message = self._get_xtable_message(key)
        if message is not None:
            return message.value
        else:
            raise ValueError(f"Expected ARRAY type, but got: {XTableProto.XTableMessage.Type.Name(message.type)}")

    def _get_xtable_message(self, key):
        try:
            message = XTableProto.XTableMessage()
            message.key = key
            message.command = XTableProto.XTableMessage.Command.GET
            self.req_socket.send(message.SerializeToString())
            response_bytes = self.req_socket.recv()
            response_message = XTableProto.XTableMessage.FromString(response_bytes)

            return response_message

        except Exception as e:
            print(f"Error occurred: {e}")
            return None

    # ====================
    # Version and Properties Methods
    # ====================
    def get_version(self):
        return self.XTABLES_CLIENT_VERSION

    def add_client_version_property(self, value):
        self.XTABLES_CLIENT_VERSION = self.XTABLES_CLIENT_VERSION + " | " + value


class XTablesServerNotFound(Exception):
    pass


def consumer(test):
    print("UPDATE: " + test.key + " " + str(test.value) + " TYPE: " + XTableProto.XTableMessage.Type.Name(test.type))
    time.sleep(0.1)


# Sample usage
if __name__ == "__main__":
    client = XTablesClient("localhost")
    client.subscribe_all(consumer)
    while True:
        time.sleep(10)
    # print(client.getUnknownBytes("name"))
    # print(client.getString("age"))
    # print(client.getArray("numbers"))
