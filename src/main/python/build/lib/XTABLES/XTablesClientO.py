import json
import logging
import socket
import threading
import time
from enum import Enum
from io import BytesIO
import random
import struct
import XTableProto_pb2 as XTableProto
import SocketClient as sc
import Utilities
from zeroconf import Zeroconf, ServiceBrowser, ServiceListener
import ast


class Status(Enum):
    FAIL = "FAIL"
    OK = "OK"


class XTablesClient:
    """*********************************CLIENT*********************************"""

    def __init__(self, server_ip=None, server_port=1735, name=None):
        logging.basicConfig(level=logging.INFO)
        self.server_ip = server_ip
        self.server_port = server_port
        self.name = name
        self.shutdown_event = threading.Event()
        self.service_found = threading.Event()
        self.logger = logging.getLogger(__name__)
        self.socket_client = None
        if self.server_ip and self.server_port:
            self._initialize_client(server_ip=self.server_ip, server_port=self.server_port)
        elif self.server_port:
            try:

                self.logger.info("Attempting to resolve IP address using OS resolver.")
                self.server_ip = socket.gethostbyname("XTABLES.local")

                self._initialize_client(server_ip=self.server_ip, server_port=self.server_port)
            except Exception:
                self.logger.fatal("Failed to resolve XTABLES server. Falling back to mDNS.")
                self.zeroconf = Zeroconf()
                self.listener = self.XTablesServiceListener(self)
                self.browser = ServiceBrowser(self.zeroconf, "_xtables._tcp.local.", self.listener)
                self.discover_service()
        else:
            self.zeroconf = Zeroconf()
            self.listener = self.XTablesServiceListener(self)
            self.browser = ServiceBrowser(self.zeroconf, "_xtables._tcp.local.", self.listener)
            self.discover_service()

    def discover_service(self):
        while not self.shutdown_event.is_set():
            try:
                if self.name is None:
                    self.logger.info("Listening for first instance of XTABLES service on port 5353...")
                else:
                    self.logger.info(f"Listening for '{self.name}' XTABLES services on port 5353...")
                service_found = self.service_found.wait(timeout=2)

                if service_found:
                    self.logger.info("Service found, proceeding to close mDNS services...")
                    self.zeroconf.close()
                    self.logger.info("mDNS service closed.")
                    self._initialize_client(self.server_ip, self.server_port)
                    break
                else:
                    self.logger.info("Service not found, retrying discovery...")
                    self.zeroconf = Zeroconf()
                    self.browser = ServiceBrowser(self.zeroconf, "_xtables._tcp.local.", self.listener)
            except Exception as e:
                self.logger.error(f"Error during service discovery: {e}. Retrying...")
                time.sleep(1)

    class XTablesServiceListener(ServiceListener):
        def __init__(self, client):
            self.client = client
            self.logger = logging.getLogger(__name__)
            self.service_resolved = False

        def add_service(self, zeroconf, service_type, name):
            if not self.service_resolved:
                info = zeroconf.get_service_info(service_type, name)
                if info:
                    self.resolve_service(info)

        def remove_service(self, zc: "Zeroconf", type_: str, name: str) -> None:
            pass

        def resolve_service(self, info):
            if not self.service_resolved:
                service_address = socket.inet_ntoa(info.addresses[0])

                port_str = info.properties.get(b'port')
                if port_str:
                    try:
                        port = int(port_str.decode('utf-8'))

                    except ValueError:
                        port = None
                else:
                    port = None
                if self.client.name and self.client.name.lower() == "localhost":
                    try:
                        service_address = socket.gethostbyname(socket.gethostname())
                    except Exception as e:
                        self.logger.fatal(f"Could not find localhost address: {e}")

                if (port is not None) and service_address and (
                        self.client.name in [None, "localhost", info.name, service_address]):
                    self.logger.info(f"Service resolved: {info.name} at {service_address}:{port}")
                    self.client.server_ip = service_address
                    self.client.server_port = port
                    self.client.service_found.set()
                    self.service_resolved = True

    def _initialize_client(self, server_ip, server_port):
        self.socket_client = sc.SocketClient(server_ip, server_port)
        self.socket_client.connect()

    def shutdown(self):
        self.shutdown_event.set()
        self.socket_client.shutdown()

    """*********************************CLIENT*********************************"""

    """--------------------------------METHODS--------------------------------"""

    def subscribe_to_all(self, consumer):
        return self.socket_client.subscribeForAllUpdates(consumer)

    def subscribe_to_key(self, key, consumer):
        return self.socket_client.subscribeForUpdates(key, consumer)

    def getTables(self, key=None, TIMEOUT=3000):
        """
        Retrieves the list of tables using the generalized send_request_and_retrieve method.

        :param key: The optional key for the specific table.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The list of tables if successful, or an empty list if the request fails or times out.
        """
        message = XTableProto.XTableMessage()
        message.command = XTableProto.XTableMessage.Command.GET_TABLES
        if key is not None:
            message.key = key
        value = self.socket_client.send_request_and_retrieve(message, TIMEOUT)
        if value:
            try:
                # Attempt to parse the response value as a JSON array
                array_value = value.decode('utf-8').split(',')
                if isinstance(array_value, list):
                    return array_value
                else:
                    self.logger.error(f"The GET_TABLES request returned an invalid data structure!")
                    return []
            except (ValueError, json.JSONDecodeError) as e:
                self.logger.error(f"Failed to parse the GET_TABLES response: {e}")
                return []
        return []

    def getString(self, key, TIMEOUT=3000):
        """

        :param key: The key to retrieve the string for.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The decoded string if successful, or None if the request fails.
        """
        byte_data = self.getBytes(key, TIMEOUT)
        if byte_data:
            return byte_data.decode('utf-8')  # Assuming UTF-8 encoding for the string
        return None

    def getInteger(self, key, TIMEOUT=3000):
        """

        :param key: The key to retrieve the integer for.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The decoded integer if successful, or None if the request fails.
        """
        byte_data = self.getBytes(key, TIMEOUT)
        if byte_data:
            return int.from_bytes(byte_data, byteorder='big', signed=True)  # Assuming the integer is signed
        return None

    def getBoolean(self, key, TIMEOUT=3000):
        """
        Retrieves a Base64-encoded boolean from the server and converts it into a boolean.

        :param key: The key to retrieve the boolean for.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The decoded boolean (True or False) if successful, or None if the request fails.
        """
        byte_data = self.getBytes(key, TIMEOUT)
        if byte_data and len(byte_data) == 1:
            return byte_data == bytes([0x01])  # 0 is False, any non-zero is True
        return None

    def getDouble(self, key, TIMEOUT=3000):
        """
        Retrieves a Base64-encoded double from the server and converts it into a double.

        :param key: The key to retrieve the double for.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The decoded double if successful, or None if the request fails.
        """
        byte_data = self.getBytes(key, TIMEOUT)
        if byte_data and len(byte_data) >= 8:
            # Convert byte array to double
            return struct.unpack('!d', byte_data)[0]
        return None

    def getBytes(self, key, TIMEOUT=3000):
        """

        :param key: The key to retrieve the byte array for.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The byte array if successful, or None if the request fails.
        """
        # Use the existing getString method to fetch the Base64-encoded string
        message = XTableProto.XTableMessage()
        message.command = XTableProto.XTableMessage.Command.GET
        message.key = key
        return self.socket_client.send_request_and_retrieve(message, TIMEOUT)

    def executePutBytes(self, key, value):
        if isinstance(value, bytes) and isinstance(key, str):
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUT
            message.type = XTableProto.XTableMessage.Type.BYTES
            message.key = key
            message.value = value
            self.socket_client.send_message(message)

    def executePublishBytes(self, key, value):
        if isinstance(value, str) and isinstance(key, str):
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUBLISH
            message.type = XTableProto.XTableMessage.Type.BYTES
            message.key = key
            message.value = value
            self.socket_client.send_message(message)

    def executePutBoolean(self, key, value):
        if isinstance(value, bool) and isinstance(key, str):
            Utilities.validate_key(key, True)
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUT
            message.type = XTableProto.XTableMessage.Type.BOOL
            message.key = key
            message.value = bytes([0x01]) if value else bytes([0x00])
            self.socket_client.send_message(message)

    def executePutString(self, key, value):
        if isinstance(value, str) and isinstance(key, str):
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUT
            message.type = XTableProto.XTableMessage.Type.STRING
            message.key = key
            message.value = value.encode("UTF-8")
            self.socket_client.send_message(message)

    def executePublishString(self, key, value):
        if isinstance(value, str) and isinstance(key, str):
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUBLISH
            message.type = XTableProto.XTableMessage.Type.STRING
            message.key = key
            message.value = value.encode("UTF-8")
            self.socket_client.send_message(message)

    def executePutInteger(self, key, value):
        if isinstance(value, int) and isinstance(key, str):
            Utilities.validate_key(key, True)
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUT
            message.type = XTableProto.XTableMessage.Type.INT64
            message.key = key
            message.value = (value.to_bytes((value.bit_length() + 7) // 8, byteorder='big') or b'\0').replace(b'\n',
                                                                                                              b'')
            self.socket_client.send_message(message)

    def executePutDouble(self, key, value):
        if isinstance(value, float) and isinstance(key, str):
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUT
            message.type = XTableProto.XTableMessage.Type.DOUBLE
            message.key = key
            message.value = struct.pack('!d', value)
            self.socket_client.send_message(message)

    def executePutArrayOrTuple(self, key, value):
        if isinstance(key, str) and isinstance(value, (list, tuple)):
            Utilities.validate_key(key, True)

            # Convert the list or tuple into a string with elements separated by a delimiter
            formatted_values = ",".join(str(item) for item in value)

            # Convert the string to bytes (UTF-8 encoding)
            formatted_values_bytes = formatted_values.encode('utf-8')

            # Set the message with the formatted byte array
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUT
            message.type = XTableProto.XTableMessage.Type.BYTES
            message.key = key
            message.value = formatted_values_bytes  # Set the formatted byte array

            self.socket_client.send_message(message)

        else:
            raise TypeError("Key must be a string and value must be a list or tuple")

    import json

    def executePutClass(self, key, obj):
        """
        Serializes the given class object into a JSON string and sends it to the server.

        :param key: The key under which the class data will be stored.
        :param obj: The class object to be serialized and sent.
        """
        if isinstance(key, str) and obj is not None:
            # Convert the class object to a JSON string
            json_string = json.dumps(obj, default=lambda o: o.__dict__)

            # Convert the JSON string to bytes (UTF-8 encoding)
            json_bytes = json_string.encode('utf-8')

            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.PUT
            message.type = XTableProto.XTableMessage.Type.STRING
            message.key = key
            message.value = json_bytes
            self.socket_client.send_message(message)

        else:
            self.logger.error("Invalid key or object provided.")

    def deleteTable(self, key=None):
        try:
            if key is not None and isinstance(key, str):
                Utilities.validate_key(key, True)
                message = XTableProto.XTableMessage()
                message.command = XTableProto.XTableMessage.Command.DELETE
                message.key = key
                response = self.socket_client.send_request_and_retrieve(message)
                return Status["OK" if response == bytes([0x01]) else "FAIL"] if response else Status.FAIL
            else:
                message = XTableProto.XTableMessage()
                message.command = XTableProto.XTableMessage.Command.DELETE
                response = self.socket_client.send_request_and_retrieve(message)

                return Status["OK" if response == bytes([0x01]) else "FAIL"] if response else Status.FAIL
        except (KeyError, Exception):
            return Status.FAIL

    def rebootServer(self):
        try:
            message = XTableProto.XTableMessage()
            message.command = XTableProto.XTableMessage.Command.REBOOT_SERVER

            response = self.socket_client.send_request_and_retrieve(message)
            return Status["OK" if response == bytes([0x01]) else "FAIL"] if response else Status.FAIL
        except (KeyError, Exception):
            return Status.FAIL

    def updateKey(self, oldKey, newKey):
        try:
            if isinstance(oldKey, str) and isinstance(newKey, str):
                Utilities.validate_key(oldKey, True)
                Utilities.validate_key(newKey, True)
                response = self.socket_client.send_request_and_retrieve("UPDATE_KEY", f"{oldKey} {newKey}")
                return Status[response.upper()] if response else Status.FAIL
        except (KeyError, Exception):
            return Status.FAIL

    """--------------------------------METHODS--------------------------------"""


def image_to_bytes(image_path):
    with open(image_path, 'rb') as image_file:
        image_bytes = image_file.read()  # Read the image as bytes
    return image_bytes


def consumer(message):
    time.sleep(1)
    print(int.from_bytes(message.value, byteorder='big'))


if __name__ == "__main__":
    c = XTablesClient(server_port=1735, server_ip="localhost")
    # Usage example
    image_path = 'D:\\stuff\\IdeaProjects\\XTABLES\\src\\main\\python\\DSC_0841_1.jpg'
    image_bytes = image_to_bytes(image_path).replace(b'\n', b'')
    # i = 0
    # while True:
    #     c.executePutBytes("test", b'ad')
    # c.subscribe_to_key("test", consumer)

    print(c.deleteTable())
    time.sleep(1000000)
