import socket
import logging
import threading
import time
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
        # Shut down the existing client if already connected
        self.close_socket()

        with self.lock:  # Ensure no concurrent connections
            while not self.shutdown_event.is_set():
                try:
                    self.logger.info(f"Connecting to server {server_ip}:{server_port}")
                    self.client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    self.client_socket.connect((server_ip, 1735))
                    self.logger.info(f"Connected to server {server_ip}:{server_port}")
                    self.out = self.client_socket.makefile('w')

                    # Stop the old message listener if it exists
                    if self.clientMessageListener:
                        self.logger.info("Stopping previous message listener...")
                        self.clientMessageListener.stop()
                        self.clientMessageListener = None  # Set to None to avoid calling join() from the same thread

                    # Start a new message listener
                    self.clientMessageListener = ClientMessageListener(self)
                    self.clientMessageListener.start()
                    break  # Exit the loop after a successful connection
                except socket.error as e:
                    self.logger.error(f"Connection error: {e}. Retrying immediately.")
                time.sleep(1)

    def send_data(self, data):
        try:
            if self.client_socket:
                self.out.write(data + "\n")
                self.out.flush()
        except socket.error as e:
            pass

    def executePutString(self, key, value):
        if isinstance(value, str) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.send_data(f"IGNORED:PUT {key} \"{value}\"")

    def executePutInteger(self, key, value):
        if isinstance(value, int) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.send_data(f"IGNORED:PUT {key} {value}")

    def executePutBoolean(self, key, value):
        if isinstance(value, bool) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.send_data(f"IGNORED:PUT {key} {str(value).lower()}")

    def executePutFloat(self, key, value):
        if isinstance(value, float) and isinstance(key, str):
            Utilities.validate_key(key, True)
            self.send_data(f"IGNORED:PUT {key} {value}")

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

            # Ensure the listener thread is stopped
            if self.clientMessageListener:
                self.logger.info("Stopping message listener...")
                self.clientMessageListener.stop()
                self.clientMessageListener = None

    def shutdown(self):
        self.shutdown_event.set()
        if self.clientMessageListener:
            self.clientMessageListener.stop()
        self.close_socket()


class XTablesServiceListener(ServiceListener):
    def __init__(self, client):
        self.client = client
        self.logger = logging.getLogger(__name__)

    def add_service(self, zeroconf, service_type, name):
        self.logger.info(f"Service found: {name}")
        info = zeroconf.get_service_info(service_type, name)
        if info:
            self.resolve_service(info)

    def remove_service(self, zeroconf, service_type, name):
        self.logger.info(f"Service removed: {name}")

    def update_service(self, zeroconf, service_type, name):
        pass

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
                    # Process the message here (placeholder logic)
                    self.logger.info(f"Received message: {message}")
                else:
                    self.logger.warning("Received an empty message, attempting reconnection...")
                    time.sleep(1)
                    self.stop_event.set()  # Stop the listener thread before reconnecting
            except Exception as e:
                self.logger.error(f"Error in message listener: {e}")
            finally:
                # Ensure client socket is closed, and let main thread handle reconnection
                self.client.close_socket()

    def stop(self):
        self.stop_event.set()
        self.logger.info("Message listener stopped.")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    client = XTablesClient()

    while not client.shutdown_event.is_set():
        try:
            client.executePutBoolean("ok", False)
        except socket.error as e:
            client.logger.error(f"Send data error: {e}")
        except Exception:
            pass

        # Reinitialize the client if it shuts down due to a listener stop
        if client.client_socket is None:
            client.logger.info("Reconnecting client...")
            client.initialize_client(client.server_ip, client.server_port)
