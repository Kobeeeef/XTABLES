import socket
import logging
import time

from zeroconf import Zeroconf, ServiceBrowser, ServiceListener
import threading

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
        self.reconnect_delay_ms = 3000
        self.browser = ServiceBrowser(self.zeroconf, "_xtables._tcp.local.", self.listener)
        self.clientMessageListener = None
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
        self.logger.info(f"Initializing client with server {server_ip}:{server_port}")
        try:
            self.client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.client_socket.connect((server_ip, server_port))
            self.logger.info(f"Connected to server {server_ip}:{server_port}")
            self.out = self.client_socket.makefile('w')
            if self.clientMessageListener is None:
                ClientMessageListener(self).start()
        except socket.error as e:
            self.logger.error(f"Connection error: {e}")
            self.close()
            time.sleep(self.reconnect_delay_ms/1000)
            self.initialize_client(server_ip, server_port)

    def send_data(self, data):
        try:
            if self.client_socket:
                self.out.write(data + "\n")
        except socket.error as e:
            self.logger.error(f"Send data error: {e}")
            self.close()
            self.initialize_client(self.server_ip, self.server_port)

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
            self.send_data(f"IGNORED:PUT {key} {value}")

    def close(self):
        try:
            self.client_socket.close()
            self.logger.info("Socket closed.")
        except socket.error as e:
            self.logger.error(f"Close socket error: {e}")


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
            self.logger.info(f"Service resolved: {info.name}")
            self.logger.info(f"Address: {service_address}, Port: {port}")
            self.client.server_ip = service_address
            self.client.server_port = port
            self.client.service_found.set()


class ClientMessageListener(threading.Thread):
    def __init__(self, client: XTablesClient):
        super().__init__()
        self.client = client
        self.client_socket = client.client_socket
        self.is_connected = False
        self.messages = []
        self.ip = client.server_ip
        self.port = client.server_port
        self.in_ = self.client_socket.makefile('r')
        self.logger = logging.getLogger(__name__)

    def run(self):
        while True:
            try:
                message = self.in_.readline().strip()
                if message:
                    self.is_connected = True
                    self.messages.append(message)
                    print(self.messages)
                elif not self.is_connected:
                    print("Recieved a empty message, attempting reconnection...")
                    self.client.initialize_client(self.ip, self.port)
                    self.is_connected = True
            except Exception as e:
                self.logger.error(e)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    client = XTablesClient(name="localhost")
    while True:
        client.executePutString("ok", "okie")