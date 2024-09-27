import socket
import logging
import threading
import time
import uuid
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
        if self.name is None:
            self.logger.info("Listening for first instance of XTABLES service on port 5353...")
        else:
            self.logger.info(f"Listening for '{self.name}' XTABLES services on port 5353...")

        self.service_found.wait()
        self.logger.info("Service found, proceeding to close mDNS services...")
        self.zeroconf.close()
        self.logger.info("mDNS service closed.")

        if self.server_ip is None or self.server_port is None:
            raise RuntimeError("The service address or port could not be found.")
        else:
            self.initialize_client(self.server_ip, self.server_port)

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

    def add_service(self, zeroconf, service_type, name):
        self.logger.info(f"Service found: {name}")
        info = zeroconf.get_service_info(service_type, name)
        if info:
            self.resolve_service(info)

    def resolve_service(self, info):
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


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    client = XTablesClient()
    while True:
        client.getString("SmartDashboard.test")
        print(len(client.response_map))
