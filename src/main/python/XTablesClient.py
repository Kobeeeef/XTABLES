import socket
import logging
import threading
import time
import uuid
import json
from zeroconf import Zeroconf, ServiceBrowser, ServiceListener
import Utilities
from enum import Enum
import base64
import zmq


class Status(Enum):
    FAIL = "FAIL"
    OK = "OK"


class XTablesClient:
    def __init__(self, server_ip=None, server_port=None, name=None, zeroMQPullPort=None, zeroMQPubPort=None,
                 useZeroMQ=False):
        self.sub_socket = None
        self.push_socket = None
        self.zeroMQPullPort = zeroMQPullPort
        self.zeroMQPubPort = zeroMQPubPort
        self.out = None
        self.subscriptions = {}
        self.logger = logging.getLogger(__name__)
        self.server_ip = server_ip
        self.server_port = server_port
        self.client_socket = None
        self.service_found = threading.Event()
        self.clientMessageListener = None
        self.shutdown_event = threading.Event()
        self.lock = threading.Lock()
        self.isConnected = False
        self.response_map = {}
        self.response_lock = threading.Lock()
        self.name = name
        self.context = zmq.Context()
        self.useZeroMQ = useZeroMQ
        if self.server_ip and self.server_port:
            self.initialize_client(server_ip=self.server_ip, server_port=self.server_port)
        else:
            self.zeroconf = Zeroconf()
            self.listener = XTablesServiceListener(self)
            self.browser = ServiceBrowser(self.zeroconf, "_xtables._tcp.local.", self.listener)
            self.discover_service()

    def discover_service(self):
        while not self.shutdown_event.is_set():
            try:
                if self.name is None:
                    self.logger.info("Listening for first instance of XTABLES service on port 5353...")
                else:
                    self.logger.info(f"Listening for '{self.name}' XTABLES services on port 5353...")

                # Wait for the service to be found with a timeout
                service_found = self.service_found.wait(timeout=2)  # Wait for 5 seconds

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
                    if self.useZeroMQ:
                        self.push_socket = self.context.socket(zmq.PUSH)
                        self.push_socket.connect(f"tcp://{server_ip}:{self.zeroMQPullPort}")
                        self.sub_socket = self.context.socket(zmq.SUB)
                        self.sub_socket.connect(f"tcp://{server_ip}:{self.zeroMQPubPort}")
                        self.sub_socket.setsockopt(zmq.RCVTIMEO, 0)

                    if self.clientMessageListener:
                        self.logger.info("Stopping previous message listener...")
                        self.clientMessageListener.stop()
                        self.clientMessageListener = None

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

    def deleteTable(self, key=None):
        try:
            if key is not None and isinstance(key, str):
                Utilities.validate_key(key, True)
                response = self.getData("DELETE", key)
                return Status[response.upper()] if response else Status.FAIL
            else:
                response = self.getData("DELETE")
                return Status[response.upper()] if response else Status.FAIL
        except (KeyError, Exception):
            return Status.FAIL

    def rebootServer(self):
        try:

            response = self.getData("REBOOT_SERVER")
            return Status[response.upper()] if response else Status.FAIL
        except (KeyError, Exception):
            return Status.FAIL

    def updateKey(self, oldKey, newKey):
        try:
            if isinstance(oldKey, str) and isinstance(newKey, str):
                Utilities.validate_key(oldKey, True)
                Utilities.validate_key(newKey, True)
                response = self.getData("UPDATE_KEY", f"{oldKey} {newKey}")
                return Status[response.upper()] if response else Status.FAIL
        except (KeyError, Exception):
            return Status.FAIL

    def executePutFloat(self, key, value):
        if isinstance(value, float) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.send_data(f"IGNORED:PUT {key} {value}")
        else:
            raise TypeError("Key must be a string and value must be a float")

    def executePutBytes(self, key, value):
        if isinstance(value, bytes) and isinstance(key, str):
            Utilities.validate_key(key, True)

            # Encode byte array to Base64 string
            base64_value = base64.b64encode(value).decode('utf-8')

            # Send the Base64 string to the server
            self.send_data(f"IGNORED:PUT {key} {base64_value}")
        else:
            self.logger.error("Key must be a string and value must be a byte array (bytes).")

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

    def getData(self, command, value=None, TIMEOUT=3000):
        """
        Generalized method to send a request to the server and retrieve the response.

        :param command: The type of command (e.g., GET or GET_TABLES).
        :param value: The optional key for the command.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The response value or None if the request times out.
        """
        if value is not None and not isinstance(value, str):
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
        if value is None:
            self.send_data(f"{request_id}:{command}")
        else:
            self.send_data(f"{request_id}:{command} {value}")

        # Wait for the response with the specified timeout
        if response_event.wait(TIMEOUT / 1000):  # Convert milliseconds to seconds
            with self.response_lock:
                value = self.response_map.pop(request_id)['value']
            return value
        else:
            # Remove the request if it timed out
            with self.response_lock:
                self.response_map.pop(request_id, None)
            self.logger.error(f"Timeout waiting for response for command: {command}, key: {value}")
            return None

    def getString(self, key, TIMEOUT=3000):
        """
        Retrieves a string value for the given key using the generalized getData method.

        :param key: The key to retrieve.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The string value if successful, or None if the request times out or fails.
        """
        value = self.getData("GET", key, TIMEOUT)
        if value:
            # Parse the result to remove the request ID and return the actual string
            parsed_value = " ".join(value.split(" ")[1:])
            return parse_string(parsed_value)
        return None

    def getTables(self, key=None, TIMEOUT=3000):
        """
        Retrieves the list of tables using the generalized getData method.

        :param key: The optional key for the specific table.
        :param TIMEOUT: Timeout in milliseconds to wait for the response (default is 3000).
        :return: The list of tables if successful, or an empty list if the request fails or times out.
        """
        value = self.getData("GET_TABLES", key, TIMEOUT)
        if value:
            try:
                # Attempt to parse the response value as a JSON array
                array_value = json.loads(parse_string(value))
                if isinstance(array_value, list):
                    return array_value
                else:
                    self.logger.error(f"The GET_TABLES request returned an invalid data structure!")
                    return []
            except (ValueError, json.JSONDecodeError) as e:
                self.logger.error(f"Failed to parse the GET_TABLES response: {e}")
                return []
        return []

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

    def subscribeForAllUpdates(self, consumer):
        """
        Sends a SUBSCRIBE_UPDATE command for the given key and registers the consumer
        to handle update events. Allows multiple consumers to subscribe to the same key.
        Returns True if the subscription was successful, and False otherwise.
        """
        try:
            # Send SUBSCRIBE_UPDATE command
            response = self.getData("SUBSCRIBE_UPDATE")
            if response == "OK":
                self.logger.info(f"Subscribed to all updates.")

                # Add the consumer to the subscriptions map (as a list of consumers)
                if "" not in self.subscriptions:
                    self.subscriptions[""] = []  # Initialize list if not present

                self.subscriptions[""].append(consumer)  # Add consumer to list
                return True
            else:
                self.logger.error(f"Failed to subscribe to all updates.")
                return False
        except Exception as e:
            self.logger.error(f"Error subscribing to all updates: {e}")
            return False

    def subscribeForUpdates(self, key, consumer):
        """
        Sends a SUBSCRIBE_UPDATE command for the given key and registers the consumer
        to handle update events. Allows multiple consumers to subscribe to the same key.
        Returns True if the subscription was successful, and False otherwise.
        """
        try:
            # Send SUBSCRIBE_UPDATE command
            response = self.getData("SUBSCRIBE_UPDATE", key)
            if response == "OK":
                self.logger.info(f"Subscribed to updates for key: {key}")

                # Add the consumer to the subscriptions map (as a list of consumers)
                if key not in self.subscriptions:
                    self.subscriptions[key] = []  # Initialize list if not present

                self.subscriptions[key].append(consumer)  # Add consumer to list
                return True
            else:
                self.logger.error(f"Failed to subscribe to updates for key: {key}")
                return False
        except Exception as e:
            self.logger.error(f"Error subscribing to updates for key {key}: {e}")
            return False

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

    def resubscribe_all(self):
        """
        Re-subscribes to all previously subscribed keys after a reconnection.
        """
        for key, consumer in self.subscriptions.items():
            self.logger.info("Attempting re-subscription to key: " + key)
            if key == "":
                self.subscribeForAllUpdates(consumer)
            else:
                self.subscribeForUpdates(key, consumer)

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

    def recv_next(self, timeout=1500):
        """
        Receives the next message for the given topic (key).
        Supports a timeout.
        """
        # Set the timeout for receiving the message
        if self.sub_socket is None:
            raise Exception("The zeroMQ is not initialized.")

        self.sub_socket.setsockopt(zmq.RCVTIMEO, timeout)

        try:
            message = self.sub_socket.recv_string()
            key, value = message.split(" ", 1)  # Split only at the first space
            return key, value
        except zmq.Again:
            return None, None

    def push_message(self, identifier, message):
        """
        Pushes a message to the server (PUSH side).
        """
        if self.push_socket is None:
            raise Exception("The zeroMQ is not initialized.")

        self.push_socket.send_string(identifier + " " + message)

    def subscribe(self, key):
        """
        Subscribe to a given topic (key).
        """
        self.sub_socket.setsockopt_string(zmq.SUBSCRIBE, key)
        print(f"Subscribed to topic: {key}")

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
        if self.isConnected:
            self.resubscribe_all()

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

    def remove_service(self, zc: "Zeroconf", type_: str, name: str) -> None:
        pass

    def resolve_service(self, info):
        if not self.service_resolved:  # Only resolve if not already resolved
            service_address = socket.inet_ntoa(info.addresses[0])

            port_str = info.properties.get(b'port')
            pull_port_str = info.properties.get(b'pull-port')
            pub_port_str = info.properties.get(b'pub-port')
            pull_port = None
            pub_port = None
            if port_str and (pull_port_str and pub_port_str if self.client.useZeroMQ else True):
                try:
                    port = int(port_str.decode('utf-8'))
                    if self.client.useZeroMQ and pull_port_str and pub_port_str:
                        pull_port = int(pull_port_str.decode('utf-8'))
                        pub_port = int(pub_port_str.decode('utf-8'))
                except ValueError:
                    port = None

                    self.logger.warning("Invalid port format from mDNS attribute. Waiting for next resolve...")
            else:
                port = None
                self.logger.warning("Invalid data from mDNS attribute. Waiting for next resolve...")

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
                if self.client.useZeroMQ:
                    self.logger.info(f"ZeroMQ resolved: {info.name} at PULL {pull_port} and PUB {pub_port}")
                    self.client.zeroMQPullPort = pull_port
                    self.client.zeroMQPubPort = pub_port
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
            if len(parts) < 2:
                self.logger.error(f"Message format invalid: {message} (too few parts)")
                return
            # Extract UUID from the first part by splitting with ':'
            tokens = parts[0].split(":")
            if len(tokens) != 2:
                self.logger.error(f"Invalid token format in message: {message}")
                return
            request_id = tokens[0].strip()
            method_type = tokens[1].strip()
            response_value = " ".join(parts[1:]).strip()

            if method_type == "UPDATE_EVENT":
                if len(parts) < 3:
                    self.logger.error(f"UPDATE_EVENT message invalid: {message}")
                    return
                key = parts[1]
                value = " ".join(parts[2:])
                if "" in self.client.subscriptions:
                    consumers = self.client.subscriptions[""]
                    for consumer in consumers:
                        consumer(key, value)
                if key in self.client.subscriptions:
                    consumers = self.client.subscriptions[key]
                    for consumer in consumers:
                        consumer(key, value)

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


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    client = XTablesClient(useZeroMQ=True)
    client.subscribe("ok")

    while True:
        key, value = client.recv_next()
        print(key)
        print(value)
