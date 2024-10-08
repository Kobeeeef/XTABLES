import socket
import logging
import threading
import time
import uuid
import json
from zeroconf import Zeroconf, ServiceBrowser, ServiceListener
import Utilities


class XTablesClient:
    def __init__(self, name=None):
        self.out = None
        self.name = name
        self.server_ip = None
        self.server_port = None
        self.client_socket = None
        self.service_found = threading.Event()
        self.logger = logging.getLogger(__name__)
        self.zeroconf = Zeroconf()
        self.listener = XTablesServiceListener(self)
        self.browser = ServiceBrowser(self.zeroconf, "_xtables._tcp.local.", self.listener)
        self.clientMessageListener = None
        self.shutdown_event = threading.Event()
        self.lock = threading.Lock()
        self.isConnected = False  # Variable to track connection status
        self.response_map = {}  # Map to track UUID responses
        self.response_lock = threading.Lock()  # Lock for thread-safe access to response_map
        self.discover_service()

    def discover_service(self):
        while not self.shutdown_event.is_set():
            try:
                if self.name is None:
                    self.logger.info("Listening for first instance of XTABLES service on port 5353...")
                else:
                    self.logger.info(f"Listening for '{self.name}' XTABLES services on port 5353...")

                # Wait for the service to be found with a timeout
                service_found = self.service_found.wait(timeout=5)  # Wait for 5 seconds

                if service_found:
                    self.logger.info("Service found, proceeding to close mDNS services...")
                    self.zeroconf.close()
                    self.logger.info("mDNS service closed.")
                    self.initialize_client(self.server_ip, self.server_port)
                    break
                else:
                    self.logger.info("Service not found, retrying discovery...")
                    # Reinitialize the service browser to keep trying
                    self.zeroconf = Zeroconf()
                    self.browser = ServiceBrowser(self.zeroconf, "_xtables._tcp.local.", self.listener)
            except Exception as e:
                self.logger.error(f"Error during service discovery: {e}. Retrying...")
                time.sleep(1)

    def initialize_client(self, server_ip, server_port):
        self.close_socket()
        self.isConnected = False  # Set to False when starting to reconnect

        with self.lock:  # Ensure no concurrent connections
            while not self.shutdown_event.is_set():
                try:
                    self.logger.info(f"Connecting to server {server_ip}:{server_port}")
                    self.client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    self.client_socket.connect((server_ip, 1735))
                    self.logger.info(f"Connected to server {server_ip}:{server_port}")
                    self.out = self.client_socket.makefile('w')
                    self.isConnected = True  # Set to True when connection is established

                    # Stop the old message listener if it exists
                    if self.clientMessageListener:
                        self.logger.info("Stopping previous message listener...")
                        self.clientMessageListener.stop()
                        self.clientMessageListener = None

                    # Start a new message listener
                    self.clientMessageListener = ClientMessageListener(self)
                    self.clientMessageListener.start()
                    break
                except socket.error as e:
                    self.logger.error(f"Connection error: {e}. Retrying immediately.")
                    time.sleep(1)

    def send_data(self, data):
        try:
            if self.client_socket and self.isConnected:
                self.out.write(data + "\n")
                self.out.flush()
        except socket.error as e:
            self.logger.error(f"Error sending data: {e}")

    def executePutBoolean(self, key, value):
        if isinstance(value, bool) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.send_data(f"IGNORED:PUT {key} {str(value).lower()}")

    def executePutString(self, key, value):
        if isinstance(value, str) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.send_data(f'IGNORED:PUT {key} "{value}"')

    def executePutInteger(self, key, value):
        if isinstance(value, int) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.send_data(f"IGNORED:PUT {key} {value}")

    def executePutArrayOrTuple(self, key, value):
        if isinstance(key, str) and isinstance(value, (list, tuple)):
            Utilities.validate_key(key, True)
            # Only convert to a list if it's a tuple, otherwise keep it as is
            formatted_values = str(value if isinstance(value, list) else list(value))
            self.send_data(f"IGNORED:PUT {key} {formatted_values}")
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
            self.send_data(f'IGNORED:PUT {key} {json_string}')
        else:
            self.logger.error("Invalid key or object provided.")

    def getString(self, key, TIMEOUT=3000):
        if not isinstance(key, str):
            raise ValueError("Key must be a string.")

        # Generate a unique UUID for the request
        request_id = str(uuid.uuid4())
        response_event = threading.Event()

        # Register the request in the response map
        with self.response_lock:
            self.response_map[request_id] = {
                'event': response_event,
                'value': None
            }

        # Send the GET request to the server
        self.send_data(f"{request_id}:GET {key}")

        # Wait for the response with the specified timeout
        if response_event.wait(TIMEOUT / 1000):  # Convert milliseconds to seconds
            with self.response_lock:
                value = self.response_map.pop(request_id)['value']
            return parse_string(value)
        else:
            # Remove the request if it timed out
            with self.response_lock:
                self.response_map.pop(request_id, None)
            self.logger.error(f"Timeout waiting for response for key: {key}")
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
                # Attempt to parse the JSON string and convert it to an instance of the class

                json_dict = json.loads(json_string.replace('\\"', '"'))  # Parse the JSON string into a dictionary
                return class_type(**json_dict)  # Create an instance of the class using the dictionary
            except (ValueError, TypeError) as e:
                self.logger.error(f"Error parsing value for key '{key}' into class {class_type.__name__}: {e}")
                return None
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

    def close_socket(self):
        with self.lock:  # Ensure cleanup is thread-safe
            if self.client_socket:
                try:
                    self.client_socket.close()
                    self.logger.info("Socket closed.")
                except socket.error as e:
                    self.logger.error(f"Close socket error: {e}")
                finally:
                    self.client_socket = None

            if self.clientMessageListener:
                self.logger.info("Stopping message listener...")
                self.clientMessageListener.stop()
                self.clientMessageListener = None

    def handle_reconnect(self):
        self.logger.info("Attempting to reconnect...")
        self.isConnected = False
        self.initialize_client(self.server_ip, self.server_port)

    def shutdown(self):
        self.shutdown_event.set()
        if self.clientMessageListener:
            self.clientMessageListener.stop()
        self.close_socket()
        self.isConnected = False


class XTablesServiceListener(ServiceListener):
    def __init__(self, client):
        self.client = client
        self.logger = logging.getLogger(__name__)
        self.service_resolved = False  # Flag to avoid multiple resolutions

    def add_service(self, zeroconf, service_type, name):
        if not self.service_resolved:
            self.logger.info(f"Service found: {name}")
            info = zeroconf.get_service_info(service_type, name)
            if info:
                self.resolve_service(info)

    def resolve_service(self, info):
        if not self.service_resolved:  # Only resolve if not already resolved
            service_address = socket.inet_ntoa(info.addresses[0])
            port = info.port
            port_str = info.properties.get(b'port')

            if port_str:
                try:
                    port = int(port_str.decode('utf-8'))
                except ValueError:
                    self.logger.warning("Invalid port format from mDNS attribute. Waiting for next resolve...")

            if self.client.name and self.client.name.lower() == "localhost":
                try:
                    service_address = socket.gethostbyname(socket.gethostname())
                except Exception as e:
                    self.logger.fatal(f"Could not find localhost address: {e}")

            if port != -1 and service_address and (self.client.name in [None, "localhost", info.name, service_address]):
                self.logger.info(f"Service resolved: {info.name} at {service_address}:{port}")
                self.client.server_ip = service_address
                self.client.server_port = port
                self.client.service_found.set()
                self.service_resolved = True


class ClientMessageListener(threading.Thread):
    def __init__(self, client: XTablesClient):
        super().__init__()
        self.client = client
        self.client_socket = client.client_socket
        self.in_ = self.client_socket.makefile('r')
        self.logger = logging.getLogger(__name__)
        self.stop_event = threading.Event()

    def run(self):
        while not self.stop_event.is_set():
            try:
                message = self.in_.readline().strip()
                if message:

                    self.process_message(message)
                else:
                    self.logger.warning("Received an empty message, attempting reconnection...")
                    self.client.isConnected = False
                    self.stop()
                    self.client.handle_reconnect()
            except Exception as e:
                self.logger.error(f"Error in message listener: {e}")
                self.client.isConnected = False
                self.stop_event.set()
                self.client.handle_reconnect()

    def process_message(self, message):
        try:
            # Split the message by spaces
            parts = message.split(" ")

            # Extract UUID from the first part by splitting with ':'
            request_id = parts[0].split(":")[0].strip()

            # Extract the value, which is everything after the first index
            response_value = " ".join(parts[2:]).strip()

            # Check if the request_id matches any pending requests
            with self.client.response_lock:
                if request_id in self.client.response_map:
                    # Set the extracted value to the response map
                    self.client.response_map[request_id]['value'] = response_value
                    self.client.response_map[request_id]['event'].set()
        except Exception as e:
            self.logger.error(f"Invalid message format: {message}. Error: {e}")

    def stop(self):
        self.stop_event.set()
        self.logger.info("Message listener stopped.")


def parse_string(s):
    # Check if the string starts and ends with \" and remove them
    if s.startswith('"\\"') and s.endswith('\\""'):
        s = s[3:-3]
    elif s.startswith('"') and s.endswith('"'):
        s = s[1:-1]
    return s

# if __name__ == "__main__":
#     logging.basicConfig(level=logging.INFO)
#     client = XTablesClient()
#     while True:
#         print(client.getArray("SmartDashboard.exampleClass"))
