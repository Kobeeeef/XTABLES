import json
import logging
import socket
import threading
import time
from enum import Enum
from io import BytesIO

from . import SocketClient as sc
from . import Utilities
from zeroconf import Zeroconf, ServiceBrowser, ServiceListener


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
            except Exception:
                self.logger.fatal("Failed to resolve XTABLES server. Falling back to mDNS.")
                self.zeroconf = Zeroconf()
                self.listener = self.XTablesServiceListener(self)
                self.browser = ServiceBrowser(self.zeroconf, "_xtables._tcp.local.", self.listener)
                self.discover_service()
            self._initialize_client(server_ip=self.server_ip, server_port=self.server_port)
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
        value = self.socket_client.send_request_and_retrieve("GET_TABLES", key, TIMEOUT)
        if value:
            try:
                # Attempt to parse the response value as a JSON array
                array_value = json.loads(value)
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
        Retrieves a string value for the given key using the generalized method.

        :param key: The key to retrieve.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The string value if successful, or None if the request times out or fails.
        """
        value = self.socket_client.send_request_and_retrieve("GET", key, TIMEOUT)
        if value == "null":
            return None
        if value:
            return Utilities.parse_string(value)
        return None

    def getClass(self, key, class_type, TIMEOUT=3000):
        """
        Retrieves a class object from the server for the given key by first fetching it as a JSON string
        and then converting it to an instance of the specified class type.

        :param key: The key for which to get the class object.
        :param class_type: The class type to deserialize the JSON string into.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: An instance of the class if successful, or None if the key is not found or an error occurs.
        """
        # Use the existing getString method to fetch the value as a string
        json_string = self.getString(key, TIMEOUT)

        if json_string is not None:
            try:
                json_dict = json.loads(json_string.replace('\\"', '"'))
                return class_type(**json_dict)
            except (ValueError, TypeError) as e:
                self.logger.error(f"Error parsing value for key '{key}' into class {class_type.__name__}: {e}")
                return None
        else:
            return None

    def getArray(self, key, TIMEOUT=3000):
        """
        Retrieves an array (list) value from the server for the given key by fetching it as a string
        and then converting it to a Python list.

        :param key: The key for which to get the array value.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The array as a Python list if successful, or None if the key is not found or the conversion fails.
        """
        # Use the existing getString method to fetch the value as a string
        string_value = self.getString(key, TIMEOUT)

        if string_value is not None:
            try:
                # Attempt to convert the string value to a list using json.loads
                array_value = json.loads(string_value)

                if isinstance(array_value, list):
                    return array_value
                else:
                    self.logger.error(f"Value for key '{key}' is not a valid array: {string_value}")
                    return None
            except (ValueError, json.JSONDecodeError) as e:
                self.logger.error(f"Failed to parse array for key '{key}': {e}")
                return None
        else:
            return None

    def getFloat(self, key, TIMEOUT=3000):
        """
        Retrieves a float value from the server for the given key by fetching it as a string
        and then converting it to a float.

        :param key: The key for which to get the float value.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The float value if successful, or None if the key is not found or the conversion fails.
        """
        # Use the existing getString method to fetch the value as a string
        string_value = self.getString(key, TIMEOUT)

        if string_value is not None:
            try:
                # Attempt to convert the string value to a float
                return float(string_value)
            except ValueError:
                self.logger.error(f"Value for key '{key}' is not a valid float: {string_value}")
                return None
        else:
            return None

    def getBoolean(self, key, TIMEOUT=3000):
        """
        Retrieves a boolean value from the server for the given key by first fetching it as a string
        and then converting it to a boolean.

        :param key: The key for which to get the boolean value.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The boolean value if successful, or None if the key is not found or the conversion fails.
        """
        string_value = self.getString(key, TIMEOUT)

        if string_value is not None:
            string_value = string_value.strip().lower()
            if string_value in ['true', '1']:
                return True
            elif string_value in ['false', '0']:
                return False
            else:
                self.logger.error(f"Value for key '{key}' is not a valid boolean: {string_value}")
                return None
        else:
            return None

    def getValue(self, key, TIMEOUT=3000):
        """
        Retrieves a value from the server for the given key and returns it in its appropriate Python type.
        The method automatically handles arrays (lists), dictionaries, booleans, integers, floats, and strings.

        :param TIMEOUT: The amount of time to wait before returning None
        :param key: The key for which to get the value. param TIMEOUT: Timeout in milliseconds to wait for the
        response (default is 3000). :return: The value in its appropriate Python type if successful, or None if the
        key is not found or the conversion fails.
        """
        # Use the existing getString method to fetch the value as a string
        string_value = self.getString(key, TIMEOUT)

        if string_value is not None:
            try:
                # Try to load the value as a JSON object first (for lists, dicts, etc.)
                value = json.loads(string_value)

                # Return the value directly if it is a known Python type (list, dict, etc.)
                if isinstance(value, (list, dict, int, float, bool, tuple)):
                    return value
                else:
                    self.logger.error(f"Value for key '{key}' is of an unsupported type: {type(value).__name__}")
                    return None
            except (ValueError, json.JSONDecodeError):
                # If JSON loading fails, it's likely a simple string
                return string_value
        else:
            return None

    def getInteger(self, key, TIMEOUT=3000):
        """
        Retrieves an integer value from the server for the given key by first fetching it as a string
        and then converting it to an integer.

        :param key: The key for which to get the integer value.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The integer value if successful, or None if the key is not found or the conversion fails.
        """
        # Use the existing getString method to fetch the value as a string
        string_value = self.getString(key, TIMEOUT)

        if string_value is not None:
            try:
                # Attempt to convert the string value to an integer
                return int(string_value)
            except ValueError:
                self.logger.error(f"Value for key '{key}' is not a valid integer: {string_value}")
                return None
        else:
            return None

    def getBytes(self, key, TIMEOUT=3000):
        """
        Retrieves a Base64-encoded string from the server and decodes it back into a byte array.

        :param key: The key to retrieve the byte array for.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The byte array if successful, or None if the request fails.
        """
        # Use the existing getString method to fetch the Base64-encoded string
        base64_string = self.getString(key, TIMEOUT)

        if base64_string is not None:
            try:
                # Decode the Base64 string back to a byte array
                return base64.b64decode(base64_string)
            except (ValueError, TypeError) as e:
                self.logger.error(f"Error decoding Base64 value for key '{key}': {e}")
                return None
        else:
            return None

    def executePutBytes(self, key, value):
        if isinstance(value, bytes) and isinstance(key, str):
            Utilities.validate_key(key, True)

            # Encode byte array to Base64 string
            base64_value = base64.b64encode(value).decode('utf-8')

            # Send the Base64 string to the server
            self.send_data(f"IGNORED:PUT {key} {base64_value}")
        else:
            self.logger.error("Key must be a string and value must be a byte array (bytes).")

    def executePutBoolean(self, key, value):
        if isinstance(value, bool) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.socket_client.send_message(f"IGNORED:PUT {key} {str(value).lower()}")

    def executePutString(self, key, value):
        if isinstance(value, str) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.socket_client.send_message(f'IGNORED:PUT {key} "{value}"')

    def executePutInteger(self, key, value):
        if isinstance(value, int) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.socket_client.send_message(f"IGNORED:PUT {key} {value}")

    def deleteTable(self, key=None):
        try:
            if key is not None and isinstance(key, str):
                Utilities.validate_key(key, True)
                response = self.socket_client.send_request_and_retrieve("DELETE", key)
                return Status[response.upper()] if response else Status.FAIL
            else:
                response = self.socket_client.send_request_and_retrieve("DELETE")
                return Status[response.upper()] if response else Status.FAIL
        except (KeyError, Exception):
            return Status.FAIL

    def rebootServer(self):
        try:

            response = self.socket_client.send_request_and_retrieve("REBOOT_SERVER")
            return Status[response.upper()] if response else Status.FAIL
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

    def executePutFloat(self, key, value):
        if isinstance(value, float) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.socket_client.send_message(f"IGNORED:PUT {key} {value}")
        else:
            raise TypeError("Key must be a string and value must be a float")

    def executePutBytes(self, key, value):
        if isinstance(value, bytes) and isinstance(key, str):
            Utilities.validate_key(key, True)

            # Encode byte array to Base64 string
            base64_value = base64.b64encode(value).decode('utf-8')

            # Send the Base64 string to the server
            self.socket_client.send_message(f"IGNORED:PUT {key} {base64_value}")
        else:
            self.logger.error("Key must be a string and value must be a byte array (bytes).")

    def executePutArrayOrTuple(self, key, value):
        if isinstance(key, str) and isinstance(value, (list, tuple)):
            Utilities.validate_key(key, True)
            # Only convert to a list if it's a tuple, otherwise keep it as is
            formatted_values = str(value if isinstance(value, list) else list(value))
            self.socket_client.send_message(f"IGNORED:PUT {key} {formatted_values}")
        else:
            raise TypeError("Key must be a string and value must be a list or tuple")

    def executePutClass(self, key, obj):
        """
        Serializes the given class object into a JSON string and sends it to the server.

        :param key: The key under which the class data will be stored.
        :param obj: The class object to be serialized and sent.
        """
        if isinstance(key, str) and obj is not None:
            # Convert the class object to a JSON string
            json_string = json.dumps(obj, default=lambda o: o.__dict__)

            Utilities.validate_key(key, True)
            self.socket_client.send_message(f'IGNORED:PUT {key} {json_string}')
        else:
            self.logger.error("Invalid key or object provided.")

    """--------------------------------METHODS--------------------------------"""

if __name__ == "__main__":
    c = XTablesClient("10.4.88.175", 1735)

    def a(key, value):
        print(f"{key}, {value}")


    c.subscribe_to_all(a)
